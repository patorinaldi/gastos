import type { Period } from '../../period.ts'
import type { IsoMonth } from '../../types/api.ts'
import styles from './PeriodSelector.module.css'

interface PeriodSelectorProps {
  period: Period
  /** El mes en curso, que define "Este mes" y "Año". */
  current: IsoMonth
  onChange: (period: Period) => void
}

/**
 * Atajos de período del tablero (RF-34): el mes en curso o el año en curso. Un mes anterior se elige
 * tocando su barra en la evolución mensual; en ese caso ninguno de los dos queda marcado.
 */
export function PeriodSelector({ period, current, onChange }: PeriodSelectorProps) {
  const currentYear = Number(current.slice(0, 4))
  const thisMonth = period.kind === 'month' && period.month === current
  const thisYear = period.kind === 'year' && period.year === currentYear

  return (
    <div className={styles.segmented} role="group" aria-label="Período">
      <button
        type="button"
        className={styles.segment}
        aria-pressed={thisMonth}
        onClick={() => onChange({ kind: 'month', month: current })}
      >
        Este mes
      </button>
      <button
        type="button"
        className={styles.segment}
        aria-pressed={thisYear}
        onClick={() => onChange({ kind: 'year', year: currentYear })}
      >
        Año
      </button>
    </div>
  )
}