import { Link } from 'react-router'
import styles from './Brand.module.css'

interface BrandProps {
  /** Si se indica, la marca es un enlace, por ejemplo al tablero. */
  to?: string
  className?: string
}

/** El nombre de la app, con el mismo estilo en todas las pantallas. */
export function Brand({ to, className }: BrandProps) {
  const classes = [styles.brand, className].filter(Boolean).join(' ')
  return to ? (
    <Link to={to} className={classes}>
      Gastos
    </Link>
  ) : (
    <span className={classes}>Gastos</span>
  )
}