import { useEffect } from 'react'
import { useRouteError } from 'react-router'
import { ErrorState } from '../components/states/States.tsx'
import { ButtonLink } from '../components/ui/Button.tsx'
import { routeErrorCopy } from '../routeError.ts'
import styles from './RouteErrorPage.module.css'

interface RouteErrorPageProps {
  /**
   * Cuando falla el layout mismo y esta pantalla lo reemplaza entero: lleva su propio margen. Dentro
   * de un layout, el layout ya lo pone.
   */
  standalone?: boolean
}

/**
 * Lo que se ve cuando una pantalla falla al dibujarse (RNF-16, RNF-17). Reemplaza a la de React
 * Router, que en producción muestra el mensaje y el stack de la excepción en inglés y no ofrece cómo
 * seguir. Ver routeError.ts.
 */
export function RouteErrorPage({ standalone = false }: RouteErrorPageProps) {
  const error = useRouteError()
  const { title, description } = routeErrorCopy(error)

  // El detalle queda en la consola, para quien tenga que diagnosticarlo, y no en la pantalla.
  useEffect(() => {
    console.error('Error al mostrar la pantalla:', error)
  }, [error])

  return (
    <div className={standalone ? styles.standalone : undefined}>
      <ErrorState
        title={title}
        titleAs="h1"
        message={description}
        retryLabel="Recargar la página"
        onRetry={() => window.location.reload()}
      />
      <p className={styles.home}>
        <ButtonLink variant="link" to="/">
          Ir al tablero
        </ButtonLink>
      </p>
    </div>
  )
}
