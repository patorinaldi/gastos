import { useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { getMonthly, getSummary } from '../api/analysis.ts'
import { listExpenses } from '../api/expenses.ts'
import { Breakdown } from '../components/dashboard/Breakdown.tsx'
import { MonthBars } from '../components/dashboard/MonthBars.tsx'
import { PeriodSelector } from '../components/dashboard/PeriodSelector.tsx'
import { RecentExpenses } from '../components/dashboard/RecentExpenses.tsx'
import { PageHeader } from '../components/layout/PageHeader.tsx'
import { SidebarContent } from '../layouts/SidebarSlot.tsx'
import { EmptyState, ErrorState, LoadingState } from '../components/states/States.tsx'
import { focusFilter, monthSeries, parseFocus, slices, type Dimension, type Focus } from '../dashboard.ts'
import { formatMoneyParts, formatMonth } from '../format.ts'
import { useRequest } from '../hooks/useRequest.ts'
import {
  currentMonth,
  includesMonth,
  monthRange,
  monthsUntil,
  parsePeriod,
  periodParams,
  periodRange,
  type Period,
} from '../period.ts'
import type { MonthlySeriesResponse, SummaryResponse } from '../types/api.ts'
import styles from './DashboardPage.module.css'

/** Cuántos meses muestra la evolución: un año, para comparar cada mes con el mismo del año anterior. */
const MONTHS_SHOWN = 12

/** Cuántos movimientos muestra el tablero. El resto está en la pantalla de movimientos. */
const RECENT_EXPENSES = 8

/**
 * Tablero (RF-34 a RF-37): el total del período, su evolución mensual contra la media, su
 * distribución y los últimos movimientos. Por defecto, el mes en curso (RD-07).
 *
 * El período y la porción elegida van en la dirección (`/?month=2026-07&categoryId=...`), con los
 * mismos nombres que los filtros de la API: así sobreviven a una recarga, se pueden compartir, y
 * "atrás" deshace el último cambio.
 *
 * En escritorio, el resumen va en la barra lateral, debajo de la navegación, y los movimientos ocupan
 * el contenido; en móvil, el resumen va arriba de los movimientos.
 */
export function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const current = currentMonth()
  const period = parsePeriod(searchParams, current)
  const focus = parseFocus(searchParams)
  const [dimension, setDimension] = useState<Dimension>(focus?.dimension ?? 'category')

  const navigate = (next: { period?: Period; focus?: Focus | null }) => {
    const nextPeriod = next.period ?? period
    const nextFocus = next.focus === undefined ? focus : next.focus
    setSearchParams({ ...periodParams(nextPeriod, current), ...focusFilter(nextFocus) })
  }

  // El anillo muestra el período completo, sin la porción elegida: así se ve cuánto pesa en el total.
  const range = periodRange(period)
  const summary = useRequest(
    `${range.from}/${range.to}`,
    () => getSummary(range),
    'No pudimos cargar el tablero.',
  )

  // La evolución sí se filtra por la porción: muestra cómo viene esa categoría, persona o medio de pago.
  const window = monthsUntil(current, MONTHS_SHOWN)
  const monthlyFilter = { from: `${window[0]}-01`, to: monthRange(current).to, ...focusFilter(focus) }
  const monthly = useRequest(
    JSON.stringify(monthlyFilter),
    () => getMonthly(monthlyFilter),
    'No pudimos cargar la evolución mensual.',
  )

  // Los movimientos se filtran por el período y por la porción, como el total.
  const expensesFilter = { ...range, ...focusFilter(focus), size: RECENT_EXPENSES }
  const expenses = useRequest(
    JSON.stringify(expensesFilter),
    () => listExpenses(expensesFilter),
    'No pudimos cargar los movimientos.',
  )

  const title = periodTitle(period)

  // El resumen se dibuja dos veces y CSS muestra uno, como decide el shell la disposición: en
  // escritorio en la barra lateral, en móvil arriba del contenido. El oculto, con display: none,
  // tampoco lo lee el lector de pantalla.
  const panel = summary.kind === 'loaded' && (
    <section className={styles.panel} aria-label={`Resumen de ${title}`}>
      <PeriodSelector period={period} current={current} onChange={(p) => navigate({ period: p })} />

      <Total
        summary={summary.data}
        title={title}
        focus={focus}
        count={expenses.kind === 'loaded' ? expenses.data.totalElements : null}
      >
        {monthly.kind === 'failed' ? (
          <p className={styles.inlineError} role="alert">
            {monthly.message}{' '}
            <button type="button" className={styles.inlineAction} onClick={monthly.retry}>
              Reintentar
            </button>
          </p>
        ) : monthly.kind === 'loaded' ? (
          <Evolution
            monthly={monthly.data}
            window={window}
            period={period}
            onSelect={(month) => navigate({ period: { kind: 'month', month } })}
          />
        ) : null}
      </Total>

      <hr className={styles.divider} />

      {/* Un período sin gastos trae total "0.00" y las listas vacías: el vacío se decide por las listas. */}
      {summary.data.byPaymentMethod.length === 0 ? (
        <p className={styles.muted}>
          Sin gastos en {title}.{' '}
          {focus && (
            <button type="button" className={styles.inlineAction} onClick={() => navigate({ focus: null })}>
              Quitar filtro
            </button>
          )}
        </p>
      ) : (
        <Breakdown
          summary={summary.data}
          dimension={dimension}
          onDimension={setDimension}
          focus={focus}
          onFocus={(f) => navigate({ focus: f })}
        />
      )}
    </section>
  )

  return (
    <>
      <PageHeader title="Tablero" />
      <SidebarContent>{panel}</SidebarContent>
      {summary.kind === 'loading' ? (
        <LoadingState />
      ) : summary.kind === 'failed' ? (
        <ErrorState message={summary.message} onRetry={summary.retry} />
      ) : (
        <div className={styles.layout}>
          <div className={styles.inlinePanel}>{panel}</div>

          <div className={styles.movements}>
            {expenses.kind === 'loading' ? (
              <LoadingState label="Cargando movimientos…" />
            ) : expenses.kind === 'failed' ? (
              <ErrorState message={expenses.message} onRetry={expenses.retry} />
            ) : expenses.data.totalElements === 0 ? (
              <EmptyState
                title={`No hay movimientos en ${title}`}
                description="Elegí otro mes en la evolución, o cargá un gasto para verlo acá."
              />
            ) : (
              <RecentExpenses
                title={`Movimientos de ${title}`}
                page={expenses.data}
                categoryColor={categoryColors(summary.data)}
              />
            )}
          </div>
        </div>
      )}
    </>
  )
}

function periodTitle(period: Period): string {
  return period.kind === 'month' ? formatMonth(period.month) : String(period.year)
}

/** El color de cada categoría en el anillo, para que la tabla de movimientos use el mismo. */
function categoryColors(summary: SummaryResponse): (categoryId: string) => string | undefined {
  const colors = new Map(slices(summary, 'category').map((s) => [s.key, s.color]))
  return (categoryId) => colors.get(categoryId)
}

interface TotalProps {
  summary: SummaryResponse
  title: string
  focus: Focus | null
  /** Cuántos movimientos tiene el período, cuando ya se sabe. */
  count: number | null
  children: ReactNode
}

/**
 * El total del período (RF-25). Con una porción elegida, el total es el de esa porción, y el pie dice
 * cuál es.
 */
function Total({ summary, title, focus, count, children }: TotalProps) {
  const slice = focus ? slices(summary, focus.dimension).find((s) => s.key === focus.key) : undefined
  // Una porción sin gastos en el período, por ejemplo una categoría elegida en otro mes, suma cero.
  const total = formatMoneyParts(focus ? (slice?.total ?? '0.00') : summary.total)

  return (
    <div className={styles.summary}>
      <h2 className={styles.total}>
        <span className="visually-hidden">Total de {title}: </span>
        <span className={styles.currency}>{total.currency}</span> {total.number}
      </h2>
      <p className={styles.meta}>
        <span className={styles.metaTitle}>{title}</span>
        {slice && <span>· {slice.label}</span>}
        {count !== null && (
          <span>
            · {count} {count === 1 ? 'movimiento' : 'movimientos'}
          </span>
        )}
      </p>
      {children}
    </div>
  )
}

interface EvolutionProps {
  monthly: MonthlySeriesResponse
  window: string[]
  period: Period
  onSelect: (month: string) => void
}

function Evolution({ monthly, window, period, onSelect }: EvolutionProps) {
  return (
    <MonthBars
      months={monthSeries(monthly.months, window)}
      average={monthly.average}
      isSelected={(month) => includesMonth(period, month)}
      onSelect={onSelect}
    />
  )
}