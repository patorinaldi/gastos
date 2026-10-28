import { formatMonth } from '../../format.ts'
import { shiftMonth } from '../../period.ts'
import type { IsoMonth } from '../../types/api.ts'
import styles from './PeriodSelector.module.css'

interface PeriodSelectorProps {
  month: IsoMonth
  /** El mes en curso: no se puede avanzar más allá. */
  current: IsoMonth
  onChange: (month: IsoMonth) => void
}

/**
 * Selector del período del tablero, de a un mes (RF-34, RD-07). No avanza más allá del mes en curso:
 * un mes que todavía no empezó no tiene gastos que mostrar.
 */
export function PeriodSelector({ month, current, onChange }: PeriodSelectorProps) {
  const atCurrent = month >= current

  return (
    <div className={styles.selector} role="group" aria-label="Período">
      <button
        type="button"
        className={styles.step}
        aria-label="Mes anterior"
        onClick={() => onChange(shiftMonth(month, -1))}
      >
        <span aria-hidden="true">‹</span>
      </button>
      {/* Se anuncia al cambiar, para que quien usa lector de pantalla sepa qué mes quedó elegido. */}
      <p className={styles.label} aria-live="polite">
        {formatMonth(month)}
      </p>
      <button
        type="button"
        className={styles.step}
        aria-label="Mes siguiente"
        disabled={atCurrent}
        onClick={() => onChange(shiftMonth(month, 1))}
      >
        <span aria-hidden="true">›</span>
      </button>
      {!atCurrent && (
        <button type="button" className={styles.reset} onClick={() => onChange(current)}>
          Este mes
        </button>
      )}
    </div>
  )
}