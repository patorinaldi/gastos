import { PageHeader } from '../components/layout/PageHeader.tsx'
import { EmptyState } from '../components/states/States.tsx'

export function ExpensesPage() {
  return (
    <>
      <PageHeader title="Movimientos" />
      <EmptyState
        title="Pantalla en construcción"
        description="Acá va a estar el listado de gastos, con filtros por período, categoría, integrante y medio de pago."
      />
    </>
  )
}
