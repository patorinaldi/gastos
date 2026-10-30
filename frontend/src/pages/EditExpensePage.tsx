import { useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import { useLocation, useNavigate, useParams } from 'react-router'
import { listCategories } from '../api/categories.ts'
import { errorMessage } from '../api/client.ts'
import { deleteExpense, getExpense, updateExpense } from '../api/expenses.ts'
import { ExpenseForm } from '../components/expenses/ExpenseForm.tsx'
import { PageHeader } from '../components/layout/PageHeader.tsx'
import { EmptyState, ErrorState, LoadingState } from '../components/states/States.tsx'
import { Button, ButtonLink } from '../components/ui/Button.tsx'
import { Notice } from '../components/ui/Notice.tsx'
import { isUuid } from '../dashboard.ts'
import { categoryOptions, expenseValues, updateRequest, type ValidExpense } from '../expenseForm.ts'
import { formatMoney } from '../format.ts'
import { useRequest } from '../hooks/useRequest.ts'
import { backPath } from '../layouts/navigation.ts'
import type { CategoryResponse, ExpenseResponse } from '../types/api.ts'
import styles from './ExpensePages.module.css'

/**
 * Edición y baja de un gasto (RF-15, RF-16, HU-09). Cualquier integrante puede editar cualquier gasto
 * del hogar; el responsable no se edita, porque es quien lo cargó.
 *
 * Al terminar vuelve a la pantalla que la abrió, con sus filtros (ver RecentExpenses). Vuelve
 * reemplazando la edición en el historial: "atrás" no reabre un formulario ya guardado.
 */
export function EditExpensePage() {
  const { id = '' } = useParams()
  const valid = isUuid(id)
  // Un identificador que no lo es no se le pide al servidor: no existe.
  const expense = useRequest(id, () => (valid ? getExpense(id) : Promise.resolve(null)), 'No pudimos cargar el gasto.')
  const categories = useRequest('categories', listCategories, 'No pudimos cargar las categorías.')

  if (!valid || (expense.kind === 'loaded' && expense.data === null)) {
    return <NotFound />
  }

  return (
    <>
      <PageHeader
        title="Editar gasto"
        eyebrow={expense.kind === 'loaded' && expense.data ? `Cargado por ${expense.data.ownerName}` : undefined}
      />
      {expense.kind === 'loading' || categories.kind === 'loading' ? (
        <LoadingState label="Cargando el gasto…" />
      ) : expense.kind === 'failed' ? (
        <ErrorState message={expense.message} onRetry={expense.retry} />
      ) : categories.kind === 'failed' ? (
        // Sin las categorías no se puede mostrar la del gasto: el formulario la cambiaría sin querer.
        <ErrorState message={categories.message} onRetry={categories.retry} />
      ) : (
        expense.data && <Editor expense={expense.data} categories={categories.data} />
      )}
    </>
  )
}

function NotFound() {
  return (
    <>
      <PageHeader title="Editar gasto" />
      <EmptyState
        title="No encontramos ese gasto"
        description="Puede que se haya dado de baja, o que el enlace esté incompleto."
        action={<ButtonLink to="/expenses">Ver movimientos</ButtonLink>}
      />
    </>
  )
}

interface EditorProps {
  expense: ExpenseResponse
  categories: CategoryResponse[]
}

function Editor({ expense, categories }: EditorProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const back = backPath(location.state, '/expenses')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const confirmButton = useRef<HTMLButtonElement>(null)
  const deleteButton = useRef<HTMLButtonElement>(null)

  // Al pedir la baja, el foco pasa a la confirmación; al cancelarla, vuelve al botón que la pidió. El
  // botón aparece recién con el estado nuevo: flushSync lo dibuja antes de enfocarlo.
  const confirmDelete = (confirming: boolean) => {
    flushSync(() => setConfirmingDelete(confirming))
    ;(confirming ? confirmButton : deleteButton).current?.focus()
  }

  const save = async (values: ValidExpense) => {
    const request = updateRequest(expense, values)
    if (Object.keys(request).length === 0) {
      navigate(back, { replace: true })
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await updateExpense(expense.id, request)
      navigate(back, { replace: true })
    } catch (failure) {
      setError(errorMessage(failure, 'Revisá los datos. Si elegiste una categoría, puede que se haya dado de baja.'))
      setSubmitting(false)
    }
  }

  const remove = async () => {
    setDeleting(true)
    setDeleteError(null)
    try {
      await deleteExpense(expense.id)
      navigate(back, { replace: true })
    } catch (failure) {
      setDeleteError(errorMessage(failure, 'No pudimos eliminar el gasto. Intentá de nuevo.'))
      setDeleting(false)
    }
  }

  return (
    <div className={styles.editor}>
      <ExpenseForm
        mode="edit"
        initial={expenseValues(expense)}
        categories={categoryOptions(categories, 'edit', expense)}
        submitting={submitting}
        error={error}
        onSubmit={save}
      >
        <ButtonLink variant="secondary" to={back}>
          Cancelar
        </ButtonLink>
      </ExpenseForm>

      <section className={styles.danger} aria-labelledby="delete-title">
        <h2 id="delete-title" className={styles.dangerTitle}>
          Eliminar el gasto
        </h2>
        {deleteError && <Notice tone="danger">{deleteError}</Notice>}
        {confirmingDelete ? (
          <>
            <p className={styles.dangerText}>
              ¿Eliminar {formatMoney(expense.amount)} en {expense.merchant}? Deja de contar en los movimientos y en
              los totales del hogar.
            </p>
            <div className={styles.actions}>
              <Button ref={confirmButton} onClick={remove} disabled={deleting}>
                {deleting ? 'Eliminando…' : 'Sí, eliminar'}
              </Button>
              <Button variant="secondary" onClick={() => confirmDelete(false)} disabled={deleting}>
                No, conservarlo
              </Button>
            </div>
          </>
        ) : (
          <>
            <p className={styles.dangerText}>Deja de contar en los movimientos y en los totales del hogar.</p>
            <div className={styles.actions}>
              <Button ref={deleteButton} variant="secondary" onClick={() => confirmDelete(true)}>
                Eliminar gasto
              </Button>
            </div>
          </>
        )}
      </section>
    </div>
  )
}
