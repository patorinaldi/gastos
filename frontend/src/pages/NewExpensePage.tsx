import { PageHeader } from '../components/layout/PageHeader.tsx'
import { EmptyState } from '../components/states/States.tsx'

export function NewExpensePage() {
  return (
    <>
      <PageHeader title="Nuevo gasto" />
      <EmptyState
        title="Pantalla en construcción"
        description="Acá va a estar el alta de un gasto: importe y comercio, y opcionalmente fecha, medio de pago y categoría."
      />
    </>
  )
}
