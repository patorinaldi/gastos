import { useRef, useState, type FormEvent, type ReactNode } from 'react'
import { parseAmount } from '../../amount.ts'
import {
  PAYMENT_METHODS,
  validateExpense,
  type CategoryOption,
  type ExpenseFormErrors,
  type ExpenseFormValues,
  type ValidExpense,
} from '../../expenseForm.ts'
import { formatMoney } from '../../format.ts'
import type { PaymentMethod } from '../../types/api.ts'
import { Button } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import { SelectField } from '../ui/SelectField.tsx'
import { TextField } from '../ui/TextField.tsx'
import styles from './ExpenseForm.module.css'

const METHOD_OPTIONS = PAYMENT_METHODS.map((method) => ({ value: method, label: method }))

interface ExpenseFormProps {
  mode: 'create' | 'edit'
  /** Los valores con los que abre. Para volver a empezar, la pantalla lo monta de nuevo con otra `key`. */
  initial: ExpenseFormValues
  categories: CategoryOption[]
  submitting: boolean
  /** El error del servidor, para todo el formulario. */
  error: string | null
  /** Se llama solo con el formulario válido. */
  onSubmit: (expense: ValidExpense) => void
  /** Acciones al lado de la de guardar, como "Cancelar". */
  children?: ReactNode
}

/**
 * Formulario de un gasto, para el alta y la edición (RF-13, RF-15).
 *
 * Arriba va lo único obligatorio, importe y comercio: con eso y confirmar se registra un gasto
 * (RNF-14). La fecha, el medio de pago y la categoría ya vienen con el valor que pondría el servidor.
 *
 * El importe se escribe como en cualquier lado ("1.234,56") y, mientras se escribe, el campo dice
 * cómo se va a guardar: un importe mal leído no da error, queda registrado (RN-06).
 */
export function ExpenseForm({ mode, initial, categories, submitting, error, onSubmit, children }: ExpenseFormProps) {
  const [values, setValues] = useState(initial)
  const [errors, setErrors] = useState<ExpenseFormErrors>({})
  const amountInput = useRef<HTMLInputElement>(null)
  const merchantInput = useRef<HTMLInputElement>(null)
  const dateInput = useRef<HTMLInputElement>(null)

  // Al corregir un campo, su error se va: en el importe, vuelve a verse cómo se va a guardar.
  const set = <K extends keyof ExpenseFormValues>(field: K, value: ExpenseFormValues[K]) => {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => (field in current ? { ...current, [field]: undefined } : current))
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const result = validateExpense(values, mode)
    if (!result.ok) {
      setErrors(result.errors)
      const firstInvalid = result.errors.amount ? amountInput : result.errors.merchant ? merchantInput : dateInput
      firstInvalid.current?.focus()
      return
    }
    setErrors({})
    onSubmit(result.expense)
  }

  // Mientras el importe se pueda leer, el campo dice cómo se va a guardar.
  const parsed = parseAmount(values.amount)
  const amountHint = parsed.ok ? `Se guarda como ${formatMoney(parsed.amount)}` : 'Por ejemplo, 1.234,56'

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      {error && <Notice tone="danger">{error}</Notice>}

      <div className={styles.essential}>
        <TextField
          ref={amountInput}
          label="Importe"
          inputMode="decimal"
          autoComplete="off"
          // En el alta es lo primero que se escribe: el teclado numérico ya abierto ahorra un toque.
          autoFocus={mode === 'create'}
          className={styles.amount}
          value={values.amount}
          onChange={(event) => set('amount', event.target.value)}
          hint={amountHint}
          error={errors.amount}
        />
        <TextField
          ref={merchantInput}
          label="Comercio"
          autoComplete="off"
          autoCapitalize="words"
          value={values.merchant}
          onChange={(event) => set('merchant', event.target.value)}
          error={errors.merchant}
        />
      </div>

      <div className={styles.details}>
        <TextField
          ref={dateInput}
          label="Fecha"
          type="date"
          value={values.expenseDate}
          onChange={(event) => set('expenseDate', event.target.value)}
          error={errors.expenseDate}
        />
        <SelectField
          label="Medio de pago"
          options={METHOD_OPTIONS}
          value={values.paymentMethod}
          onChange={(event) => set('paymentMethod', event.target.value as PaymentMethod)}
        />
        <SelectField
          label="Categoría"
          className={styles.category}
          options={categories}
          value={values.category}
          onChange={(event) => set('category', event.target.value)}
          hint={
            mode === 'create'
              ? 'Si no elegís una, la deciden las reglas del hogar. Si ninguna coincide, el gasto queda en la bandeja.'
              : undefined
          }
        />
      </div>

      <div className={styles.actions}>
        <Button type="submit" disabled={submitting}>
          {submitting ? 'Guardando…' : mode === 'create' ? 'Guardar gasto' : 'Guardar cambios'}
        </Button>
        {children}
      </div>
    </form>
  )
}
