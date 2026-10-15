import type { ReactNode } from 'react'
import styles from './PageHeader.module.css'

interface PageHeaderProps {
  title: string
  /** Texto chico sobre el título, por ejemplo el período: "Octubre 2026". */
  eyebrow?: string
  /** Acciones a la derecha del título, como filtros o un botón. */
  actions?: ReactNode
}

/** Encabezado de una pantalla. Cada pantalla tiene uno, con el único `h1` de la página. */
export function PageHeader({ title, eyebrow, actions }: PageHeaderProps) {
  return (
    <header className={styles.header}>
      <div>
        {eyebrow && <span className={styles.eyebrow}>{eyebrow}</span>}
        <h1 className={styles.title}>{title}</h1>
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </header>
  )
}