import { EmptyState } from '../components/states/States.tsx'
import { ButtonLink } from '../components/ui/Button.tsx'

export function NotFoundPage() {
  return (
    <main>
      <EmptyState
        title="Página no encontrada"
        description="La dirección no existe o cambió."
        action={<ButtonLink to="/">Volver al tablero</ButtonLink>}
      />
    </main>
  )
}
