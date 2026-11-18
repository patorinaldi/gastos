import { useRef, useState, type FormEvent } from 'react'
import { createCategory } from '../../api/categories.ts'
import { ApiError, errorMessage } from '../../api/client.ts'
import type { CategoryResponse } from '../../types/api.ts'
import { validateCategoryName } from '../../validation.ts'
import { Button } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import { TextField } from '../ui/TextField.tsx'
import styles from './NewCategoryForm.module.css'

interface NewCategoryFormProps {
  /** Con la categoría como quedó guardada: sin los espacios de los extremos. */
  onCreated: (category: CategoryResponse) => void
}

/**
 * Alta de una categoría (RF-24, HU-12). El formulario queda limpio para la siguiente, y el foco sigue
 * en el campo.
 */
export function NewCategoryForm({ onCreated }: NewCategoryFormProps) {
  const [name, setName] = useState('')
  const [fieldError, setFieldError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const input = useRef<HTMLInputElement>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const invalid = validateCategoryName(name)
    setFieldError(invalid)
    setFormError(null)
    if (invalid) {
      input.current?.focus()
      return
    }

    setSubmitting(true)
    try {
      const created = await createCategory(name)
      setName('')
      onCreated(created)
    } catch (error) {
      // Un nombre repetido (RN-09) es un error del campo: el detalle del servidor dice cuál es.
      if (error instanceof ApiError && error.status === 409) {
        setFieldError(errorMessage(error, 'Ya existe una categoría con ese nombre.'))
      } else {
        setFormError(errorMessage(error, 'Revisá el nombre e intentá de nuevo.'))
      }
    } finally {
      setSubmitting(false)
      input.current?.focus()
    }
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      {formError && <Notice tone="danger">{formError}</Notice>}
      <TextField
        ref={input}
        label="Nombre"
        autoComplete="off"
        hint="Por ejemplo, Mascotas. Se puede elegir al cargar un gasto y al clasificar la bandeja."
        value={name}
        onChange={(event) => {
          setName(event.target.value)
          setFieldError(undefined)
        }}
        error={fieldError}
      />
      <div className={styles.actions}>
        <Button type="submit" variant="secondary" disabled={submitting}>
          {submitting ? 'Creando…' : 'Crear categoría'}
        </Button>
      </div>
    </form>
  )
}
