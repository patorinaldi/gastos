import type { ReactNode } from 'react'
import styles from './Notice.module.css'

interface NoticeProps {
  /** `accent` pide atención sin alarmar, como la bandeja; `danger` avisa de un problema. */
  tone?: 'accent' | 'danger'
  children: ReactNode
  /** Acción a la derecha del mensaje, por ejemplo un botón "Revisar". */
  action?: ReactNode
}

/** Aviso en línea: "3 gastos sin categorizar · Revisar". */
export function Notice({ tone = 'accent', children, action }: NoticeProps) {
  return (
    <div className={`${styles.notice} ${styles[tone]}`} role={tone === 'danger' ? 'alert' : undefined}>
      <span className={styles.message}>{children}</span>
      {action}
    </div>
  )
}