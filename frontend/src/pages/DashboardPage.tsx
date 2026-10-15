import { PageHeader } from '../components/layout/PageHeader.tsx'
import { EmptyState } from '../components/states/States.tsx'

export function DashboardPage() {
  return (
    <>
      <PageHeader title="Tablero" />
      <EmptyState
        title="Pantalla en construcción"
        description="Acá van a estar el total del período, los gráficos de distribución y evolución, y los últimos movimientos."
      />
    </>
  )
}
