import { useSearchParams } from 'react-router'
import { getSummary } from '../api/analysis.ts'
import { listCategories } from '../api/categories.ts'
import { listExpenses } from '../api/expenses.ts'
import { RecentExpenses } from '../components/dashboard/RecentExpenses.tsx'
import { PeriodStepper } from '../components/expenses/PeriodStepper.tsx'
import { PageHeader } from '../components/layout/PageHeader.tsx'
import { EmptyState, ErrorState, LoadingState } from '../components/states/States.tsx'
import { Button, ButtonLink } from '../components/ui/Button.tsx'
import { SelectField, type SelectOption } from '../components/ui/SelectField.tsx'
import { categoryColors, UNCATEGORIZED_LABEL } from '../dashboard.ts'
import { filterParams, filterQuery, hasFilters, parseFilters, type ExpenseFilters } from '../expenseFilters.ts'
import { PAYMENT_METHODS } from '../expenseForm.ts'
import { formatMonth } from '../format.ts'
import { useRequest } from '../hooks/useRequest.ts'
import { NEW_EXPENSE_PATH } from '../layouts/navigation.ts'
import { currentMonth, periodRange, type Period } from '../period.ts'
import { UNCATEGORIZED, type CategoryResponse, type PaymentMethod, type SummaryResponse } from '../types/api.ts'
import styles from './ExpensesPage.module.css'

/** Movimientos por página. El tope de la API es 100 (RNF-12). */
const PAGE_SIZE = 20

const ALL = ''

/**
 * Movimientos (RF-17, RF-18, HU-10): todos los gastos del período, de a una página, con filtros por
 * categoría, persona y medio de pago que se combinan. Cada fila abre su edición.
 *
 * Los filtros van en la dirección (ver expenseFilters.ts): sobreviven a una recarga, "atrás" deshace
 * el último cambio, y la edición vuelve a la misma página con los mismos filtros.
 */
export function ExpensesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const current = currentMonth()
  const filters = parseFilters(searchParams, current)
  const title = periodTitle(filters.period)

  // Cambiar un filtro vuelve a la primera página: la que se estaba mirando puede no existir con el
  // filtro nuevo.
  const update = (changes: Partial<ExpenseFilters>) =>
    setSearchParams(filterParams({ ...filters, page: 0, ...changes }, current))

  const query = filterQuery(filters, PAGE_SIZE)
  const expenses = useRequest(JSON.stringify(query), () => listExpenses(query), 'No pudimos cargar los movimientos.')

  // Las personas y las categorías que se ofrecen como filtro salen del resumen del período, sin los
  // demás filtros: así aparece también quien ya se fue del hogar pero tiene gastos (RN-18), y las
  // categorías dadas de baja que todavía tienen gastos (RN-16). Si el resumen falla, el listado
  // funciona igual: solo quedan menos opciones.
  const range = periodRange(filters.period)
  const summary = useRequest(`${range.from}/${range.to}`, () => getSummary(range), 'No pudimos cargar los filtros.')
  const categories = useRequest('categories', listCategories, 'No pudimos cargar las categorías.')
  const summaryData = summary.kind === 'loaded' ? summary.data : null

  return (
    <>
      <PageHeader title="Movimientos" />

      <section className={styles.filters} aria-label="Filtros">
        <PeriodStepper period={filters.period} current={current} onChange={(period) => update({ period })} />
        <div className={styles.selects}>
          <SelectField
            label="Categoría"
            size="compact"
            options={categoryFilterOptions(categories.kind === 'loaded' ? categories.data : [], summaryData, filters.categoryId)}
            value={filters.categoryId ?? ALL}
            onChange={(event) => update({ categoryId: event.target.value || undefined })}
          />
          <SelectField
            label="Persona"
            size="compact"
            options={ownerFilterOptions(summaryData, filters.ownerId)}
            value={filters.ownerId ?? ALL}
            onChange={(event) => update({ ownerId: event.target.value || undefined })}
          />
          <SelectField
            label="Medio de pago"
            size="compact"
            options={[{ value: ALL, label: 'Todos' }, ...PAYMENT_METHODS.map((m) => ({ value: m, label: m }))]}
            value={filters.paymentMethod ?? ALL}
            onChange={(event) => update({ paymentMethod: (event.target.value || undefined) as PaymentMethod | undefined })}
          />
        </div>
      </section>

      {expenses.kind === 'loading' ? (
        <LoadingState label="Cargando movimientos…" />
      ) : expenses.kind === 'failed' ? (
        <ErrorState message={expenses.message} onRetry={expenses.retry} />
      ) : expenses.data.totalElements === 0 ? (
        hasFilters(filters) ? (
          <EmptyState
            title="Ningún movimiento cumple estos filtros"
            description={`En ${title} no hay gastos con esa combinación. Probá quitando algún filtro u otro período.`}
            action={
              <Button
                variant="secondary"
                onClick={() => update({ categoryId: undefined, ownerId: undefined, paymentMethod: undefined })}
              >
                Quitar filtros
              </Button>
            }
          />
        ) : (
          <EmptyState
            title={`No hay movimientos en ${title}`}
            description="Probá con otro período, o cargá un gasto para verlo acá."
            action={<ButtonLink to={NEW_EXPENSE_PATH}>Añadir gasto</ButtonLink>}
          />
        )
      ) : expenses.data.content.length === 0 ? (
        // La página de la dirección ya no existe: por ejemplo, se borraron gastos desde otra pestaña.
        <EmptyState
          title="Esta página quedó vacía"
          action={
            <Button variant="secondary" onClick={() => update({})}>
              Ir a la primera página
            </Button>
          }
        />
      ) : (
        <>
          <RecentExpenses
            title={`Movimientos de ${title}`}
            page={expenses.data}
            categoryColor={summaryData ? categoryColors(summaryData) : () => undefined}
            showCount={false}
          />
          <Pagination
            page={expenses.data.page}
            totalPages={expenses.data.totalPages}
            totalElements={expenses.data.totalElements}
            onChange={(page) => {
              update({ page })
              window.scrollTo({ top: 0 })
            }}
          />
        </>
      )}
    </>
  )
}

function periodTitle(period: Period): string {
  return period.kind === 'month' ? formatMonth(period.month) : String(period.year)
}

/**
 * Las categorías activas, más las dadas de baja que tienen gastos en el período, y la bandeja. Si la
 * de la dirección no está entre ellas, se agrega igual: el select tiene que mostrar el filtro que se
 * está aplicando.
 */
function categoryFilterOptions(
  categories: CategoryResponse[],
  summary: SummaryResponse | null,
  selected: string | undefined,
): SelectOption[] {
  const options: SelectOption[] = categories.map((c) => ({ value: c.id, label: c.name }))
  for (const total of summary?.byCategory ?? []) {
    if (total.categoryId && !options.some((o) => o.value === total.categoryId)) {
      options.push({ value: total.categoryId, label: total.name ?? 'Categoría' })
    }
  }
  if (selected && selected !== UNCATEGORIZED && !options.some((o) => o.value === selected)) {
    options.push({ value: selected, label: 'Categoría elegida' })
  }
  return [{ value: ALL, label: 'Todas' }, { value: UNCATEGORIZED, label: UNCATEGORIZED_LABEL }, ...options]
}

/** Las personas con gastos en el período, y la de la dirección aunque no tenga. */
function ownerFilterOptions(summary: SummaryResponse | null, selected: string | undefined): SelectOption[] {
  const options: SelectOption[] = (summary?.byOwner ?? []).map((o) => ({ value: o.ownerId, label: o.name }))
  if (selected && !options.some((o) => o.value === selected)) {
    options.push({ value: selected, label: 'Persona sin gastos en el período' })
  }
  return [{ value: ALL, label: 'Todas' }, ...options]
}

interface PaginationProps {
  page: number
  totalPages: number
  totalElements: number
  onChange: (page: number) => void
}

function Pagination({ page, totalPages, totalElements, onChange }: PaginationProps) {
  return (
    <nav className={styles.pagination} aria-label="Páginas">
      <Button variant="secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Anterior
      </Button>
      <p className={styles.pageInfo}>
        {totalPages > 1 && <>Página {page + 1} de {totalPages} · </>}
        {totalElements} {totalElements === 1 ? 'movimiento' : 'movimientos'}
      </p>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Siguiente
      </Button>
    </nav>
  )
}
