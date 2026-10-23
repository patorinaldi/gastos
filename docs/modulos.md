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
| M1 Identidad y acceso | Juan (usuario), Pato (núcleo de seguridad) | Alta de cuenta, inicio de sesión, sesión, tokens de cliente máquina y restablecimiento de contraseña implementados; verificación de correo pendiente |
| M2 Hogares | Elian | Backend implementado, pantallas pendientes |
| M3 Gastos | Elian | Pendiente |
| M4 Categorización | Elian | Catálogo inicial implementado, motor pendiente |
| M5 Análisis | Juan | Pendiente |
| M6 API de integración | Juan | Pendiente |
| M7 Interfaz de usuario | Elian, Pato | Sistema de diseño y shell implementados, pantallas pendientes |
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
| `POST` | `/api/auth/login` | Valida credenciales y emite el token de sesión. | No |
| `POST` | `/api/auth/password/forgot` | Emite y envía un token de restablecimiento. | No |
| `POST` | `/api/auth/password/reset` | Cambia la contraseña con un token válido. | No |
| `GET` | `/api/auth/me` | Devuelve el usuario autenticado y su hogar. | Sesión |
| `POST` | `/api/auth/machine-tokens` | Emite un token de larga duración para un cliente automatizado. | Sesión |
| `DELETE` | `/api/auth/machine-tokens/{id}` | Revoca un token de cliente automatizado. | Sesión |

Contratos principales:

```
POST /api/auth/register
  { "name": "Ana", "email": "ana@ejemplo.com", "password": "..." }
  → 201 { "userId": "...", "householdId": "...", "emailVerified": false }
  → 409 si el correo ya está registrado (RN-01)
  → 429 superado el límite de altas por dirección (RNF-31)

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

POST /api/auth/password/reset
  { "token": "...", "password": "..." }
  → 204 contraseña cambiada; se cierran todas las sesiones del usuario (RNF-29)
  → 400 contraseña de menos de 8 caracteres o de más de 72 bytes; el enlace no se gasta
  → 410 enlace inexistente, vencido o ya usado (RN-03)
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
arma con `WEB_URL`. El canje marca el token como usado con un update condicional, así que de dos
canjes simultáneos solo uno cambia la contraseña; marca también como usados los demás enlaces que
la persona haya pedido, e incrementa su versión de sesión. Los pedidos se limitan (RNF-32) a 5 por
correo y 20 por dirección por hora: cada pedido puede mandar un correo, y sin límite cualquiera
podría llenar la casilla de otra persona. Se cuentan por el correo escrito, exista o no la cuenta,
así que el `429` tampoco revela nada. El tope por correo permite que alguien le impida a otra
persona pedir enlaces durante una hora; es el precio de proteger su casilla.

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

Requisitos: RF-13 a RF-18. Reglas: RN-06, RN-07, RN-08, RN-12.

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
| `from`, `to` | fecha | Período. Por defecto, el mes en curso (RD-07). |
| `categoryId` | uuid | Filtra por categoría. `uncategorized` para la bandeja (RF-22). |
| `ownerId` | uuid | Filtra por integrante. |
| `paymentMethod` | enum | `Efectivo`, `Tarjeta` o `Transferencia`. |
| `page`, `size` | entero | Paginación. `size` máximo 100 (RNF-12). |

Contratos principales:

```
POST /api/expenses
  { "amount": "1234.56", "merchant": "Carrefour", "paymentMethod": "Tarjeta" }
  → 201 { "id": "...", "categoryId": "...", "expenseDate": "2026-09-21", "ownerId": "..." }
  → 400 importe no positivo (RN-06) o comercio vacío (RN-07)
```

**Decisión de diseño.** El cuerpo mínimo del alta es importe y comercio. La fecha es la del día, el
responsable sale del token y la categoría la resuelve M4 (RF-14). Todo lo demás es opcional. Eso es
lo que baja el costo de registrar un gasto, que es el problema central que el proyecto ataca.

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

Requisitos: RF-19 a RF-24, RF-43. Reglas: RN-09, RN-10, RN-12, RN-13.

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

Los patrones se almacenan en minúscula y sin tildes. Se evitan patrones cortos o palabras comunes
que coincidirían con comercios no relacionados.

**Cuando coinciden varias reglas gana la del patrón más largo.** Con los patrones `super` y
`supermercado`, el comercio "Super Mercado Rosario" se resuelve por el segundo; con `uber` y
`uber eats`, un pedido de comida no queda clasificado como transporte. El criterio es que el patrón
más específico manda, que es lo que el usuario espera al agregar una regla más precisa sobre un
comercio que ya estaba cubierto. Si dos patrones empatan en longitud, gana la regla más antigua, de
modo que el resultado no cambia al agregar reglas nuevas. No hace falta una columna de prioridad:
el orden se deduce del patrón, y no hay un orden que el usuario deba mantener a mano.

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

Contratos principales:

```
POST /api/category-rules
  { "pattern": "carrefour", "categoryId": "..." }
  → 201 { "id": "...", "reclassified": 7 }   // gastos que salieron de la bandeja
  → 409 el patrón ya existe en el hogar (RN-10)
```

Depende de M8 (esquema y siembra) y M3 (los gastos que clasifica).

---

## M5. Análisis

**Responsabilidad.** Responder las preguntas que motivan el sistema: cuánto se gastó, en qué, quién
lo pagó y cómo evoluciona.

Requisitos: RF-25 a RF-29. Reglas: RN-08, RN-14.

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
```

**Decisión de diseño.** Las agregaciones se resuelven con consultas que agrupan en la base, no
trayendo los gastos a memoria. El volumen crece con el uso y el cálculo en la aplicación dejaría de
sostenerse (RNF-11). Las políticas de aislamiento alcanzan también a estas consultas, de modo que
los totales nunca pueden mezclar hogares.

Depende de M3 (los datos que agrega) y M8.

---

## M6. API de integración

**Responsabilidad.** Permitir registrar un gasto desde fuera de la interfaz web, por ejemplo desde
una automatización del teléfono o un atajo, en el orden de los segundos.

Requisitos: RF-30 a RF-32. Reglas: RN-06, RN-07, RN-18.

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `POST` | `/api/capture` | Captura rápida de un gasto. | Token de cliente máquina |

Contrato:

```
POST /api/capture
  Authorization: Token <token de cliente máquina>
  { "amount": "1.234,56", "merchant": "Carrefour" }
  → 201 { "id": "...", "categoryId": "...", "amount": "1234.56" }
```

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
| Registro, inicio de sesión y confirmación | Alta de cuenta, acceso y confirmación de correo (RF-33). |
| Tablero | Selector de período, total, gráficos de distribución y evolución, movimientos recientes (RF-34 a RF-36). |
| Movimientos | Listado paginado con filtros combinables y alta, edición y baja de gastos. |
| Bandeja de no categorizados | Gastos sin categoría y alta de reglas desde ahí (RF-22, RF-23). |
| Hogar | Integrantes, generación y canje de códigos de invitación, ajustes (RF-38). |

**Diseños.** Dos disposiciones sobre el mismo código. En móvil, pantalla única con botón fijo de
alta. En escritorio, barra lateral y tabla. El umbral es 768 px.

**Filtrado interactivo.** Al seleccionar un mes en el gráfico de evolución, el resto de la pantalla
se re-filtra a ese mes. Al seleccionar una porción de un gráfico de distribución, se enfoca esa
dimensión (RF-37).

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
