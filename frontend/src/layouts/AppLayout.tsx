import { useRef, useState, type KeyboardEvent } from 'react'
import { NavLink, Outlet, useLocation, useMatch } from 'react-router'
import { Brand } from '../components/layout/Brand.tsx'
import { ButtonLink } from '../components/ui/Button.tsx'
import { clearSession } from '../session.ts'
import styles from './AppLayout.module.css'
import { NEW_EXPENSE_PATH, SECTIONS } from './navigation.ts'
import { SidebarSlotContext } from './sidebarSlotContext.ts'

/**
 * Shell de las pantallas autenticadas.
 *
 * En móvil la navegación vive en un menú desplegable y la acción de alta queda fija abajo, a un
 * toque desde cualquier pantalla (RNF-14). En escritorio las dos van en la barra superior.
 *
 * La barra lateral de escritorio es de la pantalla, no de la navegación: solo aparece si la pantalla
 * pone algo en ella, como el resumen del tablero (ver SidebarSlot). Las demás usan todo el ancho.
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
  // Un estado y no un ref: la pantalla tiene que volver a dibujarse cuando el lugar ya existe.
  const [sidebarSlot, setSidebarSlot] = useState<HTMLElement | null>(null)

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

  // Cerrar sesión descarta el token, y RequireSession manda al login.
  const logout = (
    <button type="button" className={styles.logout} onClick={clearSession}>
      Cerrar sesión
    </button>
  )

  // En la barra superior de escritorio, del tamaño de un botón; en la barra fija de móvil, de todo el ancho.
  const addExpense = (size: 'normal' | 'large') => (
    <ButtonLink to={NEW_EXPENSE_PATH} size={size} block={size === 'large'}>
      <span aria-hidden="true">+</span> Añadir gasto
    </ButtonLink>
  )

  return (
    <div className={styles.shell}>
      <header className={styles.topbar} onKeyDown={closeOnEscape}>
        <div className={styles.topbarRow}>
          <Brand to="/" />
          {/* En escritorio, las secciones y las acciones van en la misma barra. */}
          <div className={styles.desktopNav}>
            {navigation()}
            <div className={styles.desktopActions}>
              {logout}
              {!addingExpense && addExpense('normal')}
            </div>
          </div>
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
          {logout}
        </div>
      </header>

      <div className={styles.body}>
        {/* Lo que la pantalla actual pone en la barra lateral, como el resumen del tablero. Vacía,
            la barra no se muestra. */}
        <aside ref={setSidebarSlot} className={styles.sidebar} />

        <main className={styles.content}>
          <SidebarSlotContext value={sidebarSlot}>
            <Outlet />
          </SidebarSlotContext>
        </main>
      </div>

      {!addingExpense && <div className={styles.actionbar}>{addExpense('large')}</div>}
    </div>
  )
}