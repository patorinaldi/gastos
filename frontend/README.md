# Cliente web

React 19 + TypeScript + Vite, build estático apto para CDN. La instalación y ejecución local
están en el [README principal](../README.md#frontend).

| Ruta | Contenido |
|---|---|
| `src/router.tsx` | Rutas de la aplicación |
| `src/layouts/` | Shell de la app: `AppLayout` para las pantallas autenticadas, `AuthLayout` para registro, inicio de sesión y confirmación de correo, y `RequireSession`, que manda al login sin sesión |
| `src/pages/` | Una pantalla por ruta |
| `src/components/ui/` | Primitivos: `Button`, `ButtonLink`, `TextField`, `SelectField`, `Notice` |
| `src/components/states/` | Estados de carga, vacío y error de toda pantalla que consulta el servidor (RNF-16) |
| `src/components/layout/` | Piezas de estructura de una pantalla: `PageHeader` y `Brand`, la marca que comparten los dos layouts |
| `src/styles/` | `tokens.css` con las variables del sistema de diseño, `base.css` con el reset y el foco, y `typography.module.css` con estilos de texto compartidos |
| `src/components/expenses/` | El formulario de un gasto, compartido por el alta y la edición, y el selector de período del listado |
| `src/format.ts` | Formato de importes y fechas para Argentina, con sus pruebas en `format.test.ts` |
| `src/amount.ts` | El importe escrito en el formulario (`1.234,56`) llevado al formato de la API, con las mismas reglas que `AmountParser` del backend |
| `src/expenseForm.ts`, `src/expenseFilters.ts` | La lógica del formulario de un gasto (validar y armar la petición de alta o edición) y de los filtros del listado de movimientos en la dirección |
| `src/types/api.ts` | Contratos de la API, espejo de los DTOs del backend |
| `src/api/` | `client.ts`, por donde pasa toda llamada a la API: token, errores `problem+json` y tiempo de espera; y un archivo por módulo de la API con sus llamadas |
| `src/session.ts` | La sesión: el token del login, dónde se guarda y cuándo se descarta |
| `src/validation.ts` | Validaciones de los formularios de cuenta y del comercio de un gasto, espejo de las del backend |
| `src/config.ts` | Configuración leída del entorno (`VITE_API_URL`) |

## Estilos

- **Cada componente tiene su `.module.css`** al lado del `.tsx`. Vite los soporta sin
  configuración, y las clases quedan acotadas al componente: dos componentes pueden tener una
  clase `.title` sin pisarse.
- **Ningún valor suelto.** Colores, espacios, radios y tamaños de letra salen de las variables de
  `src/styles/tokens.css` (`var(--color-ink)`, `var(--space-4)`…). Si falta un valor, se agrega
  ahí. Las únicas excepciones son el umbral de las `@media`, porque CSS no admite variables ahí, y
  el 1 px de `.visually-hidden`, que es parte de la técnica y no del diseño.
- **Los estilos de texto que se repiten se comparten con `composes`.** La etiqueta monoespaciada en
  mayúsculas de la marca y los encabezados chicos vive en `src/styles/typography.module.css`, y
  cada módulo la toma con `composes: monoLabel from '…/typography.module.css'`.
- **La acción principal va en tinta (`Button` primario), no en ámbar.** El ámbar queda para lo que
  pide atención: avisos, la bandeja, el foco.
- **Móvil primero.** Los estilos base son los de 360 px, y lo de escritorio va en
  `@media (min-width: 768px)`, el umbral que fija M7.
- **Importes y fechas siempre por `src/format.ts`.** Los importes llegan como texto y se formatean
  sin pasar por `Number`. Las fechas sin hora no pasan por `Date`, que las correría un día en
  Argentina.

## Sesión

El token del login se guarda en `localStorage`, para que la sesión dure los 7 días del token
(RNF-29) aunque el navegador del teléfono descarte la pestaña. Las rutas de la app pasan por
`RequireSession`: sin sesión, o con una vencida, mandan al login, y después del login se vuelve a la
pantalla pedida. Un 401 de la API descarta la sesión, y la app vuelve al login sola. Cerrar sesión
descarta el token de este dispositivo; el servidor no guarda sesiones. El hogar no se lee del token,
que queda desactualizado si el usuario se cambia de hogar: se pide a `GET /api/auth/me`.

Las llamadas pasan siempre por `src/api/client.ts`, que convierte los errores en `ApiError`, y sus
mensajes por `errorMessage`: usa el `detail` que el servidor escribe para el usuario, salvo en los
400, la falta de conexión y los errores del servidor, que tienen mensaje propio (RNF-17).

El enlace del correo de alta es `/verify-email#token=...`. El token va en el fragmento, como el del
restablecimiento de contraseña, para que no llegue a ningún registro de acceso, y la pantalla lo
borra de la dirección al leerlo. Si el enlace venció o ya se usó, la misma pantalla ofrece pedir uno
nuevo (`components/auth/ResendVerification.tsx`), igual que el alta y el login cuando responde que el
correo no está verificado.

La recuperación de contraseña tiene dos pantallas. `/forgot-password`, a la que se llega desde el
login con el correo ya escrito, pide el enlace y responde igual exista o no la cuenta. El enlace del
correo es `/reset-password#token=...`: la pantalla lee el token y lo borra de la dirección, igual que
la de confirmación, pero lo canjea recién al enviar la contraseña nueva. Si el enlace venció o ya se
usó, ofrece pedir otro. Al cambiarla, el servidor cierra todas las sesiones de la cuenta, y la
pantalla descarta también la que hubiera en ese navegador.

## Gastos

El alta (`/expenses/new`) pide solo importe y comercio; la fecha, el medio de pago y la categoría
ya vienen con el valor que pondría el servidor, y sin categoría elegida la deciden las reglas del
hogar. El importe se escribe en formato local y el campo dice cómo se va a guardar ("Se guarda como
$ 1.234,00"): `src/amount.ts` lo normaliza con las mismas reglas que la captura rápida, y ante la
duda lo rechaza en lugar de adivinar. Al guardar, la pantalla dice si el gasto quedó clasificado o
en la bandeja, y deja el formulario limpio para el siguiente.

El listado (`/expenses`) lleva sus filtros en la dirección, con los mismos nombres que la API y que
el tablero (`?month=2026-09&categoryId=...&page=1`), así "Ver todos" en el tablero abre los mismos
movimientos. Las personas y categorías que ofrece como filtro salen del resumen del período, así
aparecen también quienes ya no están en el hogar pero tienen gastos.

Cada fila de movimientos, en el tablero y en el listado, abre la edición (`/expenses/:id`). La
edición manda solo los campos que cambiaron y vuelve a la pantalla que la abrió, con sus filtros.
La baja pide confirmación.

## Pruebas

`npm test` corre las pruebas con Vitest, también en CI. Corren con la zona horaria de Buenos
Aires (`vite.config.ts`), así que un cambio que haga pasar una fecha por `Date` y la corra un día
falla acá aunque la máquina esté en otra zona.

## Catálogo del sistema de diseño

Con `npm run dev`, [`/dev/ui`](http://localhost:5173/dev/ui) muestra cada componente en sus
variantes: colores, botones, campos, avisos, los tres estados y el formato de importes y fechas.
La ruta solo existe en desarrollo; el build de producción no la incluye.