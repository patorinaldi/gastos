import { useState } from 'react'
import { Link, NavLink, Outlet, useMatch } from 'react-router'
import { ButtonLink } from '../components/ui/Button.tsx'
import styles from './AppLayout.module.css'
import { NEW_EXPENSE_PATH, SECTIONS } from './navigation.ts'

/**
 * Shell de las pantallas autenticadas.
 *
 * En móvil la navegación vive en un menú desplegable y la acción de alta queda fija abajo, a un
 * toque desde cualquier pantalla (RNF-14). En escritorio las dos van en la barra lateral.
 */
export function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false)
  // En el alta misma, el botón de alta sobra.
  const addingExpense = useMatch(NEW_EXPENSE_PATH) !== null

  const navigation = (onNavigate?: () => void) => (
    <nav className={styles.nav} aria-label="Secciones">
      {SECTIONS.map((section) => (
        <NavLink key={section.to} to={section.to} end={section.end} className={styles.navLink} onClick={onNavigate}>
          {section.label}
        </NavLink>
      ))}
    </nav>
  )

  const addExpense = (size: 'normal' | 'large') => (
    <ButtonLink to={NEW_EXPENSE_PATH} size={size} block>
      <span aria-hidden="true">+</span> Añadir gasto
    </ButtonLink>
  )

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <Link to="/" className={styles.brand}>
          Gastos
        </Link>
        {navigation()}
        <div className={styles.spacer} />
        {!addingExpense && addExpense('normal')}
      </aside>

      <header className={styles.topbar}>
        <div className={styles.topbarRow}>
          <Link to="/" className={styles.brand} onClick={() => setMenuOpen(false)}>
            Gastos
          </Link>
          <button
            type="button"
            className={styles.menuButton}
            aria-expanded={menuOpen}
            aria-controls="mobile-navigation"
            onClick={() => setMenuOpen((open) => !open)}
          >
            <span aria-hidden="true" />
            <span aria-hidden="true" />
            <span aria-hidden="true" />
            <span className="visually-hidden">{menuOpen ? 'Cerrar menú' : 'Abrir menú'}</span>
          </button>
        </div>
        {menuOpen && (
          <div id="mobile-navigation" className={styles.mobileNav}>
            {navigation(() => setMenuOpen(false))}
          </div>
        )}
      </header>

      <main className={styles.content}>
        <Outlet />
      </main>

      {!addingExpense && <div className={styles.actionbar}>{addExpense('large')}</div>}
    </div>
  )
}