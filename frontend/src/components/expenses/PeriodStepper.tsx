import { formatMonth } from '../../format.ts'
import { shiftMonth, type Period } from '../../period.ts'
import type { IsoMonth } from '../../types/api.ts'
import styles from './PeriodStepper.module.css'

interface PeriodStepperProps {
  period: Period
  /** El mes en curso: el último al que se puede avanzar. */
  current: IsoMonth
  onChange: (period: Period) => void
}

/**
 * El período del listado de movimientos: un mes o un año, y flechas para ir al anterior o al
 * siguiente. A diferencia del tablero, acá no hay barras de evolución para elegir un mes viejo.
 * No avanza más allá del período en curso, que es el último con gastos.
 */
export function PeriodStepper({ period, current, onChange }: PeriodStepperProps) {
  const currentYear = Number(current.slice(0, 4))
  const atLatest = period.kind === 'month' ? period.month >= current : period.year >= currentYear
  const label = period.kind === 'month' ? formatMonth(period.month) : String(period.year)

  const step = (delta: number) =>
    onChange(
      period.kind === 'month'
        ? { kind: 'month', month: shiftMonth(period.month, delta) }
        : { kind: 'year', year: period.year + delta },
    )

  // Al pasar de un año a un mes, el mes en curso si es este año, y si no, diciembre de ese año.
  const toMonth = () =>
    period.kind === 'year' &&
    onChange({ kind: 'month', month: period.year >= currentYear ? current : `${period.year}-12` })
  const toYear = () => period.kind === 'month' && onChange({ kind: 'year', year: Number(period.month.slice(0, 4)) })

  return (
    <div className={styles.stepper}>
      <div className={styles.segmented} role="group" aria-label="Agrupar por">
        <button type="button" className={styles.segment} aria-pressed={period.kind === 'month'} onClick={toMonth}>
          Mes
        </button>
        <button type="button" className={styles.segment} aria-pressed={period.kind === 'year'} onClick={toYear}>
          Año
        </button>
      </div>

      <div className={styles.nav} role="group" aria-label="Período">
        <button
          type="button"
          className={styles.arrow}
          onClick={() => step(-1)}
          aria-label={period.kind === 'month' ? 'Mes anterior' : 'Año anterior'}
        >
          <span aria-hidden="true">‹</span>
        </button>
        <span className={styles.label} aria-live="polite">
          {label}
        </span>
        <button
          type="button"
          className={styles.arrow}
          onClick={() => step(1)}
          disabled={atLatest}
          aria-label={period.kind === 'month' ? 'Mes siguiente' : 'Año siguiente'}
        >
          <span aria-hidden="true">›</span>
        </button>
      </div>
    </div>
  )
}
