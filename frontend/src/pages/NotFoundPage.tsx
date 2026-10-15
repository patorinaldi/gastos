import { EmptyState } from '../components/states/States.tsx'
import { ButtonLink } from '../components/ui/Button.tsx'

/** Se muestra dentro de AuthLayout, que pone la marca y el main. */
export function NotFoundPage() {
  return (
    <EmptyState
      titleAs="h1"
      title="Página no encontrada"
      description="La dirección no existe o cambió."
      action={<ButtonLink to="/">Volver al tablero</ButtonLink>}
    />
  )
}