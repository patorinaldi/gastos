# Especificación de requerimientos

Proyecto Gastos. Trabajo Final Integrador, TUP UTN.

Este documento especifica qué debe hacer el sistema y con qué calidad. Las reglas del dominio que
restringen estos requisitos están en [reglas-de-negocio.md](reglas-de-negocio.md).

## Convenciones

| Prefijo | Significado |
|---|---|
| `RF-xx` | Requisito funcional. Qué hace el sistema |
| `RNF-xx` | Requisito no funcional. Con qué calidad lo hace |
| `RD-xx` | Requisito de dominio. Lo impone el área de aplicación, no el usuario |
| `RN-xx` | Regla de negocio (ver [reglas-de-negocio.md](reglas-de-negocio.md)) |

Prioridad según MoSCoW: `Debe` (imprescindible para la entrega final), `Debería` (valioso,
sacrificable), `Podría` (deseable).

---

## 1. Requisitos funcionales

### M1. Identidad y acceso

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-01 | El sistema debe permitir registrar un usuario con nombre, correo electrónico y contraseña. | Debe | Implementado |
| RF-02 | El sistema debe crear un hogar nuevo y asociar al usuario recién registrado como integrante, dentro de la misma transacción del alta. | Debe | Implementado |
| RF-03 | El sistema debe enviar, al completarse el registro, un correo con un enlace de verificación que contiene un token de un solo uso. | Debe | Implementado |
| RF-04 | El sistema debe rechazar el inicio de sesión de una cuenta cuyo correo no fue verificado. | Debe | Implementado |
| RF-05 | El sistema debe autenticar a un usuario por correo y contraseña, y emitir un token de sesión. El token puede llevar el identificador del hogar como dato de contexto para la interfaz, pero no se usa para autorizar: el hogar de cada petición se resuelve desde el usuario autenticado, porque puede haber cambiado desde que se emitió el token (RN-18). | Debe | Implementado |
| RF-06 | El sistema debe permitir solicitar el restablecimiento de la contraseña mediante un token de un solo uso enviado por correo. | Debe | Implementado |
| RF-07 | El sistema debe permitir emitir y revocar tokens de larga duración para clientes automatizados, asociados a un hogar. | Debería | Implementado |

### M2. Hogares

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-08 | El sistema debe permitir a un integrante generar un código de invitación para su hogar. | Debe | Implementado |
| RF-09 | El sistema debe permitir a un usuario registrado canjear un código de invitación e incorporarse al hogar correspondiente. | Debe | Implementado |
| RF-10 | El sistema debe listar los integrantes del hogar del usuario autenticado. | Debe | Implementado |
| RF-11 | El sistema debe permitir editar el nombre del hogar. | Debería | Implementado |
| RF-12 | El sistema debe invalidar un código de invitación una vez canjeado o vencido. | Debe | Implementado |

### M3. Gastos

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-13 | El sistema debe permitir registrar un gasto indicando únicamente importe y comercio. | Debe | Implementado |
| RF-14 | El sistema debe asignar automáticamente al gasto la fecha del día y el usuario autenticado como responsable, cuando no se indiquen explícitamente. | Debe | Implementado |
| RF-15 | El sistema debe permitir editar cualquier campo de un gasto del propio hogar. | Debe | Implementado |
| RF-16 | El sistema debe permitir la baja lógica de un gasto del propio hogar. El gasto deja de figurar en listados y análisis, pero se conserva. | Debe | Implementado |
| RF-17 | El sistema debe listar los gastos del hogar de forma paginada, ordenados por fecha descendente. | Debe | Implementado |
| RF-18 | El sistema debe permitir filtrar el listado de gastos por período, categoría, integrante y medio de pago, de forma combinable. | Debe | Implementado |

### M4. Categorización

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-19 | El sistema debe sembrar en cada hogar nuevo un catálogo inicial de categorías y reglas. | Debe | Implementado |
| RF-20 | El sistema debe resolver la categoría de un gasto en el alta, aplicando las reglas patrón-categoría del hogar sobre el nombre del comercio. Si coinciden varias, aplica la de patrón más largo y, a igual longitud, la más antigua. | Debe | Implementado |
| RF-21 | El sistema debe dejar el gasto sin categoría cuando ningún patrón coincide, en lugar de asignar una categoría por defecto. | Debe | Implementado |
| RF-22 | El sistema debe ofrecer un listado de los gastos sin categoría del hogar (bandeja de no categorizados). | Debe | Implementado |
| RF-23 | El sistema debe permitir crear una regla patrón-categoría y aplicarla retroactivamente a los gastos sin categoría que coincidan. | Debe | Implementado |
| RF-24 | El sistema debe permitir administrar el catálogo de categorías del hogar (alta, renombrado y baja lógica). | Debería | Implementado |
| RF-43 | El sistema debe permitir listar las reglas patrón-categoría del hogar y eliminarlas. Eliminar una regla no altera los gastos ya clasificados. | Debería | Implementado |

### M5. Análisis

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-25 | El sistema debe informar el total gastado por el hogar en un período. | Debe | Implementado |
| RF-26 | El sistema debe informar el gasto del período agregado por categoría, por integrante y por medio de pago. | Debe | Implementado |
| RF-27 | El sistema debe informar la serie mensual de gasto del hogar junto con su media histórica. | Debe | Implementado |
| RF-28 | El sistema debe informar los comercios con mayor gasto acumulado en el período. | Debería | Implementado |
| RF-29 | El sistema debe permitir que las consultas de análisis acepten los mismos filtros que el listado de gastos. | Debería | Implementado |

### M6. API de integración

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-30 | El sistema debe exponer un punto de entrada de captura rápida que registre un gasto a partir de importe y comercio. | Debe | Implementado |
| RF-31 | El sistema debe autenticar ese punto de entrada mediante token de cliente máquina, sin requerir sesión de usuario. | Debe | Implementado |
| RF-32 | El sistema debe normalizar de forma defensiva los importes recibidos desde clientes externos, aceptando separadores de miles y decimales en formato local. | Debe | Implementado |

### M7. Interfaz de usuario

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-33 | El sistema debe ofrecer pantallas de registro, inicio de sesión y confirmación de correo. | Debe | Implementado |
| RF-34 | El sistema debe ofrecer un tablero con selector de período, total del período y listado de movimientos. | Debe | Implementado |
| RF-35 | El sistema debe representar gráficamente la distribución del gasto por categoría, integrante y medio de pago. | Debe | Implementado |
| RF-36 | El sistema debe representar gráficamente la evolución mensual del gasto contra su media histórica. | Debe | Implementado |
| RF-37 | El sistema debe permitir que al seleccionar un elemento de un gráfico se re-filtre el resto de la pantalla. | Debería | Implementado |
| RF-38 | El sistema debe ofrecer pantallas para invitar integrantes, unirse por código y ver los miembros del hogar. | Debe | Implementado |

### M8. Plataforma y calidad

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-39 | El sistema debe versionar el esquema de base de datos mediante migraciones y aplicarlas automáticamente al arrancar. | Debe | Implementado |
| RF-40 | El sistema debe devolver los errores de la API en un formato estandarizado y resuelto de forma centralizada. | Debe | Pendiente |
| RF-41 | El sistema debe exponer un punto de verificación de estado que no requiera autenticación ni revele detalle interno. | Debe | Implementado |
| RF-42 | El sistema debe ejecutar la batería de pruebas en cada incorporación de cambios y bloquear la incorporación si falla. | Debe | Implementado |

---

## 2. Requisitos no funcionales

Cada RNF se expresa con una métrica verificable. Se evitan términos como "rápido", "seguro" o
"amigable". No son comprobables y, por lo tanto, no son requisitos.

### 2.1 Seguridad

| ID | Requisito | Métrica de verificación |
|---|---|---|
| RNF-01 | El aislamiento entre hogares debe hacerse cumplir en el motor de base de datos y no depender de condiciones escritas en cada consulta. | Las tablas de negocio tienen políticas RLS activas con `FORCE`. Lo verifica `HouseholdIsolationTest`. |
| RNF-02 | Un usuario de un hogar no debe poder leer, modificar ni eliminar datos de otro hogar, aun conociendo sus identificadores. | Por cada tabla de negocio y cada operación (lectura, alta, modificación, baja) existe una prueba automatizada que lo comprueba. |
| RNF-03 | Ante ausencia de contexto de hogar, las consultas deben devolver cero filas, nunca el total. | Prueba automatizada sobre las tres tablas con RLS sin contexto activo. |
| RNF-04 | La aplicación debe conectarse a la base con un rol sin privilegio de omisión de políticas. | `select rolbypassrls, rolsuper from pg_roles where rolname = current_user` devuelve `false` en ambos. |
| RNF-05 | Las contraseñas deben almacenarse cifradas con una función de derivación con sal, nunca en texto plano ni con hash simple. | Revisión de código y ausencia de la contraseña en el esquema. Algoritmo bcrypt. |
| RNF-06 | El sistema debe limitar los intentos fallidos de inicio de sesión a 10 por correo ingresado desde una misma dirección IP, exista o no una cuenta con ese correo, a 50 por dirección IP con cualquier correo, y a 100 por correo desde cualquier dirección, en ventanas de 15 minutos. Las direcciones IPv6 se agrupan por su prefijo /64. Superado un límite, rechaza los intentos hasta que cierre la ventana. | Prueba automatizada del limitador, con un correo registrado y con uno sin cuenta, de que el bloqueo de un correo no alcanza a otra dirección y de que un ataque repartido entre direcciones se frena en el tope por correo. |
| RNF-07 | Ningún secreto (cadena de conexión, clave de firma, credencial de correo) debe estar versionado en el repositorio. | Inspección del repositorio. Todos se inyectan por variable de entorno. |
| RNF-08 | Los tokens de verificación de correo y de restablecimiento de contraseña deben ser de un solo uso y vencer a las 24 horas de emitidos. | Prueba automatizada de reutilización y de vencimiento. |
| RNF-09 | Los registros de la aplicación no deben contener contraseñas, hashes ni tokens en claro. | Revisión de código. Las entidades no exponen `toString()` con campos sensibles. |
| RNF-29 | El token de sesión debe vencer a los 7 días de emitido, y las sesiones de un usuario deben poder invalidarse antes de su vencimiento: cambiar la contraseña cierra todas las abiertas, en todos los dispositivos. Siete días evitan pedir el inicio de sesión a diario desde el teléfono; la invalidación acota el daño de un token robado. | Prueba automatizada del vencimiento y de la invalidación por versión de sesión. |
| RNF-30 | El sistema debe limitar los códigos de invitación inválidos que se prueban, sumando la vista previa y el canje, a 10 por usuario y a 50 por dirección IP en ventanas de 15 minutos, y no debe permitir más de 10 invitaciones vigentes por hogar. Superado un límite de intentos, rechaza las pruebas hasta que cierre la ventana, aun con un código válido. Acota la probabilidad de adivinar un código vigente y entrar a un hogar ajeno. | Prueba automatizada del limitador, de que durante el bloqueo un código válido tampoco entra, de que el código del hogar propio no cuenta como fallo, y del rechazo de la invitación que supera el tope. |
| RNF-31 | El sistema debe limitar las altas de cuenta a 20 por dirección IP en ventanas de 1 hora, contando todos los intentos que llegan al alta, tengan éxito o no. Superado el límite, rechaza el alta con 429 hasta que cierre la ventana, sin consultar ni escribir en la base. Acota el costo de las llamadas al alta, que corren bcrypt, y el sondeo de qué correos tienen cuenta, que el 409 del alta no puede ocultar (RN-01). | Prueba automatizada del limitador y del rechazo por HTTP, incluido que el alta rechazada no crea nada. |
| RNF-32 | El sistema debe limitar los pedidos de enlaces por correo, sumando el restablecimiento de contraseña y el reenvío de la verificación, a 5 por correo ingresado y a 20 por dirección IP en ventanas de una hora, exista o no una cuenta con ese correo. Superado un límite, rechaza el pedido indicando cuándo se puede volver a intentar. | Prueba automatizada que supera el límite y recibe el rechazo. |
| RNF-33 | El sistema no debe permitir más de 10 tokens de cliente máquina vigentes por persona, ni más de 20 pedidos de emisión por persona en ventanas de 1 hora, salgan bien o no. Superado el tope de vigentes, rechaza la emisión hasta que se revoque alguno; superado el de pedidos, la rechaza con 429 hasta que cierre la ventana. | Prueba automatizada que emite hasta el tope, recibe el rechazo y vuelve a emitir tras revocar uno, y que supera el tope de pedidos emitiendo y revocando. |

### 2.2 Rendimiento

| ID | Requisito | Métrica de verificación |
|---|---|---|
| RNF-10 | El registro de un gasto desde la API de integración debe responder en menos de 1 segundo en el percentil 95. | Medición sobre el entorno desplegado con 10.000 gastos cargados. |
| RNF-11 | Las consultas de análisis del período deben responder en menos de 2 segundos en el percentil 95, con un hogar de hasta 10.000 gastos. | Igual que RNF-10. |
| RNF-12 | El listado de gastos debe paginarse, con un tamaño de página máximo de 100 elementos. | Revisión del contrato de la API. La petición de un tamaño mayor se acota o se rechaza. |
| RNF-13 | Las consultas frecuentes por hogar, fecha, categoría e integrante deben apoyarse en índices y no en recorridos secuenciales de tabla. | `EXPLAIN` sobre las consultas del listado y del análisis. |

### 2.3 Usabilidad

| ID | Requisito | Métrica de verificación |
|---|---|---|
| RNF-14 | Registrar un gasto desde la interfaz debe requerir como máximo 3 interacciones desde el tablero (abrir el formulario, completar importe y comercio, confirmar). | Recorrido guiado sobre la interfaz desplegada. |
| RNF-15 | La interfaz debe ser utilizable en pantallas desde 360 px de ancho, sin desplazamiento horizontal. | Verificación en navegador a 360, 768 y 1440 px. |
| RNF-16 | Toda operación que consulte el servidor debe presentar estado de carga, estado vacío y estado de error diferenciados. | Revisión pantalla por pantalla. |
| RNF-17 | Los mensajes de error dirigidos al usuario deben estar en español y describir la acción correctiva, sin exponer detalle técnico. | Revisión de los textos de la interfaz. |

### 2.4 Fiabilidad y disponibilidad

| ID | Requisito | Métrica de verificación |
|---|---|---|
| RNF-18 | El esquema de base de datos debe poder reconstruirse íntegramente desde las migraciones del repositorio, sin pasos manuales. | Arranque contra una base vacía en la batería de pruebas de integración. |
| RNF-19 | La aplicación debe rechazar el arranque si el modelo de clases y el esquema de la base divergen. | `ddl-auto: validate`. Lo verifica `EntityMappingTest`. |
| RNF-20 | La base de datos debe contar con copias de seguridad automáticas provistas por el servicio gestionado. | Configuración del proveedor. |
| RNF-21 | El fallo del envío de correo no debe impedir el registro del usuario. La operación debe poder reintentarse. | Prueba automatizada con el emisor de correo fallando. |

### 2.5 Mantenibilidad

| ID | Requisito | Métrica de verificación |
|---|---|---|
| RNF-22 | El backend debe organizarse en capas con dependencias unidireccionales (web, service, repository, domain). | Revisión de la estructura de paquetes. |
| RNF-23 | Las entidades persistentes y los objetos de transferencia deben ser tipos distintos. El modelo interno no se expone en la API. | Revisión de código en cada incorporación de cambios. |
| RNF-24 | Toda incorporación a la rama principal debe pasar por revisión de al menos otro integrante, y de los otros dos cuando toque aislamiento, migraciones o autenticación. | Reglas de protección de rama y plantilla de PR. |
| RNF-25 | Los identificadores del código deben escribirse en inglés y los valores de datos del dominio en español. | Revisión de código. |

### 2.6 Portabilidad y escalabilidad

| ID | Requisito | Métrica de verificación |
|---|---|---|
| RNF-26 | El cliente web debe poder publicarse como contenido estático en una red de distribución, sin requerir ejecución en servidor. | Artefacto de compilación estático desplegado en Cloudflare Pages. |
| RNF-27 | El entorno de desarrollo debe levantarse con la base de datos en contenedor, sin instalación local de PostgreSQL. | `docker compose up` y arranque verificado. |
| RNF-28 | Las pruebas de integración deben ejecutarse contra el mismo motor y versión mayor de base de datos que producción. | Testcontainers con PostgreSQL 17. |

---

## 3. Requisitos de dominio

Surgen del área de aplicación, la economía compartida de una unidad de convivencia, y no de un
pedido explícito del usuario. Se cumplen obligatoriamente.

| ID | Requisito | Fundamento |
|---|---|---|
| RD-01 | Los importes monetarios deben representarse con tipo decimal de precisión exacta y dos decimales, nunca con punto flotante. | El redondeo binario introduce diferencias de centavos que, acumuladas en agregaciones, invalidan los totales. |
| RD-02 | Todo importe de gasto debe ser estrictamente mayor que cero. | Un gasto de importe cero o negativo no representa un hecho económico. Las devoluciones quedan fuera de alcance. |
| RD-03 | Todo dato de negocio debe pertenecer a exactamente un hogar. | El hogar es la unidad de convivencia que comparte economía, y la unidad de aislamiento del sistema. |
| RD-04 | Un usuario pertenece a exactamente un hogar. | En el dominio de las unidades de convivencia una persona comparte economía con un solo grupo. Ver §2 de la propuesta. |
| RD-05 | Los medios de pago admitidos son `Efectivo`, `Tarjeta` y `Transferencia`. | Cubren las formas de pago habituales del dominio sin obligar al usuario a clasificar con más detalle del que aporta valor. |
| RD-06 | Los valores de datos del dominio se registran en español. | El sistema se usa en Argentina y los valores se muestran tal como se almacenan. |
| RD-07 | El período de análisis por defecto es el mes calendario. | Los ingresos y los vencimientos de servicios del dominio son mensuales. |
| RD-08 | El día y el mes en curso se deciden en la hora de Argentina (`America/Argentina/Buenos_Aires`), la misma para todo el hogar y para todos sus clientes. | Las fechas de los gastos no tienen hora, y un mes tiene que significar lo mismo para todos los integrantes. Con la zona de cada dispositivo, un gasto cargado el 31 a las 22 h podría quedar en el mes siguiente, y el tablero abrir en un mes distinto del que el servidor considera en curso. El sistema se usa en Argentina (RD-06). |

---

## 4. Matriz de trazabilidad

Relaciona cada módulo con los requisitos que lo justifican y con las reglas de negocio que lo
restringen. Sirve para verificar que ningún módulo carece de requisitos y que ningún requisito
queda sin módulo que lo implemente.

| Módulo | Requisitos funcionales | Reglas de negocio | No funcionales críticos |
|---|---|---|---|
| M1 Identidad y acceso | RF-01 a RF-07 | RN-01, RN-02, RN-03, RN-11, RN-18 | RNF-05, RNF-06, RNF-08, RNF-29, RNF-31, RNF-32, RNF-33 |
| M2 Hogares | RF-08 a RF-12 | RN-04, RN-05, RN-18 | RNF-01, RNF-02, RNF-30 |
| M3 Gastos | RF-13 a RF-18 | RN-06, RN-07, RN-08, RN-12, RN-16 | RNF-10, RNF-12, RNF-13 |
| M4 Categorización | RF-19 a RF-24, RF-43 | RN-09, RN-10, RN-12, RN-13, RN-16, RN-17 | RNF-13 |
| M5 Análisis | RF-25 a RF-29 | RN-08, RN-14, RN-16, RN-18 | RNF-11, RNF-13 |
| M6 API de integración | RF-30 a RF-32 | RN-06, RN-07, RN-17, RN-18 | RNF-10 |
| M7 Interfaz de usuario | RF-33 a RF-38 | ninguna | RNF-14 a RNF-17 |
| M8 Plataforma y calidad | RF-39 a RF-42 | RN-15 | RNF-01 a RNF-04, RNF-18, RNF-19, RNF-22 a RNF-25 |

---

## 5. Fuera de alcance

Se declaran para acotar el compromiso de la entrega final. No son omisiones.

| Funcionalidad | Motivo |
|---|---|
| Inicio de sesión con proveedores externos (Google, Apple). | El backend emite su propia sesión. Incorporar un proveedor más adelante es agregar una fuente de identidad, no rehacer el mecanismo. |
| Pertenencia simultánea a varios hogares. | Obliga a resolver la noción de hogar activo sin beneficio en el dominio actual (ver RD-04). |
| Sincronización con entidades bancarias. | Requiere integración con terceros y tratamiento de datos financieros fuera del alcance académico. |
| Presupuestos y alertas por categoría. | Funcionalidad de evolución posterior. No afecta el modelo de datos actual. |
| Liquidación de deudas entre integrantes. | El sistema informa el gasto conjunto, no salda cuentas individuales. |
| Múltiples monedas. | Obliga a modelar cotizaciones y fechas de conversión. |
| Aplicaciones nativas móviles. | El cliente web responde a pantallas desde 360 px (RNF-15) y la captura rápida se cubre con M6. |
| Registro de auditoría dedicado. | El esquema ya conserva lo necesario para reconstruir qué pasó: fechas de alta, modificación, baja y archivado, el responsable de cada gasto, y quién creó y quién canjeó cada invitación. |
| Exportación de datos. | Funcionalidad de evolución posterior. No afecta el modelo de datos actual. |
