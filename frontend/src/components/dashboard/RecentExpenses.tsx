import { Link } from 'react-router'
import { UNCATEGORIZED_LABEL } from '../../dashboard.ts'
import { formatDayMonth, formatMoney } from '../../format.ts'
import type { ExpenseResponse, PageResponse } from '../../types/api.ts'
import styles from './RecentExpenses.module.css'

interface RecentExpensesProps {
  title: string
  page: PageResponse<ExpenseResponse>
  /** El color de cada categoría en el anillo, para que la tabla use el mismo. */
  categoryColor: (categoryId: string) => string | undefined
}

/**
 * Los últimos movimientos del período, con los mismos filtros que el resto del tablero (RF-34). Es un
 * vistazo: el listado completo, con sus filtros y la edición, es la pantalla de movimientos.
 *
 * En móvil es una lista; desde 768 px, una tabla con fecha, comercio, categoría, persona e importe.
 * Las dos salen del mismo marcado: la tabla es la lista con las columnas a la vista.
 */
export function RecentExpenses({ title, page, categoryColor }: RecentExpensesProps) {
  return (
    <section className={styles.recent} aria-labelledby="recent-title">
      <div className={styles.header}>
        <h2 id="recent-title" className={styles.title}>
          {title}
        </h2>
        {page.totalElements > page.content.length && (
          <Link to="/expenses" className={styles.all}>
            Ver todos
          </Link>
        )}
      </div>

      <div className={styles.table} role="table" aria-labelledby="recent-title">
        <div className={styles.head} role="row">
          <span role="columnheader" className={styles.date}>Fecha</span>
          <span role="columnheader" className={styles.merchantCol}>Comercio</span>
          <span role="columnheader" className={styles.category}>Categoría</span>
          <span role="columnheader" className={styles.owner}>Persona</span>
          <span role="columnheader" className={styles.amountCol}>Importe</span>
        </div>

        {page.content.map((expense) => {
          const color = expense.categoryId ? categoryColor(expense.categoryId) : undefined
          return (
            <div key={expense.id} className={styles.row} role="row">
              <span role="cell" className={`${styles.date} ${styles.mono}`}>
                {formatDayMonth(expense.expenseDate)}
              </span>
              <span role="cell" className={styles.merchantCol}>
                <span className={styles.merchant}>{expense.merchant}</span>
                {/* En móvil no hay columnas: la fecha, la categoría y la persona van debajo del comercio. */}
                <span className={styles.meta}>
                  {formatDayMonth(expense.expenseDate)} · {expense.categoryName ?? expense.paymentMethod} ·{' '}
                  {expense.ownerName}
                </span>
              </span>
              {/* Lo que falta clasificar se marca también en móvil, donde la columna no se ve. */}
              <span role="cell" className={expense.categoryName ? styles.category : `${styles.category} ${styles.pendingCell}`}>
                {expense.categoryName ? (
                  <span className={styles.categoryCell}>
                    {color && <span className={styles.dot} style={{ background: color }} aria-hidden="true" />}
                    {expense.categoryName}
                  </span>
                ) : (
                  <span className={styles.pending}>{UNCATEGORIZED_LABEL}</span>
                )}
              </span>
              <span role="cell" className={`${styles.owner} ${styles.sub}`}>
                {expense.ownerName}
              </span>
              <span role="cell" className={styles.amountCol}>
                <span className={styles.amount}>{formatMoney(expense.amount)}</span>
              </span>
            </div>
          )
        })}
      </div>

      <p className={styles.count}>
        {page.content.length} de {page.totalElements} {page.totalElements === 1 ? 'movimiento' : 'movimientos'}
      </p>
    </section>
  )
}