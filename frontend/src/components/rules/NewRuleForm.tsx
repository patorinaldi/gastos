import { useRef, useState, type FormEvent } from 'react'
import { createCategoryRule } from '../../api/categories.ts'
import { ApiError, errorMessage } from '../../api/client.ts'
import { validatePattern } from '../../patterns.ts'
import type { CategoryResponse, CategoryRuleResponse } from '../../types/api.ts'
import { Button } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import { SelectField } from '../ui/SelectField.tsx'
import { TextField } from '../ui/TextField.tsx'
import styles from './NewRuleForm.module.css'

const NONE = ''

interface NewRuleFormProps {
  categories: CategoryResponse[]
  /** Con la regla como quedó guardada, y cuántos gastos sacó de la bandeja. */
  onCreated: (rule: CategoryRuleResponse, reclassified: number) => void
}

interface Errors {
  pattern?: string
  category?: string
}

/**
 * Alta de una regla desde el listado (RF-23). Es la misma que crea la bandeja al clasificar, pero sin
 * esperar a que llegue un gasto de ese comercio. Es también cómo se cambia la categoría de un patrón:
 * se elimina su regla y se crea de nuevo.
 *
 * Después de crearla, la categoría queda elegida para la próxima, que suele ser de la misma.
 */
export function NewRuleForm({ categories, onCreated }: NewRuleFormProps) {
  const [pattern, setPattern] = useState('')
  const [categoryId, setCategoryId] = useState(NONE)
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const patternInput = useRef<HTMLInputElement>(null)
  const categoryInput = useRef<HTMLSelectElement>(null)

  const category = categories.find((c) => c.id === categoryId)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const found: Errors = {
      pattern: validatePattern(pattern),
      category: category ? undefined : 'Elegí una categoría',
    }
    setErrors(found)
    setFormError(null)
    if (found.pattern || !category) {
      ;(found.pattern ? patternInput : categoryInput).current?.focus()
      return
    }

    setSubmitting(true)
    try {
      const created = await createCategoryRule({ pattern: pattern.trim(), categoryId: category.id })
      setPattern('')
      onCreated(
        { id: created.id, pattern: created.pattern, categoryId: category.id, categoryName: category.name },
        created.reclassified,
      )
      patternInput.current?.focus()
    } catch (failure) {
      // Un patrón repetido (RN-10): el detalle del servidor dice a qué categoría apunta la regla que ya
      // está, y acá se dice cómo cambiarla. Si otro integrante la creó después de cargar la pantalla,
      // no está en la lista hasta recargar.
      if (failure instanceof ApiError && failure.status === 409) {
        setErrors({
          pattern: `${errorMessage(failure, 'Ya existe una regla con ese patrón.')} Para que apunte a otra, eliminá esa regla y creala de nuevo. Si no la ves en la lista, recargá la pantalla.`,
        })
        patternInput.current?.focus()
      } else {
        // Un 400 con el patrón ya validado es una categoría que otro integrante dio de baja.
        setFormError(errorMessage(failure, 'Revisá el patrón y la categoría. Si la categoría se dio de baja, recargá la pantalla.'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      {formError && <Notice tone="danger">{formError}</Notice>}
      <TextField
        ref={patternInput}
        label="Patrón"
        autoComplete="off"
        hint="Clasifica los comercios que contienen este texto al principio de una palabra, sin importar mayúsculas ni tildes: «uber» reconoce «MERPAGO*UBER», pero no «Pub Ernesto»."
        value={pattern}
        onChange={(event) => {
          setPattern(event.target.value)
          setErrors((current) => ({ ...current, pattern: undefined }))
        }}
        error={errors.pattern}
      />
      <SelectField
        ref={categoryInput}
        label="Categoría"
        options={[{ value: NONE, label: 'Elegí una categoría' }, ...categories.map((c) => ({ value: c.id, label: c.name }))]}
        value={categoryId}
        onChange={(event) => {
          setCategoryId(event.target.value)
          setErrors((current) => ({ ...current, category: undefined }))
        }}
        error={errors.category}
      />
      <div className={styles.actions}>
        <Button type="submit" variant="secondary" disabled={submitting}>
          {submitting ? 'Creando…' : 'Crear regla'}
        </Button>
      </div>
    </form>
  )
}
