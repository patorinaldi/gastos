# Definición funcional de los módulos

Proyecto Gastos. Trabajo Final Integrador, TUP UTN.

Detalle de los ocho módulos declarados en la propuesta: qué resuelve cada uno, qué expone, qué
reglas hace cumplir y de qué depende. Los identificadores `RF-xx`, `RNF-xx` y `RN-xx` remiten a
[requerimientos.md](requerimientos.md) y [reglas-de-negocio.md](reglas-de-negocio.md). El esquema
sobre el que se apoyan todos está en [modelo-de-datos.md](modelo-de-datos.md).

## Convenciones de la API

- Prefijo común `/api`. Todos los recursos se sirven bajo él.
- Autenticación por token de sesión en `Authorization: Bearer <token>`, salvo el registro, el
  inicio de sesión, la verificación de correo, la recuperación de contraseña y el punto de
  verificación de estado.
- El hogar nunca viaja en la petición. El servidor lo resuelve en cada petición desde el hogar
  actual del usuario autenticado (`users.household_id`), no desde el token, y lo propaga al
  contexto de la base (RN-18). Un cliente no puede pedir datos de otro hogar porque no tiene forma
  de nombrarlo.
- Los errores se devuelven con `application/problem+json` (RFC 9457), resueltos de forma
  centralizada (RF-40).
- Los importes se serializan como cadena decimal con dos decimales, para no perder precisión en el
  tránsito por JSON (RD-01).

## Estado al 23/09/2026

| Módulo | Responsable | Estado |
|---|---|---|
| M1 Identidad y acceso | Juan (usuario), Pato (núcleo de seguridad) | Alta de cuenta, verificación de correo, inicio de sesión, sesión, emisión, revocación y autenticación de tokens de cliente máquina y restablecimiento de contraseña implementados; en el cliente web, las pantallas de registro, inicio de sesión, confirmación de correo y restablecimiento de contraseña implementadas, y las de tokens de cliente máquina pendientes. Con `EMAIL_SENDER=console`, el alta y los enlaces por correo responden 503 |
| M2 Hogares | Elian | Backend y pantalla del hogar implementados: integrantes, invitación, canje con confirmación y nombre |
| M3 Gastos | Elian | Alta con categoría automática, detalle, edición, baja lógica y listado con filtros implementados; en el cliente web, las pantallas de alta, edición y baja, y el listado de movimientos con filtros, implementadas |
| M4 Categorización | Elian | Catálogo, motor de reglas en el alta, bandeja y reglas con reclasificación implementados; pantallas pendientes |
| M5 Análisis | Juan | Resumen, serie mensual y ranking de comercios implementados, con los filtros del listado |
| M6 API de integración | Juan | Captura rápida implementada, con normalización de importes y autenticación por token de cliente máquina |
| M7 Interfaz de usuario | Elian, Pato | Sistema de diseño, shell y pantallas de registro, inicio de sesión, confirmación de correo y restablecimiento de contraseña implementados; tablero con período, total, evolución mensual, distribución, movimientos recientes y filtrado desde los gráficos; pantalla del hogar; alta, edición y baja de gastos y listado de movimientos con filtros; el resto de las pantallas pendiente |
| M8 Plataforma y calidad | Pato | Migraciones, aislamiento y entidades implementados |

---

## M1. Identidad y acceso

**Responsabilidad.** Establecer quién es el usuario y a qué hogar pertenece. Es el módulo que
alimenta el contexto de aislamiento. Sin identidad resuelta no hay hogar, y sin hogar no hay datos
visibles.

Requisitos: RF-01 a RF-07. Reglas: RN-01, RN-02, RN-03, RN-11, RN-18.

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `POST` | `/api/auth/register` | Registra un usuario, crea su hogar y siembra el catálogo inicial. Envía el correo de verificación. | No |
| `POST` | `/api/auth/verify` | Confirma el correo a partir del token recibido. | No |
| `POST` | `/api/auth/verify/resend` | Reenvía el enlace de verificación, por ejemplo si venció. | No |
| `POST` | `/api/auth/login` | Valida credenciales y emite el token de sesión. | No |
| `POST` | `/api/auth/password/forgot` | Emite y envía un token de restablecimiento. | No |
| `POST` | `/api/auth/password/reset` | Cambia la contraseña con un token válido. | No |
| `GET` | `/api/auth/me` | Devuelve el usuario autenticado y su hogar. | Sesión |
| `POST` | `/api/auth/machine-tokens` | Emite un token de larga duración para un cliente automatizado. | Sesión |
| `GET` | `/api/auth/machine-tokens` | Lista los tokens propios: los vigentes y los revocados en los últimos 30 días. | Sesión |
| `DELETE` | `/api/auth/machine-tokens/{id}` | Revoca un token de cliente automatizado. | Sesión |

Contratos principales:

```
POST /api/auth/register
  { "name": "Ana", "email": "ana@ejemplo.com", "password": "..." }
  → 201 { "userId": "...", "householdId": "...", "emailVerified": false }
  → 409 si el correo ya está registrado (RN-01)
  → 429 superado el límite de altas por dirección (RNF-31)
  → 503 el envío de correos configurado no puede llevar el enlace de verificación (RNF-09)

POST /api/auth/verify
  { "token": "..." }
  → 204 correo verificado; la cuenta ya puede iniciar sesión (RN-02)
  → 410 enlace inexistente, vencido o ya usado (RN-03)

POST /api/auth/verify/resend
  { "email": "ana@ejemplo.com" }
  → 202 sin cuerpo, exista o no la cuenta y esté o no verificada
  → 429 superado el límite de pedidos (RNF-32)
  → 503 el envío de correos configurado no puede llevar el enlace (RNF-09)

POST /api/auth/login
  { "email": "ana@ejemplo.com", "password": "..." }
  → 200 { "token": "...", "expiresAt": "..." }
  → 401 credenciales inválidas
  → 403 correo sin verificar (RN-02)
  → 429 superado el límite de intentos (RNF-06)

POST /api/auth/password/forgot
  { "email": "ana@ejemplo.com" }
  → 202 sin cuerpo, exista o no la cuenta (CU-08, 1a)
  → 429 superado el límite de pedidos (RNF-32)
  → 503 el envío de correos configurado no puede llevar el enlace (RNF-09)

POST /api/auth/password/reset
  { "token": "...", "password": "..." }
  → 204 contraseña cambiada; se cierran todas las sesiones del usuario (RNF-29), se revocan sus
        tokens de cliente máquina y el correo queda verificado
  → 400 contraseña de menos de 8 caracteres o de más de 72 bytes; el enlace no se gasta
  → 410 enlace inexistente, vencido o ya usado (RN-03)

POST /api/auth/machine-tokens
  { "name": "Atajo del teléfono", "password": "..." }
  → 201 { "id": "...", "name": "...", "token": "gst_...", "createdAt": "..." }
        el token en claro va solo en esta respuesta
  → 403 la contraseña no es la de la cuenta
  → 409 ya tiene el máximo de tokens vigentes (RNF-33)
  → 429 superado el límite de pedidos de emisión (RNF-33)

GET /api/auth/machine-tokens
  → 200 [ { "id": "...", "name": "...", "createdAt": "...", "lastUsedAt": "...", "revokedAt": null } ]

DELETE /api/auth/machine-tokens/{id}
  → 204 revocado, o ya lo estaba
  → 404 no existe, es de otra persona o de un hogar anterior
```

**Decisiones de diseño.** El token de sesión identifica al usuario, y el hogar de cada petición se
lee de `users.household_id`, no de un atributo del token. Cuesta una consulta por petición, pero es
lo que exige RN-18: el servidor acepta un token firmado hasta que vence, así que si el hogar viajara
en él, quien se cambió de hogar seguiría leyendo y escribiendo en el anterior con un token emitido
antes del cambio. El token puede llevar el hogar como dato para el cliente (RF-05), pero el servidor
no lo usa para decidir a qué hogar accede la petición. Ese dato queda desactualizado si el usuario
se cambia de hogar: el cliente tiene que tomar el hogar de `GET /api/auth/me` y no leerlo del token.

El token vence a los 7 días y lleva además la versión de sesión del usuario (`users.session_version`,
migración V6). Esa misma consulta por petición la compara con la vigente, y si no coincide rechaza
el token: incrementar la versión invalida de una vez todas las sesiones del usuario, en todos sus
dispositivos. Es lo que hace el restablecimiento de contraseña (RNF-29). Cerrar sesión en un solo
dispositivo es descartar el token del lado del cliente.

El alta de cuenta tiene su propio límite por dirección (RNF-31): 20 por hora. A diferencia del de
login, **cuentan todos los intentos**, también los que terminan bien, porque lo que se limita es el
alta en sí y no hay una contraseña que acertar. Hace falta por dos motivos: cada alta que llega a
correr bcrypt cuesta del orden de 100 ms de procesador, y el 409 de un correo ya registrado es parte
del contrato y no se puede callar, así que el alta permite averiguar qué correos tienen cuenta. El
límite no lo impide, lo encarece. No hay contador por correo: repetir un correo solo devuelve 409, y
bloquearlo le permitiría a un tercero dejar sin alta a su dueño. El orden dentro del alta va de lo
barato a lo caro: el límite, la comprobación de que el correo esté libre, y recién al final bcrypt.

El límite de intentos de login (RNF-06) cuenta en memoria, en tres contadores de ventana fija:
el correo desde cada dirección IP, la dirección con cualquier correo y el correo desde cualquier
dirección. Contar solo por correo le permitiría a cualquiera bloquear la cuenta de otro probando
contraseñas, y contar solo por dirección no frenaría un ataque sobre un correo repartido entre
varias. Por eso el contador por correo solo tiene el tope más alto (100, el máximo de intentos
consecutivos sobre una cuenta que admite NIST SP 800-63B): acota el ataque repartido, y bloquear
al dueño exige un ataque deliberado. Las IPv6 cuentan por su prefijo /64. En memoria alcanza con la
única instancia del despliegue; con más de una, cada instancia contaría por su lado.

La dirección sale de `X-Forwarded-For` (`server.forward-headers-strategy: native`), porque en
Azure toda petición llega a través de un proxy. Tomcat solo confía en el header si la conexión
viene del proxy de App Service (`169.254.x.x`, `server.tomcat.remoteip.internal-proxies`), y toma
la última dirección, la que agrega Azure, así que el cliente no puede elegir la suya. Azure la
manda con el puerto, que `ClientAddress` quita.

El restablecimiento de contraseña responde `202` antes de buscar la cuenta: buscarla, emitir el
token y enviar el correo corren en segundo plano. Si corrieran dentro de la petición, el tiempo de
respuesta, que solo incluye el envío cuando la cuenta existe, o un error del proveedor de correo,
revelarían qué correos están registrados. El enlace lleva el token en el fragmento
(`/reset-password#token=...`), que el navegador no manda al servidor ni en el header `Referer`, y se
arma con `WEB_URL`. El canje bloquea primero al usuario y después marca el token como usado con un update
condicional: de dos canjes simultáneos, del mismo enlace o de dos enlaces de la misma persona, solo
uno cambia la contraseña, y el otro recibe `410`. Bloquear los tokens antes que al usuario dejaba a
dos enlaces distintos esperándose entre sí. En la misma transacción marca como usados los demás
enlaces que la persona haya pedido, incrementa su versión de sesión, revoca sus tokens de cliente
máquina, que no vencen y pudieron emitirse con la contraseña comprometida, y da el correo por
verificado, porque el enlace llegó a esa casilla: sin eso, una cuenta sin verificar quedaría con la
contraseña nueva y el inicio de sesión rechazado igual (RN-02).

La verificación de correo sigue el mismo patrón. El alta emite el token de verificación dentro de
su transacción, así que la cuenta y su token se crean juntos o no se crea ninguno, y manda el correo
después de confirmarla, en segundo plano: un proveedor lento o caído no demora ni hace fallar el
alta. Si el enlace no llega o vence, `POST /api/auth/verify/resend` manda otro, con la misma
respuesta exista o no la cuenta. Confirmar un enlace anula los demás enlaces de verificación
pendientes, con el mismo orden de locks que el restablecimiento. El enlace es
`/verify-email#token=...`, también sobre `WEB_URL`.

Los correos salen por SMTP (`EMAIL_SENDER=smtp`), que sirve con cualquier proveedor que lo ofrezca,
así que cambiar de proveedor es cambiar variables de entorno. El health de la aplicación no consulta
al servidor SMTP: una caída del proveedor no tira el despliegue, y un correo que no sale queda en el
log. El envío por consola escribe los correos en el log, y con ellos los enlaces de un solo uso. Por
eso no acepta correos con tokens salvo que se habilite `gastos.email.console-allow-secrets`, como
hacen el perfil dev y las pruebas. Un despliegue con `EMAIL_SENDER=console` responde `503` al alta,
al pedido de restablecimiento y al reenvío de la verificación: una cuenta que no puede recibir su
enlace no se podría verificar nunca. Los correos pendientes se envían antes de apagarse la
aplicación (`spring.task.execution.shutdown`). Los pedidos de enlaces, restablecimiento y reenvío de
la verificación, se limitan juntos (RNF-32) a 5 por correo y 20 por dirección por hora: cada pedido
puede mandar un correo a la misma casilla, y sin límite cualquiera podría llenarla. Se cuentan por el correo escrito, exista o no la cuenta,
así que el `429` tampoco revela nada. El tope por correo permite que alguien le impida a otra
persona pedir enlaces durante una hora; es el precio de proteger su casilla.

Los tokens de cliente máquina los emite y los revoca cada persona desde su sesión, y cada una ve y
revoca solo los suyos, aunque comparta hogar: un token es una credencial de quien lo emitió, y los
gastos que carga quedan a su nombre. Un token ajeno responde `404`, igual que uno inexistente. La
emisión bloquea primero la fila del usuario, como el canje de invitaciones, y toma el hogar de esa
fila: una emisión y un cambio de hogar simultáneos no se cruzan, y el token nunca queda emitido
para el hogar que la persona acaba de dejar. Cada persona tiene hasta 10 tokens vigentes (RNF-33):
cada uno es una forma más de cargar gastos a su nombre, y no vence.

Emitir pide la contraseña actual: sin ella, una sesión robada alcanzaría para convertirse en un
acceso que no vence. Una contraseña incorrecta responde `403` y no `401`, porque la sesión sigue
siendo válida. Los pedidos de emisión se limitan a 20 por persona por hora (RNF-33), salgan bien o
no: sin ese tope, emitir y revocar en un bucle nunca llega al de vigentes y deja una fila revocada
por vuelta, y cada pedido prueba una contraseña. Los revocados no se borran, así que el listado
muestra solo los de los últimos 30 días. Revocar alcanza a los mismos tokens que muestra el
listado, los del hogar actual: los de un hogar anterior ya los revocó el cambio de hogar (RN-18).

El registro y la creación del hogar son una
sola transacción (RN-11): el alta crea el hogar, fija su identificador en el contexto de la
transacción y recién entonces siembra el catálogo inicial de M4, que se inserta bajo las políticas
de aislamiento y por lo tanto exige un hogar activo.

Depende de M8 (esquema, propagación de contexto). El hogar que crea el alta es el que después
administra M2.

---

## M2. Hogares

**Responsabilidad.** Administrar la unidad de convivencia: quiénes la integran, cómo se suma
alguien nuevo y qué pasa con el hogar que esa persona deja.

Requisitos: RF-08 a RF-12. Reglas: RN-04, RN-05, RN-18.

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `GET` | `/api/household` | Datos del hogar del usuario autenticado. | Sesión |
| `PATCH` | `/api/household` | Renombra el hogar. | Sesión |
| `GET` | `/api/household/members` | Lista los integrantes. | Sesión |
| `POST` | `/api/household/invitations` | Genera un código de invitación. | Sesión |
| `POST` | `/api/household/invitations/preview` | Valida un código y describe las consecuencias del cambio de hogar, sin aplicarlo. | Sesión |
| `POST` | `/api/household/invitations/redeem` | Canjea un código y cambia al usuario de hogar. Exige confirmación explícita. | Sesión |

Contratos principales:

```
PATCH /api/household
  { "name": "Casa Rivoira" }
  → 200 { "id": "...", "name": "Casa Rivoira", "createdAt": "..." }
  → 400 nombre vacío, de más de 200 caracteres o con caracteres de control o invisibles

POST /api/household/invitations
  → 201 { "code": "GST-4K2P-9XZ", "expiresAt": "..." }
  → 409 el hogar ya tiene el máximo de invitaciones vigentes (RNF-30)

POST /api/household/invitations/preview
  { "code": "GST-4K2P-9XZ" }
  → 200 { "householdName": "Casa Rivoira", "currentHouseholdWillBeArchived": true,
          "machineTokensToRevoke": 2 }
  → 409 el código es del hogar que el usuario ya integra
  → 410 código inexistente, vencido, ya canjeado o de un hogar archivado (RN-05, RN-18)
  → 429 superado el límite de códigos inválidos (RNF-30)

POST /api/household/invitations/redeem
  { "code": "GST-4K2P-9XZ", "confirm": true }
  → 200 { "householdId": "...", "householdName": "Casa Rivoira",
          "previousHouseholdArchived": true }
  → 400 falta la confirmación
  → 409 el código es del hogar que el usuario ya integra
  → 410 código inexistente, vencido, ya canjeado o de un hogar archivado (RN-05, RN-18)
  → 429 superado el límite de códigos inválidos (RNF-30)
```

**El canje es un cambio de hogar.** Por RN-11 todo usuario ya tiene un hogar, así que canjear un
código implica dejar el actual (CU-03). La interfaz llama primero a `preview` para mostrar la
advertencia que exige RN-18 y después a `redeem` con `"confirm": true`. `redeem` no depende de que
se haya llamado a `preview`: repite todas las validaciones y rechaza el canje sin la confirmación.
Dentro de una sola transacción, `redeem` cambia el hogar del usuario, marca la invitación como
canjeada, revoca los tokens de cliente máquina del usuario en el hogar anterior y, si ese hogar
quedó sin integrantes, lo archiva. Los gastos que el usuario cargó quedan en el hogar anterior. Si
otra persona canjea el mismo código entre las dos llamadas, `redeem` responde `410` y no aplica
nada.

**Una sola respuesta para todos los códigos inválidos.** Inexistente, vencido, ya canjeado o de un
hogar archivado responden igual, con `410`, para que no se puedan descubrir códigos válidos por
tanteo. El `409` sí se distingue porque solo revela el hogar que el usuario ya integra. El código
viaja en el cuerpo y no en la ruta, para que no quede en los registros de acceso. La base guarda
solo su hash, así que el valor en claro existe únicamente en la respuesta que genera la invitación.

**Decisión de diseño.** El recurso es `/api/household`, en singular y sin identificador. El hogar
del usuario autenticado es el único al que puede acceder, y nombrarlo en la ruta sugeriría que
existe la posibilidad de pedir otro.

**El código.** Formato `GST-XXXX-XXX`: siete caracteres aleatorios sobre un alfabeto de 31 símbolos
sin los que se confunden al leerlos o dictarlos (`0`, `O`, `1`, `I`, `L`). Antes de calcular el
hash se normaliza —mayúsculas, sin espacios ni guiones—, así que se encuentra aunque se tipee en
minúsculas o se copie con espacios. Son unas 2,75 × 10¹⁰ combinaciones.

**Límites contra el tanteo (RNF-30).** Acertar un código vigente es entrar a un hogar ajeno y ver
todos sus gastos, así que dos límites acotan la probabilidad de lograrlo:

- **Códigos inválidos.** `preview` y `redeem` cuentan juntos, porque los dos sirven para probar un
  código: 10 por usuario y 50 por dirección IP cada 15 minutos. Es el mismo conteo en memoria, de
  ventana fija, que el límite de login, y responde igual: `429` con `Retry-After`. Solo cuenta un
  `410`. El `409` del código propio no revela nada, y un código válido no cuenta, pero tampoco
  reinicia los contadores: si los reiniciara, quien tenga un código válido de otra cuenta propia
  podría alternarlo con el tanteo. Durante el bloqueo, un código válido también se rechaza, para que
  el bloqueo no avise en qué intento se acertó. El límite envuelve al servicio desde el controlador,
  así que un intento rechazado no abre una transacción.
- **Invitaciones vigentes.** Un hogar tiene a lo sumo 10 sin canjear ni vencer, porque cada una es
  un código más para acertar. La undécima responde `409` hasta que alguna se use o venza. Para
  contarlas, `createInvitation` bloquea el hogar con `for no key update`: sin el bloqueo, dos
  pedidos simultáneos pasarían el tope los dos.

Los valores están en `gastos.invitation-limit`. Si hiciera falta más margen, alargar el código no
cambia ni el contrato ni el esquema.

**Canjes simultáneos.** `redeem` bloquea las filas que toca, siempre en el mismo orden para que dos
canjes no puedan esperarse mutuamente: primero el usuario, después la invitación y por último los
dos hogares, ordenados por id. Cada bloqueo resuelve una carrera:

| Bloqueo | Carrera que resuelve |
|---|---|
| Usuario | La misma persona canjea dos códigos a la vez. El segundo canje lee el hogar que dejó el primero, no el que tenía al empezar la petición. |
| Invitación | Dos personas canjean el mismo código. La segunda la encuentra ya canjeada y recibe `410`, sin cambios. |
| Hogares | Los dos últimos integrantes se van a la vez: sin el bloqueo, cada uno contaría al otro todavía adentro y el hogar quedaría activo y vacío. O alguien se suma a un hogar mientras su último integrante lo deja: sin el bloqueo, quedaría adentro de un hogar archivado. |

Usuario y hogares se bloquean con `for no key update` y no con `for update`, que chocaría con el
`for key share` que toma toda escritura que los referencia por clave foránea: mientras durara un
canje, nadie podría cargar un gasto en esos hogares. Lo prueba `InvitationConcurrencyTest`, que
fuerza cada carrera de forma determinística.

Por la misma razón, `households` y `users` se actualizan escribiendo solo las columnas que cambiaron
(`@DynamicUpdate`). Con la fila entera, entre dos transacciones concurrentes la última en confirmar
pisaba a la otra con lo que había leído: un renombre podía revivir un hogar archivado, e invalidar
las sesiones de un usuario podía devolverlo al hogar que dejó.

Depende de M1 (identidad; el hogar se crea en el alta; revocación de tokens de cliente máquina) y
M8 (aislamiento).

---

## M3. Gastos

**Responsabilidad.** El registro del hecho económico: alta, edición, baja y consulta del gasto
compartido del hogar.

Requisitos: RF-13 a RF-18. Reglas: RN-06, RN-07, RN-08, RN-12, RN-16.

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `POST` | `/api/expenses` | Registra un gasto. Resuelve la categoría con el motor de M4. | Sesión |
| `GET` | `/api/expenses` | Listado paginado con filtros combinables. | Sesión |
| `GET` | `/api/expenses/{id}` | Detalle de un gasto. | Sesión |
| `PATCH` | `/api/expenses/{id}` | Modifica campos de un gasto. | Sesión |
| `DELETE` | `/api/expenses/{id}` | Da de baja un gasto. La baja es lógica. | Sesión |

Parámetros del listado:

| Parámetro | Tipo | Descripción |
|---|---|---|
| `from`, `to` | fecha ISO (`2026-09-01`) | Período. Por defecto, el mes en curso (RD-07); en la bandeja, toda la historia. |
| `categoryId` | uuid | Filtra por categoría. `uncategorized` para la bandeja (RF-22). |
| `ownerId` | uuid | Filtra por integrante. |
| `paymentMethod` | enum | `Efectivo`, `Tarjeta` o `Transferencia`. |
| `page`, `size` | entero | Paginación. `page` desde 0 y hasta 10.000; `size` por defecto 20 y máximo 100 (RNF-12). |

Contratos principales:

```
POST /api/expenses
  { "amount": "1234.56", "merchant": "Carrefour", "paymentMethod": "Tarjeta" }
  → 201 { "id": "...", "amount": "1234.56", "merchant": "Carrefour", "expenseDate": "2026-09-21",
          "paymentMethod": "Tarjeta", "categoryId": "...", "categoryName": "Supermercado",
          "ownerId": "...", "ownerName": "Ana", "createdAt": "...", "updatedAt": "..." }
  → 400 importe no positivo o con más de dos decimales (RN-06), comercio vacío, solo de espacios
        (también duros o de ancho cero) o con caracteres de control o invisibles (RN-07), o una
        categoría inexistente, de otro hogar o dada de baja (RN-08, RN-16)

GET /api/expenses?from=2026-09-01&to=2026-09-30&categoryId=uncategorized&page=0&size=20
  → 200 { "content": [ { ...como el alta... } ], "page": 0, "size": 20,
          "totalElements": 3, "totalPages": 1 }

PATCH /api/expenses/{id}
  { "amount": "1100.50" }            // solo cambia lo que viene
  { "uncategorized": true }          // devuelve el gasto a la bandeja
  → 200 el gasto completo, como en el alta
  → 400 las mismas validaciones del alta, o categoryId junto con uncategorized
  → 404 inexistente, dado de baja o de otro hogar

DELETE /api/expenses/{id}
  → 204
  → 404 inexistente, ya dado de baja o de otro hogar
```

**Decisión de diseño.** El cuerpo mínimo del alta es importe y comercio. La fecha es la del día, el
responsable sale del token y la categoría la resuelve M4 (RF-14). Todo lo demás es opcional. Eso es
lo que baja el costo de registrar un gasto, que es el problema central que el proyecto ataca.

**El día es el de Argentina.** La fecha que completa el alta y el mes en curso del listado y del
análisis se deciden en la misma zona, `America/Argentina/Buenos_Aires`. Si el alta tomara la fecha en
UTC, un gasto cargado el 31 a las 22 h quedaría con fecha del 1 y no aparecería en el mes en que se
hizo.

**El importe del alta viaja con punto decimal** (RD-01): `"1234.56"`, nunca `"1.234,56"`. La interfaz
web arma el valor, así que no hay nada que interpretar, y un formato ambiguo se rechaza con `400`. La
normalización de formatos locales es de la captura de M6, que recibe texto leído de una notificación;
el normalizador, `AmountParser`, vive con el resto de M3 y rechaza cualquier formato que admita más de
una lectura en lugar de adivinar.

**Edición.** Cualquier integrante puede editar cualquier gasto del hogar, también el de alguien que ya
se fue (RN-18). El responsable no se edita: es quien cargó el gasto. Una categoría nueva tiene que estar
activa; si la que llega es la que el gasto ya tenía, no se vuelve a validar, para que un formulario
completo pueda corregir el importe de un gasto viejo cuya categoría se dio de baja después. Como
`categoryId` nulo significa "no cambiar", devolver un gasto a la bandeja se pide con
`"uncategorized": true`. Dos ediciones simultáneas del mismo gasto se serializan con un bloqueo de
fila, y una edición que llega después de la baja responde `404`.

**Gastos de otro hogar.** Un identificador de otro hogar responde `404`, igual que uno inexistente: un
`403` confirmaría que existe (HU-09). En el listado, filtrar por una categoría o un integrante de otro
hogar no da error: no hay gastos que coincidan y la página viene vacía, como en el análisis.

**Orden del listado.** Por fecha descendente (RF-17), y a igual fecha por fecha de carga e
identificador. Sin ese desempate, los gastos del mismo día podían cambiar de lugar entre una página y
la siguiente, y uno aparecer dos veces o ninguna.

**La bandeja no tiene período por defecto.** Con `categoryId=uncategorized` y sin `from` ni `to`, el
listado trae los gastos sin categoría de cualquier mes (RF-22). La bandeja es trabajo pendiente, no el
gasto de un período: con el mes en curso por defecto, un gasto del mes anterior quedaría sin clasificar
y fuera de la vista. Con período, filtra como cualquier otro listado.

**Las bajas son lógicas.** Un gasto dado de baja deja de aparecer en listados y análisis, pero la
fila se conserva. En una aplicación de dinero, el borrado físico destruye el historial sobre el que
después se pide explicación: quién cargó qué, cuándo y por cuánto. La consecuencia es que todas las
consultas de gastos filtran las filas dadas de baja.

**El conjunto de medios de pago es cerrado.** Lo garantiza un `CHECK` en la base, así que sumar uno
nuevo requiere una migración. Es deliberado por ahora: evita que cada cliente invente su propia
variante y que el análisis por medio de pago se fragmente.

Depende de M1, M4 (resolución de categoría) y M8.

---

## M4. Categorización

**Responsabilidad.** Convertir un nombre de comercio en una categoría, con un resultado
reproducible y corregible por el usuario.

Requisitos: RF-19 a RF-24, RF-43. Reglas: RN-09, RN-10, RN-12, RN-13, RN-16, RN-17.

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `GET` | `/api/categories` | Catálogo de categorías del hogar. | Sesión |
| `POST` | `/api/categories` | Crea una categoría. | Sesión |
| `PATCH` | `/api/categories/{id}` | Renombra una categoría. | Sesión |
| `DELETE` | `/api/categories/{id}` | Da de baja una categoría. La baja es lógica. | Sesión |
| `GET` | `/api/category-rules` | Reglas patrón-categoría del hogar. | Sesión |
| `POST` | `/api/category-rules` | Crea una regla y reclasifica retroactivamente. | Sesión |
| `DELETE` | `/api/category-rules/{id}` | Elimina una regla. | Sesión |

**El motor de reglas.** Una regla asocia un patrón a una categoría. Al registrarse un gasto, el
motor compara el nombre del comercio contra los patrones del hogar. Si alguno coincide, asigna su
categoría. No usa aprendizaje automático, así que la misma entrada produce siempre el mismo
resultado, siempre se puede decir qué regla se aplicó, y no hay dependencia de un servicio externo
ni costo por consulta.

Una categoría elegida a mano en el alta manda sobre las reglas. El motor solo corre cuando el gasto
llega sin categoría, y si ninguna regla coincide el gasto queda en la bandeja (RF-21, RN-12).

**Qué es coincidir.** Los patrones se guardan en minúscula, sin tildes y con un solo espacio entre
palabras: es la forma que muestra el listado de reglas, y la API la aplica a lo que recibe, así que la
interfaz puede proponer el comercio tal como está y el servidor lo lleva a patrón (CU-05, paso 2). Para
comparar, patrón y comercio se reducen a sus letras y números, sin tildes ni mayúsculas, y la regla
coincide si el patrón aparece en el comercio empezando al principio de una de sus palabras. Así
`supermercado` coincide con "Super Mercado Rosario", `mcdonald` con "McDonald's", `pedidosya` con
"PEDIDOS YA" y `uber` con "MERPAGO*UBER": el comercio se tipea a mano o llega de una notificación del
banco, y los espacios y la puntuación varían. Que tenga que empezar una palabra evita que `uber`
coincida con "Pub Ernesto" o con "Tuberías", y `coto` con "Escoto": ese error no se vería, porque el
gasto quedaría en una categoría equivocada y no en la bandeja. El final sí puede caer en medio de una
palabra, así que `farmacia` coincide con "Farmacias del Pueblo", y también `sube` con "Subestación".

Un patrón necesita al menos tres letras o números, y normalizado no puede pasar los 200 caracteres
(algunos caracteres se alargan al normalizarlos, como la ligadura `ﬃ`); si no, responde `400`. Se evitan además
patrones cortos o palabras comunes que coincidirían con comercios no relacionados: por eso la siembra
no incluye `dia` ni `claro`.

**Cuando coinciden varias reglas gana la del patrón más largo.** Con los patrones `super` y
`supermercado`, el comercio "Super Mercado Rosario" se resuelve por el segundo; con `uber` y
`uber eats`, un pedido de comida no queda clasificado como transporte. El criterio es que el patrón
más específico manda, que es lo que el usuario espera al agregar una regla más precisa sobre un
comercio que ya estaba cubierto. Si dos patrones empatan en longitud, gana la regla más antigua, de
modo que el resultado no cambia al agregar reglas nuevas. No hace falta una columna de prioridad:
el orden se deduce del patrón, y no hay un orden que el usuario deba mantener a mano. La longitud se
cuenta en letras y números, y entre las 43 reglas de la siembra, que tienen la misma fecha, desempata
el identificador: el resultado nunca depende del orden en que la base devuelve las reglas.

**Patrones repetidos (RN-10).** Dos patrones con las mismas letras y números, como `burger king` y
`burgerking`, coinciden con exactamente los mismos comercios, así que cuentan como el mismo y el
segundo responde `409`. La respuesta dice a qué categoría apunta la regla existente (CU-05, 4a): para
que el patrón apunte a otra, se elimina esa regla y se crea de nuevo.

**Reclasificación retroactiva (RN-13).** Al crear una regla, los gastos activos de la bandeja que
coinciden con el patrón salen de ella. Cada uno recibe la categoría que le daría el motor completo,
con la regla nueva incluida: con `uber eats` ya existente, crear `uber` no manda a Transporte un
"Uber Eats" que estaba en la bandeja. Es lo que habría pasado si la regla hubiera existido al
cargarlo. Los gastos que ya tienen categoría no se tocan, aunque coincidan: pueden ser una corrección
manual. La asignación vuelve a comprobar, fila por fila, que el gasto siga sin categoría y activo, así
que no pisa lo que otro integrante clasificó o dio de baja mientras tanto. Un gasto cargado en el mismo
instante en que se crea la regla puede quedar en la bandeja, donde se ve y se clasifica a mano.

La categoría de la regla se bloquea mientras se crea: si alguien la da de baja al mismo tiempo, o la
regla se crea antes y la baja la borra, o la baja llega antes y la regla se rechaza con `400`. Sin el
bloqueo, la regla podía quedar apuntando a una categoría dada de baja.

**Catálogo inicial.** Cada hogar nuevo recibe 10 categorías y 43 reglas de comercios habituales en
Argentina, sembradas por la migración V3. Dos categorías, Ropa y Educación, se siembran sin reglas.
No hay patrones de comercio confiables para ellas, y quedan disponibles para asignar a mano desde
la bandeja.

**Renombrar una categoría alcanza a todos sus gastos.** Los gastos la referencian por
identificador y el nombre vive en una sola fila, así que el cambio se refleja en el historial
completo sin ninguna actualización adicional. No pueden quedar dos nombres para la misma categoría.

**Dar de baja una categoría también es una baja lógica.** Los gastos que la usaban conservan su
categoría en el historial y en el análisis del período correspondiente; la categoría deja de
ofrecerse para nuevas asignaciones y sus reglas dejan de aplicarse. Las reglas, en cambio, se
eliminan físicamente: no son un hecho económico, sino una preferencia de clasificación.

Una categoría o una regla de otro hogar responde `404`, igual que una inexistente. El nombre de una
categoría es visible: no puede ser solo de espacios ni tener caracteres de control o invisibles.

Contratos principales:

```
GET /api/categories
  → 200 [ { "id": "...", "name": "Supermercado" } ]   // solo las activas, por nombre

POST /api/categories            { "name": "Mascotas" }
PATCH /api/categories/{id}      { "name": "Animales" }
  → 201 / 200 { "id": "...", "name": "..." }
  → 400 nombre vacío, de más de 200 caracteres o con caracteres invisibles
  → 404 (solo PATCH) inexistente, dada de baja o de otro hogar
  → 409 ya hay una categoría activa con ese nombre, sin distinguir mayúsculas (RN-09)

DELETE /api/categories/{id}
  → 204
  → 404 inexistente, ya dada de baja o de otro hogar

GET /api/category-rules
  → 200 [ { "id": "...", "pattern": "carrefour", "categoryId": "...", "categoryName": "Supermercado" } ]

POST /api/category-rules
  { "pattern": "Almacén de Pepe", "categoryId": "..." }
  → 201 { "id": "...", "pattern": "almacen de pepe", "categoryId": "...",
          "reclassified": 7 }   // gastos que salieron de la bandeja
  → 400 patrón con menos de tres letras o números o de más de 200 caracteres ya normalizado, o una
        categoría inexistente, de otro hogar o dada de baja
  → 409 el patrón ya existe en el hogar (RN-10), con la categoría de la regla existente

DELETE /api/category-rules/{id}
  → 204   // los gastos que ya clasificó conservan su categoría (RF-43)
  → 404 inexistente o de otro hogar
```

Depende de M8 (esquema y siembra) y M3 (los gastos que clasifica).

---

## M5. Análisis

**Responsabilidad.** Responder las preguntas que motivan el sistema: cuánto se gastó, en qué, quién
lo pagó y cómo evoluciona.

Requisitos: RF-25 a RF-29. Reglas: RN-08, RN-14, RN-16, RN-18.

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `GET` | `/api/expenses/summary` | Total del período y agregación por categoría, integrante y medio de pago. | Sesión |
| `GET` | `/api/expenses/monthly` | Serie mensual con media histórica. | Sesión |
| `GET` | `/api/expenses/merchants` | Comercios con mayor gasto acumulado. | Sesión |

Contratos principales:

```
GET /api/expenses/summary?from=2026-09-01&to=2026-09-30
  → 200 {
      "total": "154320.00",
      "byCategory":      [ { "categoryId": "...", "name": "Supermercado", "total": "82150.00" } ],
      "byOwner":         [ { "ownerId": "...", "name": "Ana", "total": "91200.00" } ],
      "byPaymentMethod": [ { "paymentMethod": "Tarjeta", "total": "120000.00" } ]
    }

GET /api/expenses/monthly
  → 200 { "months": [ { "month": "2026-09", "total": "154320.00" } ], "average": "131870.50" }

GET /api/expenses/merchants?from=2026-09-01&to=2026-09-30&limit=10
  → 200 [ { "merchant": "Carrefour", "total": "30000.00", "expenses": 3 } ]
```

Los tres aceptan los mismos filtros que el listado de gastos, sin la paginación (RF-29): `from` y
`to` (fechas ISO, inclusive), `categoryId` (un identificador o `uncategorized`), `ownerId` y
`paymentMethod`. Todos son opcionales y se combinan. Sin período, `/summary` y `/merchants` toman el
mes en curso (RD-07, CU-06), en la hora de Argentina, y `/monthly` toda la historia del hogar. Las
reglas del filtro son las mismas que las del listado: las dos peticiones comparten `ExpenseFilter`, y
las fechas de la URL se leen en ISO para toda la API (`spring.mvc.format.date: iso`). Un período invertido o un filtro mal formado responde `400`. Una categoría o un integrante
de otro hogar no da error: no hay gastos que coincidan, y la respuesta es la de un período vacío.
`limit` va de 1 a 50, y por defecto es 10.

Cada lista va de mayor a menor total. Un período sin gastos responde `"0.00"` y listas vacías, nunca
nulos. Los gastos dados de baja no cuentan (RN-16). Las categorías dadas de baja sí: sus gastos
siguen siendo gasto del período en que se hicieron. El total es la suma del desglose por medio de
pago, y las consultas del resumen corren en `REPEATABLE_READ`: el total y los desgloses coinciden
siempre, aunque otro integrante cargue un gasto mientras tanto. Los gastos sin categoría forman una fila de
`byCategory` con `categoryId` y `name` nulos. Quien se cambió de hogar sigue figurando en `byOwner`
con los gastos que dejó (RN-18).

La serie mensual tiene un punto por cada mes con gasto del período, incluido el mes en curso. La
media es lo habitual contra lo que se compara el mes (HU-14): el promedio de los meses con gasto
(RN-14), con los demás filtros pero sin el período, y sin el mes en curso. Con el período, mirar un
solo mes daría una media igual a ese mes; con el mes en curso, unos pocos días de gasto contarían como
un mes entero y la hundirían. Sin meses anteriores con gasto, la media es `"0.00"`. Se redondea a
centavos con redondeo bancario (`HALF_EVEN`), que no sesga los promedios hacia arriba.

El ranking de comercios agrupa sin distinguir mayúsculas y con los espacios normalizados, porque el
comercio se tipea a mano o llega de una notificación del banco: cualquier tira de espacios,
tabulaciones, saltos de línea, espacios duros o de ancho cero pasa a ser un espacio, y se recortan los
extremos. "Carrefour", "CARREFOUR " y "Carrefour" con un espacio duro al final son el mismo, y se
muestra la forma que más se repite.

**Decisión de diseño.** Las agregaciones se resuelven con consultas que agrupan en la base, no
trayendo los gastos a memoria. El volumen crece con el uso y el cálculo en la aplicación dejaría de
sostenerse (RNF-11). Las políticas de aislamiento alcanzan también a estas consultas, de modo que
los totales nunca pueden mezclar hogares. Las consultas no nombran el hogar: corren dentro de una
transacción, que fija el hogar de la sesión, y RLS deja fuera las filas de cualquier otro. Como
`app_current_household()` es `stable`, el planificador usa igual los índices que empiezan por
`household_id`. El desglose por categoría va dentro de `/summary`: es la misma consulta con los
mismos filtros, y la pantalla lo pide junto con el resto del resumen.

Depende de M3 (los datos que agrega) y M8.

---

## M6. API de integración

**Responsabilidad.** Permitir registrar un gasto desde fuera de la interfaz web, por ejemplo desde
una automatización del teléfono o un atajo, en el orden de los segundos.

Requisitos: RF-30 a RF-32. Reglas: RN-06, RN-07, RN-17, RN-18.

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `POST` | `/api/capture` | Captura rápida de un gasto. | Token de cliente máquina |

Contrato:

```
POST /api/capture
  Authorization: Token <token de cliente máquina>
  { "amount": "1.234,56", "merchant": "Carrefour", "paymentMethod": "Tarjeta" }
  → 201 { "id": "...", "amount": "1234.56", "merchant": "Carrefour", "categoryId": "...",
          "expenseDate": "2026-10-29" }
  → 400 importe que no se puede interpretar sin ambigüedad, o comercio vacío o inválido
  → 401 token inexistente, revocado, de un hogar que su dueño dejó o archivado
  → 403 con una sesión web en lugar de un token de cliente máquina
```

El gasto queda en el hogar del token, a nombre de su dueño y con la fecha de hoy en la hora de
Argentina, igual que un alta desde la interfaz. `paymentMethod` es opcional y por defecto es
`Tarjeta`: la captura sale de notificaciones del banco, que son de compras con tarjeta o de
transferencias. La categoría la resuelve el mismo motor de reglas que el alta (RN-17), así que una
captura y un gasto cargado a mano con el mismo comercio quedan en la misma categoría, y sin regla
que coincida el gasto queda en la bandeja. El comercio también llega como se leyó de la
notificación: antes de validarlo se reducen los saltos de línea, los espacios duros y los espacios
repetidos a uno solo, con el mismo criterio con el que el análisis agrupa los comercios.

**Normalización defensiva de importes.** El cliente que invoca este punto de entrada suele extraer
el importe de un texto, como una notificación bancaria o un mensaje, y lo envía tal como lo leyó.
El módulo acepta separadores de miles y decimales en formato local (`1.234,56`), en formato
anglosajón (`1,234.56`) y sin separadores, y los normaliza antes de validar (RF-32). Si el valor
resultante no cumple RN-06, se rechaza con un error explicativo en lugar de registrar un importe
equivocado.

**Decisión de diseño.** El canal de autenticación es propio y distinto del de la sesión web. El
token es de larga duración, se guarda en un dispositivo y se puede revocar individualmente sin
afectar la sesión de la persona. Solo habilita este punto de entrada, no el resto de la API: en
otra ruta se ignora, y la petición se rechaza por falta de sesión. Al autenticarlo, el módulo exige
que no esté revocado y que su hogar sea el hogar actual de su dueño (RN-18), así una automatización
no sigue cargando gastos en un hogar que su dueño dejó.

El token tiene la forma `gst_` seguida de 32 bytes aleatorios en base64url, y la base guarda solo
su hash SHA-256. Con 256 bits no hace falta limitar los intentos, como sí con los códigos de
invitación. Todo rechazo es el mismo 401, con `WWW-Authenticate: Token` y sin indicar el motivo.
Al autenticarlo se registra su último uso.

Depende de M1 (emisión y revocación de tokens), M3 y M4.

---

## M7. Interfaz de usuario

**Responsabilidad.** El cliente web. No contiene lógica de negocio: consume la API y presenta.

Requisitos: RF-33 a RF-38. No funcionales: RNF-14 a RNF-17, RNF-26.

| Pantalla | Contenido |
|---|---|
| Registro, inicio de sesión y confirmación | Alta de cuenta, acceso, confirmación de correo y restablecimiento de contraseña (RF-33, RF-06). |
| Tablero | Selector de período, total, gráficos de distribución y evolución, movimientos recientes y filtrado desde los gráficos (RF-34 a RF-37). |
| Movimientos | Listado paginado con filtros combinables y alta, edición y baja de gastos. |
| Bandeja de no categorizados | Gastos sin categoría y alta de reglas desde ahí (RF-22, RF-23). |
| Hogar | Integrantes, generación y canje de códigos de invitación, ajustes (RF-38). |

**Sesión.** El token del login se guarda en el navegador y dura sus 7 días (RNF-29). Sin sesión,
las pantallas de la app mandan al login, y un 401 de la API la descarta. El enlace de confirmación
que arma el backend es `/verify-email#token=...`, con el token en el fragmento como el del
restablecimiento: el navegador no lo manda al servidor que sirve el cliente web. Un enlace vencido o
usado lleva a pedir otro con `POST /api/auth/verify/resend`, que también se ofrece después del alta
y cuando el login rechaza un correo sin verificar.

**Diseños.** Dos disposiciones sobre el mismo código. En móvil, pantalla única con menú desplegable y
botón fijo de alta. En escritorio, barra superior con las secciones y el alta, y tabla. La barra
lateral de escritorio es de cada pantalla, no de la navegación: solo aparece si la pantalla la usa,
como el tablero para su resumen. El umbral es 768 px.

**Tablero.** El período es el mes en curso por defecto (RD-07), o el año, y la evolución muestra los
últimos doce meses con la media histórica al pie. La distribución es un solo anillo que se reparte por
categoría, persona o medio de pago, con una leyenda que tiene los mismos datos y los mismos clics, y
que es la que usan el teclado y el lector de pantalla. Lo que no tiene categoría va en gris. El período
y la porción elegida van en la dirección (`/?month=2026-07&categoryId=...`), con los nombres de los
filtros de la API, así un enlace se puede compartir y "atrás" deshace el último cambio.

**Filtrado interactivo.** Al seleccionar un mes en el gráfico de evolución, el resto de la pantalla
se re-filtra a ese mes. Al seleccionar una porción de la distribución, el total, la evolución y los
movimientos pasan a mostrar solo esa categoría, persona o medio de pago; el anillo sigue mostrando el
período completo, para que se vea cuánto pesa la porción (RF-37).

**Hogar.** Una sola pantalla con cuatro bloques: integrantes, invitar, nombre del hogar y unirse a
otro (RF-38). El código generado se muestra con su vencimiento y un botón para copiarlo, y vive solo
en el estado de la pantalla: no va a la dirección ni al almacenamiento del navegador, porque no se
puede volver a pedir y quien lo tiene entra al hogar. Unirse va en dos pasos, como pide CU-03: la
vista previa dice qué hogar es, si el actual queda archivado y cuántos tokens de cliente máquina se
revocan, y recién la confirmación canjea el código. Cancelar no lo consume. Después del canje la
pantalla vuelve a pedir el hogar y los integrantes; el token de sesión sigue sirviendo (RN-18).

Depende de todos los módulos de la API.

---

## M8. Plataforma y calidad

**Responsabilidad.** La base sobre la que se apoyan los demás: esquema, aislamiento, manejo de
errores, pruebas, integración continua y despliegue. Es el módulo que hace verificable la promesa
central del proyecto.

Requisitos: RF-39 a RF-42. Reglas: RN-15. No funcionales: RNF-01 a RNF-04, RNF-18, RNF-19, RNF-22 a
RNF-25, RNF-27, RNF-28.

| Componente | Estado | Descripción |
|---|---|---|
| Migraciones versionadas | Implementado | V1 esquema base, V2 rol y políticas de aislamiento, V2.5 índice de correo, V3 catálogo inicial, V4 baja lógica, V5 cambio de hogar y tablas de acceso, V6 versión de sesión. |
| Rol de aplicación restringido | Implementado | `gastos_api`, sin privilegio de omisión de políticas ni de superusuario. |
| Propagación del contexto de hogar | Implementado | Se fija al inicio de cada transacción, con alcance transaccional para que no se filtre entre peticiones al devolver la conexión al pool. |
| Entidades y repositorios | Implementado | Siete entidades validadas contra el esquema al arrancar. |
| Pruebas de aislamiento | Implementado | Las tres tablas con aislamiento, por lectura, alta, modificación y baja, en los tres contextos: sin hogar activo, con el ajeno y con el propio. |
| Emisor de correo | Implementado | Interfaz propia con implementación de desarrollo que escribe a consola. |
| Migraciones complementarias | Implementado | Migración V5: tokens de verificación y restablecimiento, invitaciones y tokens de cliente máquina, guardados como hash y sin RLS por ser datos de acceso. Archivado de hogares y cambio de hogar conservando los gastos en el hogar anterior (RN-18). |
| Baja lógica | Implementado | Marca de baja (`active`, `deleted_at`) en gastos y categorías, con un check que impide que se contradigan. El borrado por JPA es una baja lógica. Los gastos dados de baja quedan fuera de todas las consultas; las categorías, solo de las que las ofrecen, y sus reglas se eliminan. Índice único parcial para que un nombre dado de baja no siga ocupando el suyo. |
| Manejo global de errores | Parcial | Los errores de los controladores y de la validación, y los 401 y 403 que responde la cadena de seguridad antes de llegar a un controlador, salen como `problem+json` con el mismo formato. La cadena conserva además el header `WWW-Authenticate`. Falta un manejo explícito de los errores no previstos y de los de integridad de la base, para que respondan sin detalle técnico (RNF-17). |
| Integración continua | Implementado | GitHub Actions en cada PR: el backend con `./mvnw verify`, incluidas las pruebas de integración contra un PostgreSQL de Testcontainers, y el frontend con chequeo de tipos, build y lint. Que una ejecución en rojo bloquee la incorporación depende de marcar los dos checks como obligatorios en la protección de `main`. |
| Despliegue | Backend implementado, cliente web pendiente | El backend se publica en Azure App Service en cada incorporación a `main`, con Flyway al arrancar contra una Azure Database for PostgreSQL – Flexible Server (PostgreSQL 17). La preparación de la base y las variables de entorno están en el README. Falta publicar el cliente web. |

**Verificación de estado.** `GET /actuator/health` responde sin autenticación y sin detalle de
componentes. El detalle revelaría nombres de host, esquemas y estado de las dependencias (RF-41).

---

## Dependencias entre módulos

`X → Y` se lee «Y depende de X».

```
M8 Plataforma      →  base de todos
M1 Identidad       →  M2, M3, M4, M5, M6
M2 Hogares         →  M3, M5
M3 Gastos          →  M4, M5, M6
M4 Categorización  →  M3 (resolución en el alta), M5
M7 Interfaz        →  consume M1 a M6
```

M3 y M4 se necesitan mutuamente y es deliberado: el alta de un gasto pide a M4 que resuelva la
categoría, y el alta de una regla en M4 reclasifica los gastos de M3 que quedaron en la bandeja.
