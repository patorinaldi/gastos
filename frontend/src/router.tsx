import { createBrowserRouter, type RouteObject } from 'react-router'
import { AppLayout } from './layouts/AppLayout.tsx'
import { AuthLayout } from './layouts/AuthLayout.tsx'
import { DashboardPage } from './pages/DashboardPage.tsx'
import { ExpensesPage } from './pages/ExpensesPage.tsx'
import { HouseholdPage } from './pages/HouseholdPage.tsx'
import { InboxPage } from './pages/InboxPage.tsx'
import { LoginPage } from './pages/LoginPage.tsx'
import { NewExpensePage } from './pages/NewExpensePage.tsx'
import { NotFoundPage } from './pages/NotFoundPage.tsx'
import { RegisterPage } from './pages/RegisterPage.tsx'

// El catálogo del sistema de diseño solo existe en desarrollo. Se carga con un import dinámico
// dentro de la rama de desarrollo: en el build de producción la rama desaparece, y con ella la
// página y su CSS. Un import estático arriba dejaría el CSS en el bundle igual.
const devRoutes: RouteObject[] = import.meta.env.DEV
  ? [
      {
        path: 'dev/ui',
        lazy: async () => ({ Component: (await import('./pages/dev/UiCatalogPage.tsx')).UiCatalogPage }),
      },
    ]
  : []

export const router = createBrowserRouter([
  {
    // Las pantallas sin sesión no llevan el shell de la app.
    element: <AuthLayout />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'expenses', element: <ExpensesPage /> },
      { path: 'expenses/new', element: <NewExpensePage /> },
      { path: 'inbox', element: <InboxPage /> },
      { path: 'household', element: <HouseholdPage /> },
      ...devRoutes,
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])