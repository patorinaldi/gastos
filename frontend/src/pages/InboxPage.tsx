import { PageHeader } from '../components/layout/PageHeader.tsx'
import { EmptyState } from '../components/states/States.tsx'

export function InboxPage() {
  return (
    <>
      <PageHeader title="Bandeja" />
      <EmptyState
        title="Pantalla en construcción"
        description="Acá van a estar los gastos sin categoría, y la creación de reglas para clasificarlos."
      />
    </>
  )
}
