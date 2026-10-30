import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { listCategories } from '../api/categories.ts'
import { errorMessage } from '../api/client.ts'
import { createExpense } from '../api/expenses.ts'
import { ExpenseForm } from '../components/expenses/ExpenseForm.tsx'
import { PageHeader } from '../components/layout/PageHeader.tsx'
import { ButtonLink } from '../components/ui/Button.tsx'
import { Notice } from '../components/ui/Notice.tsx'
import { categoryOptions, createRequest, emptyExpense, type ValidExpense } from '../expenseForm.ts'
import { formatMoney } from '../format.ts'
import { useRequest } from '../hooks/useRequest.ts'
import { backPath } from '../layouts/navigation.ts'
import { today } from '../period.ts'
import type { ExpenseResponse } from '../types/api.ts'
import styles from './ExpensePages.module.css'

/**
 * Alta de un gasto (RF-13, RF-14, HU-08). Desde el tablero son tres pasos: el botón de alta, importe
 * y comercio, y guardar (RNF-14).
 *
 * Al guardar, la pantalla no se va: dice cómo quedó el gasto, con su categoría o en la bandeja, y deja
 * el formulario limpio para cargar el siguiente, que es lo común al pasar varios tickets juntos.
 */
export function NewExpensePage() {
  const location = useLocation()
  const categories = useRequest('categories', listCategories, 'No pudimos cargar las categorías.')
  const [formKey, setFormKey] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [created, setCreated] = useState<ExpenseResponse | null>(null)

  const submit = async (expense: ValidExpense) => {
    setSubmitting(true)
    setError(null)
    try {
      setCreated(await createExpense(createRequest(expense)))
      // Una key nueva monta el formulario de cero, con la fecha de hoy y el foco en el importe.
      setFormKey((key) => key + 1)
    } catch (failure) {
      // El 400 de una categoría que se dio de baja mientras el formulario estaba abierto no trae un
      // detalle para el usuario: el mensaje lo nombra como causa posible.
      setError(errorMessage(failure, 'Revisá los datos. Si elegiste una categoría, puede que se haya dado de baja.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <PageHeader title="Nuevo gasto" />

      <div className={styles.messages}>
        {/* Una región que existe desde el principio, para que el lector de pantalla anuncie el aviso. */}
        <div role="status">{created && <CreatedNotice expense={created} />}</div>
        {categories.kind === 'failed' && (
          <Notice
            tone="danger"
            action={
              <button type="button" className={styles.inlineAction} onClick={categories.retry}>
                Reintentar
              </button>
            }
          >
            {categories.message} Igual podés guardar el gasto: lo clasifican las reglas del hogar.
          </Notice>
        )}
      </div>

      <ExpenseForm
        key={formKey}
        mode="create"
        initial={emptyExpense(today())}
        categories={categoryOptions(categories.kind === 'loaded' ? categories.data : [], 'create')}
        submitting={submitting}
        error={error}
        onSubmit={submit}
      >
        <ButtonLink variant="secondary" to={backPath(location.state, '/')}>
          Volver
        </ButtonLink>
      </ExpenseForm>
    </>
  )
}

/** Cómo quedó el gasto recién guardado. Si fue a la bandeja, lo dice: hay que clasificarlo. */
function CreatedNotice({ expense }: { expense: ExpenseResponse }) {
  const what = `${formatMoney(expense.amount)} en ${expense.merchant}`
  return (
    <Notice
      action={
        <Link to={`/expenses/${expense.id}`} className={styles.inlineAction}>
          Editar
        </Link>
      }
    >
      {expense.categoryName ? (
        <>
          Guardamos {what}, en <strong>{expense.categoryName}</strong>.
        </>
      ) : (
        <>
          Guardamos {what}. Ninguna regla lo reconoció y quedó en la{' '}
          <Link to="/inbox" className={styles.inlineAction}>
            bandeja
          </Link>
          .
        </>
      )}
    </Notice>
  )
}
