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
| RF-01 | El sistema debe permitir registrar un usuario con nombre, correo electrónico y contraseña. | Debe | Pendiente |
| RF-02 | El sistema debe crear un hogar nuevo y asociar al usuario recién registrado como integrante, dentro de la misma transacción del alta. | Debe | Pendiente |
| RF-03 | El sistema debe enviar, al completarse el registro, un correo con un enlace de verificación que contiene un token de un solo uso. | Debe | Pendiente |
| RF-04 | El sistema debe rechazar el inicio de sesión de una cuenta cuyo correo no fue verificado. | Debe | Pendiente |
| RF-05 | El sistema debe autenticar a un usuario por correo y contraseña, y emitir un token de sesión que incluya el identificador de su hogar. | Debe | Pendiente |
| RF-06 | El sistema debe permitir solicitar el restablecimiento de la contraseña mediante un token de un solo uso enviado por correo. | Debe | Pendiente |
| RF-07 | El sistema debe permitir emitir y revocar tokens de larga duración para clientes automatizados, asociados a un hogar. | Debería | Pendiente |

### M2. Hogares

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-08 | El sistema debe permitir a un integrante generar un código de invitación para su hogar. | Debe | Pendiente |
| RF-09 | El sistema debe permitir a un usuario registrado canjear un código de invitación e incorporarse al hogar correspondiente. | Debe | Pendiente |
| RF-10 | El sistema debe listar los integrantes del hogar del usuario autenticado. | Debe | Pendiente |
| RF-11 | El sistema debe permitir editar el nombre del hogar. | Debería | Pendiente |
| RF-12 | El sistema debe invalidar un código de invitación una vez canjeado o vencido. | Debe | Pendiente |

### M3. Gastos

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-13 | El sistema debe permitir registrar un gasto indicando únicamente importe y comercio. | Debe | Pendiente |
| RF-14 | El sistema debe asignar automáticamente al gasto la fecha del día y el usuario autenticado como responsable, cuando no se indiquen explícitamente. | Debe | Pendiente |
| RF-15 | El sistema debe permitir editar cualquier campo de un gasto del propio hogar. | Debe | Pendiente |
| RF-16 | El sistema debe permitir la baja lógica de un gasto del propio hogar. El gasto deja de figurar en listados y análisis, pero se conserva. | Debe | Pendiente |
| RF-17 | El sistema debe listar los gastos del hogar de forma paginada, ordenados por fecha descendente. | Debe | Pendiente |
| RF-18 | El sistema debe permitir filtrar el listado de gastos por período, categoría, integrante y medio de pago, de forma combinable. | Debe | Pendiente |

### M4. Categorización

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-19 | El sistema debe sembrar en cada hogar nuevo un catálogo inicial de categorías y reglas. | Debe | Implementado |
| RF-20 | El sistema debe resolver la categoría de un gasto en el alta, aplicando las reglas patrón-categoría del hogar sobre el nombre del comercio. Si coinciden varias, aplica la de patrón más largo y, a igual longitud, la más antigua. | Debe | Pendiente |
| RF-21 | El sistema debe dejar el gasto sin categoría cuando ningún patrón coincide, en lugar de asignar una categoría por defecto. | Debe | Pendiente |
| RF-22 | El sistema debe ofrecer un listado de los gastos sin categoría del hogar (bandeja de no categorizados). | Debe | Pendiente |
| RF-23 | El sistema debe permitir crear una regla patrón-categoría y aplicarla retroactivamente a los gastos sin categoría que coincidan. | Debe | Pendiente |
| RF-24 | El sistema debe permitir administrar el catálogo de categorías del hogar (alta, renombrado y baja lógica). | Debería | Pendiente |

### M5. Análisis

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-25 | El sistema debe informar el total gastado por el hogar en un período. | Debe | Pendiente |
| RF-26 | El sistema debe informar el gasto del período agregado por categoría, por integrante y por medio de pago. | Debe | Pendiente |
| RF-27 | El sistema debe informar la serie mensual de gasto del hogar junto con su media histórica. | Debe | Pendiente |
| RF-28 | El sistema debe informar los comercios con mayor gasto acumulado en el período. | Debería | Pendiente |
| RF-29 | El sistema debe permitir que las consultas de análisis acepten los mismos filtros que el listado de gastos. | Debería | Pendiente |

### M6. API de integración

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-30 | El sistema debe exponer un punto de entrada de captura rápida que registre un gasto a partir de importe y comercio. | Debe | Pendiente |
| RF-31 | El sistema debe autenticar ese punto de entrada mediante token de cliente máquina, sin requerir sesión de usuario. | Debe | Pendiente |
| RF-32 | El sistema debe normalizar de forma defensiva los importes recibidos desde clientes externos, aceptando separadores de miles y decimales en formato local. | Debe | Pendiente |

### M7. Interfaz de usuario

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-33 | El sistema debe ofrecer pantallas de registro, inicio de sesión y confirmación de correo. | Debe | Pendiente |
| RF-34 | El sistema debe ofrecer un tablero con selector de período, total del período y listado de movimientos. | Debe | Pendiente |
| RF-35 | El sistema debe representar gráficamente la distribución del gasto por categoría, integrante y medio de pago. | Debe | Pendiente |
| RF-36 | El sistema debe representar gráficamente la evolución mensual del gasto contra su media histórica. | Debe | Pendiente |
| RF-37 | El sistema debe permitir que al seleccionar un elemento de un gráfico se re-filtre el resto de la pantalla. | Debería | Pendiente |
| RF-38 | El sistema debe ofrecer pantallas para invitar integrantes, unirse por código y ver los miembros del hogar. | Debe | Pendiente |

### M8. Plataforma y calidad

| ID | Requisito | Prioridad | Estado |
|---|---|---|---|
| RF-39 | El sistema debe versionar el esquema de base de datos mediante migraciones y aplicarlas automáticamente al arrancar. | Debe | Implementado |
| RF-40 | El sistema debe devolver los errores de la API en un formato estandarizado y resuelto de forma centralizada. | Debe | Pendiente |
| RF-41 | El sistema debe exponer un punto de verificación de estado que no requiera autenticación ni revele detalle interno. | Debe | Implementado |
| RF-42 | El sistema debe ejecutar la batería de pruebas en cada incorporación de cambios y bloquear la incorporación si falla. | Debe | Pendiente |

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
| RNF-06 | El sistema debe limitar los intentos fallidos de inicio de sesión a 10 por cuenta, con bloqueo de 15 minutos al superarlos. | Prueba automatizada del limitador. |
| RNF-07 | Ningún secreto (cadena de conexión, clave de firma, credencial de correo) debe estar versionado en el repositorio. | Inspección del repositorio. Todos se inyectan por variable de entorno. |
| RNF-08 | Los tokens de verificación de correo y de restablecimiento de contraseña deben ser de un solo uso y vencer a las 24 horas de emitidos. | Prueba automatizada de reutilización y de vencimiento. |
| RNF-09 | Los registros de la aplicación no deben contener contraseñas, hashes ni tokens en claro. | Revisión de código. Las entidades no exponen `toString()` con campos sensibles. |

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

---

## 4. Matriz de trazabilidad

Relaciona cada módulo con los requisitos que lo justifican y con las reglas de negocio que lo
restringen. Sirve para verificar que ningún módulo carece de requisitos y que ningún requisito
queda sin módulo que lo implemente.

| Módulo | Requisitos funcionales | Reglas de negocio | No funcionales críticos |
|---|---|---|---|
| M1 Identidad y acceso | RF-01 a RF-07 | RN-01, RN-02, RN-03, RN-11 | RNF-05, RNF-06, RNF-08 |
| M2 Hogares | RF-08 a RF-12 | RN-04, RN-05 | RNF-01, RNF-02 |
| M3 Gastos | RF-13 a RF-18 | RN-06, RN-07, RN-08, RN-16 | RNF-10, RNF-12, RNF-13 |
| M4 Categorización | RF-19 a RF-24 | RN-09, RN-10, RN-12, RN-13, RN-16, RN-17 | RNF-13 |
| M5 Análisis | RF-25 a RF-29 | RN-08, RN-14, RN-16 | RNF-11, RNF-13 |
| M6 API de integración | RF-30 a RF-32 | RN-06, RN-07 | RNF-10 |
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
