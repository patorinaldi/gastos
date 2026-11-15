import { useRef, useState, type FormEvent, type RefObject } from 'react'
import { flushSync } from 'react-dom'
import { deactivateCategory, renameCategory } from '../../api/categories.ts'
import { ApiError, errorMessage } from '../../api/client.ts'
import type { CategoryResponse } from '../../types/api.ts'
import { validateCategoryName } from '../../validation.ts'
import { Button } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import { TextField } from '../ui/TextField.tsx'
import styles from './CategoryRow.module.css'

type Mode = 'idle' | 'renaming' | 'confirming'

interface CategoryRowProps {
  category: CategoryResponse
  onRenamed: (category: CategoryResponse) => void
  /** Ya no está en el catálogo: se dio de baja, o la había dado de baja otro integrante. */
  onDeactivated: (category: CategoryResponse) => void
}

/**
 * Una categoría del catálogo, con su renombrado y su baja (RF-24, HU-12).
 *
 * El foco sigue a lo que aparece: el campo al renombrar, la confirmación al dar de baja, y el botón
 * que abrió cada uno al cancelar o al guardar. Los botones aparecen recién con el estado nuevo:
 * flushSync los dibuja antes de enfocarlos.
 */
export function CategoryRow({ category, onRenamed, onDeactivated }: CategoryRowProps) {
  const [mode, setMode] = useState<Mode>('idle')
  const [name, setName] = useState(category.name)
  const [fieldError, setFieldError] = useState<string | undefined>()
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const renameButton = useRef<HTMLButtonElement>(null)
  const deactivateButton = useRef<HTMLButtonElement>(null)
  const nameInput = useRef<HTMLInputElement>(null)
  const confirmButton = useRef<HTMLButtonElement>(null)

  const show = (next: Mode, focus: RefObject<HTMLElement | null>) => {
    flushSync(() => {
      setMode(next)
      setError(null)
      setFieldError(undefined)
      if (next === 'renaming') {
        setName(category.name)
      }
    })
    focus.current?.focus()
  }

  const rename = async (event: FormEvent) => {
    event.preventDefault()
    const invalid = validateCategoryName(name)
    setFieldError(invalid)
    setError(null)
    if (invalid) {
      nameInput.current?.focus()
      return
    }
    // El servidor guarda el nombre sin los espacios de los extremos: así se sabe si cambió.
    if (name.trim() === category.name) {
      show('idle', renameButton)
      return
    }

    setBusy(true)
    try {
      const renamed = await renameCategory(category.id, name)
      // La fila cambia de lugar en la lista, ordenada por nombre: el foco vuelve después de moverla.
      flushSync(() => {
        setBusy(false)
        setMode('idle')
        onRenamed(renamed)
      })
      renameButton.current?.focus()
    } catch (failure) {
      setBusy(false)
      if (failure instanceof ApiError && failure.status === 409) {
        setFieldError(errorMessage(failure, 'Ya existe una categoría con ese nombre.'))
        nameInput.current?.focus()
      } else {
        setError(renameError(failure))
      }
    }
  }

  const deactivate = async () => {
    setBusy(true)
    setError(null)
    try {
      await deactivateCategory(category.id)
      onDeactivated(category)
    } catch (failure) {
      // Un 404 es una categoría que otro integrante ya dio de baja: el resultado es el que se pidió.
      if (failure instanceof ApiError && failure.status === 404) {
        onDeactivated(category)
        return
      }
      setError(errorMessage(failure, 'No pudimos dar de baja la categoría. Intentá de nuevo.'))
      setBusy(false)
    }
  }

  return (
    <li className={styles.row}>
      {mode === 'renaming' ? (
        <form className={styles.form} onSubmit={rename} noValidate>
          {error && <Notice tone="danger">{error}</Notice>}
          <TextField
            ref={nameInput}
            label={`Nuevo nombre de «${category.name}»`}
            autoComplete="off"
            hint="Cambia en todos sus gastos, también en los de meses anteriores."
            value={name}
            onChange={(event) => {
              setName(event.target.value)
              setFieldError(undefined)
            }}
            error={fieldError}
          />
          <div className={styles.actions}>
            <Button type="submit" disabled={busy}>
              {busy ? 'Guardando…' : 'Guardar'}
            </Button>
            <Button variant="secondary" onClick={() => show('idle', renameButton)} disabled={busy}>
              Cancelar
            </Button>
          </div>
        </form>
      ) : (
        <>
          <div className={styles.summary}>
            <p className={styles.name}>{category.name}</p>
            {mode === 'idle' && (
              <div className={styles.actions}>
                <Button ref={renameButton} variant="secondary" onClick={() => show('renaming', nameInput)}>
                  Renombrar
                </Button>
                <Button ref={deactivateButton} variant="secondary" onClick={() => show('confirming', confirmButton)}>
                  Dar de baja
                </Button>
              </div>
            )}
          </div>

          {mode === 'confirming' && (
            <div className={styles.confirm}>
              {error && <Notice tone="danger">{error}</Notice>}
              <p className={styles.confirmText}>
                Deja de ofrecerse para clasificar, y sus reglas se eliminan. Los gastos que ya la tienen la conservan,
                en los movimientos y en el tablero.
              </p>
              <div className={styles.actions}>
                <Button ref={confirmButton} onClick={deactivate} disabled={busy}>
                  {busy ? 'Dando de baja…' : 'Sí, dar de baja'}
                </Button>
                <Button variant="secondary" onClick={() => show('idle', deactivateButton)} disabled={busy}>
                  Cancelar
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </li>
  )
}

/** Un 404 al renombrar es una categoría que otro integrante dio de baja mientras tanto. */
function renameError(failure: unknown): string {
  if (failure instanceof ApiError && failure.status === 404) {
    return 'Esta categoría ya no está en el catálogo: puede que otro integrante la haya dado de baja. Recargá la pantalla.'
  }
  return errorMessage(failure, 'Revisá el nombre e intentá de nuevo.')
}
