import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { getSummary } from '../api/analysis.ts'
import { errorMessage } from '../api/client.ts'
import { PeriodSelector } from '../components/dashboard/PeriodSelector.tsx'
import { PageHeader } from '../components/layout/PageHeader.tsx'
import { EmptyState, ErrorState, LoadingState } from '../components/states/States.tsx'
import { formatMonth, formatMoney } from '../format.ts'
import { currentMonth, monthFromParam, monthRange } from '../period.ts'
import type { IsoMonth, SummaryResponse } from '../types/api.ts'
import styles from './DashboardPage.module.css'

/** El resultado de un pedido, con el pedido al que responde. */
type Result =
  | { request: string; kind: 'loaded'; summary: SummaryResponse }
  | { request: string; kind: 'failed'; message: string }

/**
 * Tablero (RF-34): el total del período elegido. Por defecto, el mes en curso (RD-07).
 *
 * El mes va en la dirección, `/?month=2026-09`: así el período sobrevive a una recarga, se puede
 * compartir, y "atrás" vuelve al mes anterior que se miró.
 */
export function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const current = currentMonth()
  // Un mes inválido o futuro en la dirección se ignora: muestra el mes en curso en lugar de un error.
  const month = monthFromParam(searchParams.get('month'), current)
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<Result | null>(null)

  // Cada pedido se identifica por el mes y el intento. Si el resultado guardado es de otro pedido,
  // la pantalla está cargando: no hace falta un estado aparte que haya que acordarse de reiniciar.
  const request = `${month}#${attempt}`

  useEffect(() => {
    // Una respuesta que llega después de cambiar de mes ya no es la de la pantalla, y se descarta.
    let active = true
    getSummary(monthRange(month)).then(
      (summary) => active && setResult({ request, kind: 'loaded', summary }),
      (error: unknown) =>
        active && setResult({ request, kind: 'failed', message: errorMessage(error, 'No pudimos cargar el tablero.') }),
    )
    return () => {
      active = false
    }
  }, [month, request])

  const changeMonth = (next: IsoMonth) => {
    // El mes en curso es el valor por defecto: no hace falta escribirlo en la dirección.
    setSearchParams(next === current ? {} : { month: next })
  }

  return (
    <>
      <PageHeader
        title="Tablero"
        actions={<PeriodSelector month={month} current={current} onChange={changeMonth} />}
      />
      {result?.request !== request ? (
        <LoadingState />
      ) : result.kind === 'failed' ? (
        <ErrorState message={result.message} onRetry={() => setAttempt((n) => n + 1)} />
      ) : (
        <Summary month={month} current={current} summary={result.summary} />
      )}
    </>
  )
}

interface SummaryProps {
  month: IsoMonth
  current: IsoMonth
  summary: SummaryResponse
}

function Summary({ month, current, summary }: SummaryProps) {
  // Un período sin gastos trae total "0.00" y las listas vacías: el vacío se decide por las listas.
  if (summary.byPaymentMethod.length === 0) {
    return (
      <EmptyState
        title={month === current ? 'Todavía no hay gastos este mes' : `No hubo gastos en ${formatMonth(month)}`}
        description={
          month === current
            ? 'Cuando cargues un gasto, el total del mes aparece acá.'
            : 'Elegí otro mes para ver su total.'
        }
      />
    )
  }

  return (
    <section className={styles.kpi} aria-labelledby="dashboard-total">
      <h2 id="dashboard-total" className={styles.kpiLabel}>
        Total de {formatMonth(month)}
      </h2>
      <p className={styles.kpiValue}>{formatMoney(summary.total)}</p>
    </section>
  )
}