import { Outlet } from 'react-router'
import { Brand } from '../components/layout/Brand.tsx'
import styles from './AuthLayout.module.css'

/**
 * Marco de las pantallas sin la navegación de la app: registro, inicio de sesión y confirmación de
 * correo (RF-33), y la página de dirección inexistente. Lleva la marca, que vuelve al inicio.
 */
export function AuthLayout() {
  return (
    <div className={styles.page}>
      <Brand to="/" />
      <main className={styles.card}>
        <Outlet />
      </main>
    </div>
  )
}