import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'
import type { Slice } from '../../dashboard.ts'

interface DonutProps {
  slices: Slice[]
  /** La porción elegida, que queda resaltada; las demás se atenúan. */
  selectedKey: string | null
  onSelect: (key: string) => void
}

/**
 * El anillo de la distribución (RF-35). Se carga aparte del resto de la app, solo en el tablero: es la
 * única pantalla que usa Recharts.
 *
 * Ocupa la caja que le da el CSS del contenedor (`--donut-size`), y los radios van en porcentaje: el
 * tamaño se define en un solo lugar.
 *
 * Es solo la parte visual. La leyenda de al lado tiene los mismos datos y los mismos clics como
 * botones, que es lo que usan el teclado y el lector de pantalla; por eso el anillo queda oculto para
 * ellos y sin la capa de accesibilidad de Recharts.
 */
export default function Donut({ slices, selectedKey, onSelect }: DonutProps) {
  return (
    <ResponsiveContainer width="100%" height="100%" aria-hidden="true">
      <PieChart margin={{ top: 0, right: 0, bottom: 0, left: 0 }} accessibilityLayer={false}>
        <Pie
          data={slices}
          dataKey="value"
          nameKey="label"
          cx="50%"
          cy="50%"
          // Un anillo fino deja lugar en el centro para el total.
          innerRadius="70%"
          outerRadius="99%"
          startAngle={90}
          endAngle={-270}
          stroke="none"
          isAnimationActive={false}
          onClick={(_: unknown, index: number) => onSelect(slices[index].key)}
        >
          {slices.map((slice) => (
            <Cell
              key={slice.key}
              fill={slice.color}
              fillOpacity={selectedKey === null || selectedKey === slice.key ? 1 : 0.35}
              cursor="pointer"
            />
          ))}
        </Pie>
      </PieChart>
    </ResponsiveContainer>
  )
}