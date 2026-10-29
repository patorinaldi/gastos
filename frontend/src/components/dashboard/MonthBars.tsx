import type { MonthTotal } from '../../dashboard.ts'
import { formatMoney, formatMonth, formatMonthShort } from '../../format.ts'
import type { IsoMonth, Money } from '../../types/api.ts'
import styles from './MonthBars.module.css'

interface MonthBarsProps {
  months: MonthTotal[]
  /** La media histórica, sin el mes en curso (RF-27). */
  average: Money
  isSelected: (month: IsoMonth) => boolean
  onSelect: (month: IsoMonth) => void
}

/**
 * Evolución mensual del gasto contra su media (RF-36), que es también el selector de mes: tocar una
 * barra lleva el tablero a ese mes (RF-37, HU-15). La media va al pie, entre el primer y el último mes.
 *
 * Cada barra es un botón, así se elige igual con el teclado que con el dedo, y el lector de pantalla
 * anuncia el mes, su total y si está elegido. Por eso está dibujada con elementos HTML y no con la
 * librería de gráficos: con ella, el clic no llega desde el teclado.
 */
export function MonthBars({ months, average, isSelected, onSelect }: MonthBarsProps) {
  if (months.length === 0) {
    return null
  }
  const max = Math.max(...months.map((m) => m.value))

  return (
    <div className={styles.chart}>
      <div className={styles.bars} role="group" aria-label="Gasto por mes">
        {months.map((m) => (
          <button
            key={m.month}
            type="button"
            className={styles.bar}
            aria-pressed={isSelected(m.month)}
            aria-label={`${formatMonth(m.month)}: ${formatMoney(m.total)}`}
            title={`${formatMonth(m.month)}: ${formatMoney(m.total)}`}
            onClick={() => onSelect(m.month)}
          >
            <span
              className={styles.fill}
              // La altura es el dato: un estilo en línea, porque cambia con cada mes.
              style={{ height: max > 0 ? `${(m.value / max) * 100}%` : 0 }}
            />
          </button>
        ))}
      </div>
      <p className={styles.caption}>
        <span>{formatMonthShort(months[0].month)}</span>
        <span>media {formatMoney(average)}</span>
        <span>{formatMonthShort(months[months.length - 1].month)}</span>
      </p>
    </div>
  )
}