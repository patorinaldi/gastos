import { PageHeader } from '../components/layout/PageHeader.tsx'
import { EmptyState } from '../components/states/States.tsx'

export function HouseholdPage() {
  return (
    <>
      <PageHeader title="Hogar" />
      <EmptyState
        title="Pantalla en construcción"
        description="Acá van a estar los integrantes del hogar, las invitaciones y el canje de un código."
      />
    </>
  )
}
