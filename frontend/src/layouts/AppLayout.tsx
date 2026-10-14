import { useRef, useState, type KeyboardEvent } from 'react'
import { NavLink, Outlet, useLocation, useMatch } from 'react-router'
import { Brand } from '../components/layout/Brand.tsx'
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
  const { pathname } = useLocation()
  // El menú guarda la ruta en la que se abrió y solo se muestra en esa. Así cualquier navegación
  // lo cierra, venga de donde venga: un link del menú, el botón fijo de alta o "atrás".
  const [openAt, setOpenAt] = useState<string | null>(null)
  const menuOpen = openAt === pathname
  const menuButton = useRef<HTMLButtonElement>(null)
  // En el alta misma, el botón de alta sobra.
  const addingExpense = useMatch(NEW_EXPENSE_PATH) !== null

  const closeMenu = () => setOpenAt(null)

  // Escape cierra el menú y devuelve el foco al botón, para que quien navega con teclado no tenga
  // que volver a buscarlo.
  const closeOnEscape = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && menuOpen) {
      closeMenu()
      menuButton.current?.focus()
    }
  }

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
        <Brand to="/" className={styles.sidebarBrand} />
        {navigation()}
        <div className={styles.spacer} />
        {!addingExpense && addExpense('normal')}
      </aside>

      <header className={styles.topbar} onKeyDown={closeOnEscape}>
        <div className={styles.topbarRow}>
          <Brand to="/" />
          <button
            ref={menuButton}
            type="button"
            className={styles.menuButton}
            aria-expanded={menuOpen}
            aria-controls="mobile-navigation"
            onClick={() => setOpenAt(menuOpen ? null : pathname)}
          >
            <span className={styles.bar} aria-hidden="true" />
            <span className={styles.bar} aria-hidden="true" />
            <span className={styles.bar} aria-hidden="true" />
            <span className="visually-hidden">{menuOpen ? 'Cerrar menú' : 'Abrir menú'}</span>
          </button>
        </div>
        {/* Siempre en la página, para que aria-controls apunte a algo; hidden lo oculta. Un link
            a la ruta actual no cambia la ruta, así que también cierra el menú al tocarlo. */}
        <div id="mobile-navigation" className={styles.mobileNav} hidden={!menuOpen}>
          {navigation(closeMenu)}
        </div>
      </header>

      <main className={styles.content}>
        <Outlet />
      </main>

      {!addingExpense && <div className={styles.actionbar}>{addExpense('large')}</div>}
    </div>
  )
}