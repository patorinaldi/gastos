import { useRef, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client.ts'
import { renameHousehold } from '../../api/household.ts'
import type { HouseholdResponse } from '../../types/api.ts'
import { validateHouseholdName } from '../../validation.ts'
import { Button } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import { TextField } from '../ui/TextField.tsx'
import styles from './HouseholdNameForm.module.css'

interface HouseholdNameFormProps {
  household: HouseholdResponse
  /** Con el hogar como quedó guardado, para que la pantalla muestre el nombre nuevo. */
  onRenamed: (household: HouseholdResponse) => void
}

/**
 * Cambiar el nombre del hogar (RF-11, HU-18). Lo ven igual todos los integrantes. Un nombre hecho
 * solo de espacios se rechaza antes de enviarlo, y el hogar conserva el anterior.
 */
export function HouseholdNameForm({ household, onRenamed }: HouseholdNameFormProps) {
  const [name, setName] = useState(household.name)
  const [saved, setSaved] = useState(household.name)
  const [fieldError, setFieldError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const input = useRef<HTMLInputElement>(null)

  // El servidor guarda el nombre sin los espacios de los extremos: así se compara para saber si cambió.
  const unchanged = name.trim() === saved

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const invalid = validateHouseholdName(name)
    setFieldError(invalid)
    setFormError(null)
    setDone(false)
    if (invalid) {
      input.current?.focus()
      return
    }
    if (unchanged) {
      return
    }

    setSubmitting(true)
    try {
      const renamed = await renameHousehold(name)
      setName(renamed.name)
      setSaved(renamed.name)
      setDone(true)
      onRenamed(renamed)
    } catch (error) {
      setFormError(errorMessage(error, 'Revisá el nombre e intentá de nuevo.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      {formError && <Notice tone="danger">{formError}</Notice>}
      <TextField
        ref={input}
        label="Nombre"
        autoComplete="off"
        hint="Lo ven todos los integrantes."
        value={name}
        onChange={(event) => {
          setName(event.target.value)
          setDone(false)
        }}
        error={fieldError}
      />
      <div className={styles.actions}>
        <Button type="submit" variant="secondary" disabled={submitting || unchanged}>
          {submitting ? 'Guardando…' : 'Guardar el nombre'}
        </Button>
        <span className={styles.done} aria-live="polite">
          {done && 'Nombre guardado.'}
        </span>
      </div>
    </form>
  )
}
