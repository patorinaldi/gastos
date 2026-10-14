import { useEffect, useState, type ReactNode } from 'react'
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

/**
 * Los lectores de pantalla anuncian una región role="status" cuando cambia su contenido, no
 * cuando aparece ya con el texto. Por eso la región nace vacía y recibe el texto después, con un
 * setTimeout: un setState directo en el efecto podría agruparse con el primer render, y la región
 * nunca llegaría a existir vacía. El texto visible queda oculto para ellos, para que no se lea dos
 * veces.
 */
export function LoadingState({ label = 'Cargando…' }: LoadingStateProps) {
  const [announcement, setAnnouncement] = useState('')
  useEffect(() => {
    const timer = setTimeout(() => setAnnouncement(label))
    return () => clearTimeout(timer)
  }, [label])

  return (
    <div className={styles.state}>
      <span className={styles.spinner} aria-hidden="true" />
      <p className={styles.label} aria-hidden="true">
        {label}
      </p>
      <p role="status" className="visually-hidden">
        {announcement}
      </p>
    </div>
  )
}

interface EmptyStateProps {
  title: string
  description?: string
  /** Lo que se puede hacer para que deje de estar vacío, por ejemplo "Añadir gasto". */
  action?: ReactNode
  /**
   * Elemento del título. Dentro de una pantalla con PageHeader es un párrafo; en una pantalla que
   * es solo este estado, como la de dirección inexistente, tiene que ser su h1.
   */
  titleAs?: 'p' | 'h1' | 'h2'
}

/**
 * La consulta salió bien y no hay nada que mostrar. No es un error: el texto explica por qué
 * está vacío y, si se puede, ofrece cómo llenarlo.
 */
export function EmptyState({ title, description, action, titleAs: Title = 'p' }: EmptyStateProps) {
  return (
    <div className={styles.state}>
      <span className={`${styles.icon} ${styles.emptyIcon}`} aria-hidden="true">
        ∅
      </span>
      <Title className={styles.title}>{title}</Title>
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