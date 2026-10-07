import type { ReactNode } from 'react'
import { Button } from '../ui/Button.tsx'
import styles from './States.module.css'

/*
 * Los tres estados de toda pantalla que consulta el servidor (RNF-16). Se distinguen a simple
 * vista, y cada uno se anuncia distinto a un lector de pantalla: la carga como estado, el error
 * como alerta.
 */

interface LoadingStateProps {
  label?: string
}

export function LoadingState({ label = 'Cargando…' }: LoadingStateProps) {
  return (
    <div className={styles.state} role="status">
      <span className={styles.spinner} aria-hidden="true" />
      <p className={styles.label}>{label}</p>
    </div>
  )
}

interface EmptyStateProps {
  title: string
  description?: string
  /** Lo que se puede hacer para que deje de estar vacío, por ejemplo "Añadir gasto". */
  action?: ReactNode
}

/**
 * La consulta salió bien y no hay nada que mostrar. No es un error: el texto explica por qué
 * está vacío y, si se puede, ofrece cómo llenarlo.
 */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className={styles.state}>
      <span className={`${styles.icon} ${styles.emptyIcon}`} aria-hidden="true">
        ∅
      </span>
      <p className={styles.title}>{title}</p>
      {description && <p className={styles.description}>{description}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </div>
  )
}

interface ErrorStateProps {
  /**
   * Qué pasó y qué hacer, en español y sin detalle técnico (RNF-17). Sin mensaje propio, el
   * genérico invita a reintentar.
   */
  message?: string
  onRetry?: () => void
}

export function ErrorState({
  message = 'No pudimos cargar esta información. Revisá tu conexión e intentá de nuevo.',
  onRetry,
}: ErrorStateProps) {
  return (
    <div className={styles.state} role="alert">
      <span className={`${styles.icon} ${styles.errorIcon}`} aria-hidden="true">
        !
      </span>
      <p className={styles.description}>{message}</p>
      {onRetry && (
        <div className={styles.action}>
          <Button variant="secondary" onClick={onRetry}>
            Reintentar
          </Button>
        </div>
      )}
    </div>
  )
}