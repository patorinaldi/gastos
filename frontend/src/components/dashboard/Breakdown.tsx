import { lazy, Suspense } from 'react'
import { DIMENSIONS, slices, type Dimension, type Focus } from '../../dashboard.ts'
import { formatMoney, formatMoneyCompact } from '../../format.ts'
import type { SummaryResponse } from '../../types/api.ts'
import styles from './Breakdown.module.css'

// Recharts solo lo usa este anillo: se descarga al abrir el tablero, no con el login ni el registro.
const Donut = lazy(() => import('./Donut.tsx'))

interface BreakdownProps {
  summary: SummaryResponse
  dimension: Dimension
  onDimension: (dimension: Dimension) => void
  focus: Focus | null
  onFocus: (focus: Focus | null) => void
}

/**
 * Distribución del gasto del período por categoría, persona o medio de pago (RF-35). Elegir una
 * porción, en el anillo o en la leyenda, filtra el resto del tablero a ella (RF-37); volver a elegirla,
 * o tocar la cruz, quita el filtro.
 *
 * El anillo muestra siempre el período completo, también con una porción elegida: así se ve cuánto
 * pesa esa porción en el total.
 */
export function Breakdown({ summary, dimension, onDimension, focus, onFocus }: BreakdownProps) {
  const items = slices(summary, dimension)
  const selectedKey = focus?.dimension === dimension ? focus.key : null

  const toggle = (key: string) => onFocus(selectedKey === key ? null : { dimension, key })

  return (
    <section className={styles.breakdown} aria-label="Distribución del gasto">
      <div className={styles.toolbar}>
        <div className={styles.chips} role="group" aria-label="Repartir por">
          {DIMENSIONS.map((d) => (
            <button
              key={d.value}
              type="button"
              className={styles.chip}
              aria-pressed={dimension === d.value}
              onClick={() => onDimension(d.value)}
            >
              {d.label}
            </button>
          ))}
        </div>
        {focus && (
          <button type="button" className={styles.reset} aria-label="Quitar filtro" title="Quitar filtro" onClick={() => onFocus(null)}>
            <span aria-hidden="true">×</span>
          </button>
        )}
      </div>

      <div className={styles.body}>
        <div className={styles.donut}>
          {/* Mientras se descarga Recharts, el lugar del anillo queda reservado y vacío. */}
          <Suspense fallback={null}>
            <Donut slices={items} selectedKey={selectedKey} onSelect={toggle} />
          </Suspense>
          {/* Abreviado, para que entre en el centro: el total exacto está arriba, en el resumen, y por
              eso esta copia queda oculta para el lector de pantalla. */}
          <p className={styles.hole} aria-hidden="true">
            <span className={styles.holeLabel}>Total</span>
            <span className={styles.holeValue}>{formatMoneyCompact(summary.total)}</span>
          </p>
        </div>

        <ul className={styles.legend}>
          {items.map((slice) => (
            <li key={slice.key}>
              <button
                type="button"
                className={styles.item}
                aria-pressed={selectedKey === slice.key}
                onClick={() => toggle(slice.key)}
              >
                <span className={styles.dot} style={{ background: slice.color }} aria-hidden="true" />
                <span className={styles.name}>{slice.label}</span>
                <span className={styles.amount}>{formatMoney(slice.total)}</span>
                <span className={styles.share}>{Math.round(slice.share)}%</span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}