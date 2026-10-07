import { Outlet } from 'react-router'
import styles from './AuthLayout.module.css'

/**
 * Marco de las pantallas sin sesión: registro, inicio de sesión y confirmación de correo (RF-33).
 * No lleva la navegación de la app, porque todavía no hay a dónde ir.
 */
export function AuthLayout() {
  return (
    <div className={styles.page}>
      <span className={styles.brand}>Gastos</span>
      <main className={styles.card}>
        <Outlet />
      </main>
    </div>
  )
}