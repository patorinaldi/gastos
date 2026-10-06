# Cliente web

React 19 + TypeScript + Vite, build estático apto para CDN. La instalación y ejecución local
están en el [README principal](../README.md#frontend).

| Ruta | Contenido |
|---|---|
| `src/router.tsx` | Rutas de la aplicación |
| `src/layouts/` | Shell de la app: `AppLayout` para las pantallas autenticadas y `AuthLayout` para registro e inicio de sesión |
| `src/pages/` | Una pantalla por ruta |
| `src/components/ui/` | Primitivos: `Button`, `ButtonLink`, `TextField`, `Notice` |
| `src/components/states/` | Estados de carga, vacío y error de toda pantalla que consulta el servidor (RNF-16) |
| `src/components/layout/` | Piezas de estructura de una pantalla, como `PageHeader` |
| `src/styles/` | `tokens.css` con las variables del sistema de diseño y `base.css` con el reset y el foco |
| `src/format.ts` | Formato de importes y fechas para Argentina |
| `src/types/api.ts` | Contratos de la API, espejo de los DTOs del backend |
| `src/config.ts` | Configuración leída del entorno (`VITE_API_URL`) |

## Estilos

- **Cada componente tiene su `.module.css`** al lado del `.tsx`. Vite los soporta sin
  configuración, y las clases quedan acotadas al componente: dos componentes pueden tener una
  clase `.title` sin pisarse.
- **Ningún valor suelto.** Colores, espacios, radios y tamaños de letra salen de las variables de
  `src/styles/tokens.css` (`var(--color-ink)`, `var(--space-4)`…). Si falta un valor, se agrega
  ahí.
- **La acción principal va en tinta (`Button` primario), no en ámbar.** El ámbar queda para lo que
  pide atención: avisos, la bandeja, el foco.
- **Móvil primero.** Los estilos base son los de 360 px, y lo de escritorio va en
  `@media (min-width: 768px)`, el umbral que fija M7.
- **Importes y fechas siempre por `src/format.ts`.** Los importes llegan como texto y se formatean
  sin pasar por `Number`. Las fechas sin hora no pasan por `Date`, que las correría un día en
  Argentina.

## Catálogo del sistema de diseño

Con `npm run dev`, [`/dev/ui`](http://localhost:5173/dev/ui) muestra cada componente en sus
variantes: colores, botones, campos, avisos, los tres estados y el formato de importes y fechas.
La ruta solo existe en desarrollo; el build de producción no la incluye.