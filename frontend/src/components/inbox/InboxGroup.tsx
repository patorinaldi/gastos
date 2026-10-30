import { useId, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { createCategoryRule } from '../../api/categories.ts'
import { ApiError, errorMessage } from '../../api/client.ts'
import { updateExpense } from '../../api/expenses.ts'
import { formatDayMonth, formatMoney } from '../../format.ts'
import { countMatches, type MerchantGroup } from '../../inbox.ts'
import { matchesPattern, validatePattern } from '../../patterns.ts'
import type { CategoryResponse, ExpenseResponse } from '../../types/api.ts'
import { Button } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import { SelectField } from '../ui/SelectField.tsx'
import { TextField } from '../ui/TextField.tsx'
import styles from './InboxGroup.module.css'

const NONE = ''

interface InboxGroupProps {
  group: MerchantGroup
  categories: CategoryResponse[]
  /** Toda la bandeja que se ve, para anticipar a cuántos gastos alcanza la regla. */
  inbox: ExpenseResponse[]
  open: boolean
  onOpen: () => void
  onClose: () => void
  /** Se clasificó algo: la bandeja se vuelve a pedir, y el mensaje dice qué pasó. */
  onDone: (message: string) => void
}

/**
 * Los gastos de la bandeja de un comercio y cómo clasificarlos (HU-11).
 *
 * Por defecto, clasificar crea una regla con el comercio como patrón (RF-23): el servidor la aplica
 * a toda la bandeja, también a gastos de otros meses, y los próximos de ese comercio ya no llegan acá.
 * Sin la regla, se clasifican solo los gastos del grupo, uno por uno.
 */
export function InboxGroup({ group, categories, inbox, open, onOpen, onClose, onDone }: InboxGroupProps) {
  const titleId = useId()
  const count = group.expenses.length

  return (
    <section className={styles.group} aria-labelledby={titleId}>
      <div className={styles.header}>
        <div className={styles.heading}>
          <h2 id={titleId} className={styles.merchant}>
            {group.merchant}
          </h2>
          <p className={styles.meta}>
            {count} {count === 1 ? 'gasto' : 'gastos'} sin categoría
          </p>
        </div>
        {!open && (
          <Button variant="secondary" onClick={onOpen}>
            Clasificar
          </Button>
        )}
      </div>

      <ul className={styles.expenses}>
        {group.expenses.map((expense) => (
          <li key={expense.id} className={styles.expense}>
            <span className={styles.date}>{formatDayMonth(expense.expenseDate)}</span>
            <Link to={`/expenses/${expense.id}`} state={{ back: '/inbox' }} className={styles.expenseLink}>
              {expense.merchant}
            </Link>
            <span className={styles.owner}>{expense.ownerName}</span>
            <span className={styles.amount}>{formatMoney(expense.amount)}</span>
          </li>
        ))}
      </ul>

      {open && <ClassifyForm group={group} categories={categories} inbox={inbox} onCancel={onClose} onDone={onDone} />}
    </section>
  )
}

interface ClassifyFormProps {
  group: MerchantGroup
  categories: CategoryResponse[]
  inbox: ExpenseResponse[]
  onCancel: () => void
  onDone: (message: string) => void
}

interface Errors {
  category?: string
  pattern?: string
}

function ClassifyForm({ group, categories, inbox, onCancel, onDone }: ClassifyFormProps) {
  const [categoryId, setCategoryId] = useState(NONE)
  const [withRule, setWithRule] = useState(true)
  const [pattern, setPattern] = useState(group.merchant)
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const categoryInput = useRef<HTMLSelectElement>(null)
  const patternInput = useRef<HTMLInputElement>(null)

  const category = categories.find((c) => c.id === categoryId)
  const matches = countMatches(inbox, pattern)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const found: Errors = {
      category: category ? undefined : 'Elegí una categoría',
      pattern: withRule ? patternError(pattern, group.merchant) : undefined,
    }
    setErrors(found)
    setFormError(null)
    if (!category || found.pattern) {
      ;(found.category ? categoryInput : patternInput).current?.focus()
      return
    }

    setSubmitting(true)
    try {
      onDone(withRule ? await classifyWithRule(pattern, category) : await classifyEach(group, category))
    } catch (failure) {
      // Un 409 es un patrón que ya existe: su regla manda a otra categoría, o a esta y el gasto se
      // devolvió a mano a la bandeja. El detalle del servidor dice cuál; acá se dice cómo seguir.
      const conflict = failure instanceof ApiError && failure.status === 409
      setFormError(
        conflict
          ? `${errorMessage(failure, 'Ya existe una regla con ese patrón.')} Cambiá el patrón, o destildá la regla para clasificar solo estos gastos.`
          : errorMessage(failure, 'Revisá el patrón y la categoría e intentá de nuevo.'),
      )
      setSubmitting(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      {formError && <Notice tone="danger">{formError}</Notice>}
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

      <label className={styles.check}>
        <input type="checkbox" checked={withRule} onChange={(event) => setWithRule(event.target.checked)} />
        <span>Recordar este comercio: los próximos gastos se clasifican solos</span>
      </label>

      {withRule && (
        <TextField
          ref={patternInput}
          label="Patrón"
          autoComplete="off"
          value={pattern}
          onChange={(event) => {
            setPattern(event.target.value)
            setErrors((current) => ({ ...current, pattern: undefined }))
          }}
          hint={`Clasifica los comercios que contienen este texto, sin importar mayúsculas ni tildes. En la bandeja coincide con ${matches} ${matches === 1 ? 'gasto' : 'gastos'}.`}
          error={errors.pattern}
        />
      )}

      <div className={styles.actions}>
        <Button type="submit" disabled={submitting}>
          {submitting ? 'Clasificando…' : 'Clasificar'}
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={submitting}>
          Cancelar
        </Button>
      </div>
    </form>
  )
}

/**
 * Además de las reglas del servidor, el patrón tiene que coincidir con el comercio del grupo: si no,
 * la regla se crearía y estos gastos seguirían en la bandeja, que es justo lo que se quería resolver.
 */
function patternError(pattern: string, merchant: string): string | undefined {
  return (
    validatePattern(pattern) ??
    (matchesPattern(merchant, pattern)
      ? undefined
      : `El patrón no coincide con «${merchant}»: usá una parte del nombre del comercio`)
  )
}

async function classifyWithRule(pattern: string, category: CategoryResponse): Promise<string> {
  const rule = await createCategoryRule({ pattern: pattern.trim(), categoryId: category.id })
  const moved = rule.reclassified === 1 ? '1 gasto pasó' : `${rule.reclassified} gastos pasaron`
  return `Creamos la regla «${rule.pattern}» para ${category.name}: ${moved} de la bandeja a esa categoría.`
}

/**
 * Sin regla, gasto por gasto y de a uno: son pocos, y así un error no deja peticiones a medio camino.
 * Si alguno falla, sigue con el resto y lo dice: la bandeja que vuelve muestra los que quedaron.
 */
async function classifyEach(group: MerchantGroup, category: CategoryResponse): Promise<string> {
  let done = 0
  let lastError: unknown = null
  for (const expense of group.expenses) {
    try {
      await updateExpense(expense.id, { categoryId: category.id })
      done += 1
    } catch (failure) {
      lastError = failure
    }
  }
  const total = group.expenses.length
  // Si no salió ninguno, el error es el del formulario: sin conexión, por ejemplo.
  if (done === 0) {
    throw lastError
  }
  return done === total
    ? `Clasificamos ${total === 1 ? 'el gasto' : `los ${total} gastos`} de ${group.merchant} en ${category.name}.`
    : `Clasificamos ${done} de ${total} gastos de ${group.merchant} en ${category.name}. Los demás siguen en la bandeja: intentá de nuevo.`
}
