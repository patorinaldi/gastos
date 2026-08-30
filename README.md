# TRABAJO FINAL INTEGRADOR

# Gastos

**Aplicación web para el registro y análisis de gastos compartidos en unidades de convivencia**

*Propuesta de Proyecto — 1.ª Entrega · Tecnicatura Universitaria en Programación, UTN*

| | |
|---|---|
| **Integrantes** | Juan Fransisco Reales · Elian Rivoira · Patricio Tomas Rinaldi |
| **Tutor/a** | Gerardo Adrian Herrera |
| **Repositorio único** | [https://github.com/patorinaldi](https://github.com/patorinaldi/gastos) |
| **Tipo de propuesta** | Inventiva propia (sin cliente externo) |
| **Fecha** | 31 de agosto de 2026 |

---

## 1. Problema y solución propuesta

En una unidad de convivencia el dinero se gasta de forma distribuida: cada integrante paga con su propio medio, en momentos y comercios distintos, y muchas veces por bienes que benefician a todo el grupo. El resultado es que nadie tiene una imagen completa del gasto conjunto. Las alternativas habituales —una planilla compartida, un grupo de mensajería, la memoria— fallan por la misma razón: dependen de un trabajo manual y repetitivo que nadie sostiene más de unas semanas.

El problema, entonces, no es la falta de herramientas sino su costo de uso. Las aplicaciones existentes exigen abrir la app, elegir cuenta, seleccionar una categoría de una lista larga y completar varios campos; ese costo, multiplicado por cada compra, es lo que hace abandonar el registro. A eso se suma que están pensadas para un individuo: cuando dos personas comparten economía, cada una termina con un registro parcial y ninguna con el total.

Gastos es una aplicación web multiusuario que resuelve ambas cosas. Cada persona se registra con su correo y al hacerlo se crea su hogar, al que puede invitar al resto mediante un código; a partir de ahí todos los gastos alimentan una misma vista compartida. Registrar un gasto requiere únicamente el importe y el comercio: el sistema asigna la fecha, identifica a la persona por sus credenciales y determina la categoría aplicando las reglas del hogar. Si ningún patrón coincide, el gasto queda en una bandeja de revisión desde la cual el usuario crea una regla que se aplica retroactivamente sobre los pendientes, de modo que enseñarle un comercio al sistema se hace una sola vez. Sobre esa base, un tablero muestra el total del período, la distribución por categoría, persona o medio de pago, y la evolución mensual contra la media histórica, con visualizaciones interactivas que re-filtran el resto de la pantalla.

**Elementos originales del trabajo:**

- **Aislamiento multi-inquilino a nivel de motor.** La separación de datos entre hogares se implementa con Row-Level Security de PostgreSQL y no con condiciones escritas a mano en cada consulta. La diferencia es de naturaleza: una condición olvidada en una sola consulta es una fuga de datos, mientras que una política del motor alcanza a todas —incluidas las nativas— y falla de forma cerrada.
- **Categorización automática determinista.** El motor de reglas asigna categorías sin recurrir a aprendizaje automático: el resultado es reproducible, explicable, corregible por el usuario y sin dependencias externas ni costo por consulta.
- **Captura desde clientes automatizados.** Además de la interfaz web, la API expone un punto de entrada invocable desde automatizaciones del dispositivo móvil, con un canal de autenticación propio para clientes máquina, que lleva el registro de un gasto al orden de los segundos.

---

## 2. Alcance

| Área | Funcionalidad comprometida |
|---|---|
| **Identidad** | Registro con correo y contraseña, verificación de correo obligatoria, inicio de sesión, recuperación de contraseña. |
| **Hogares** | Creación del hogar en el alta, invitación de integrantes por código, gestión de miembros y ajustes del hogar. |
| **Gastos** | Alta, edición y baja; listado paginado con filtros por período, categoría, persona y medio de pago. |
| **Categorización** | Catálogo de categorías, reglas patrón–categoría, aplicación automática en el alta, bandeja de no categorizados y reclasificación retroactiva. |
| **Análisis** | Total del período, agregaciones por categoría / persona / medio de pago, serie mensual con media histórica y filtrado interactivo desde los gráficos. |
| **Integración** | Punto de entrada para captura rápida desde clientes externos, autenticado por token de cliente máquina. |
| **Seguridad** | Aislamiento verificado entre hogares, contraseñas cifradas, limitación de intentos de inicio de sesión y errores estandarizados. |

Queda explícitamente fuera de alcance, como evolución posterior: el inicio de sesión mediante proveedores externos (Google, Apple), la pertenencia de un usuario a varios hogares simultáneamente, la sincronización con entidades bancarias, los presupuestos y alertas por categoría, la liquidación de deudas entre integrantes, el manejo de múltiples monedas y las aplicaciones nativas móviles.

Las dos primeras exclusiones son decisiones de diseño y no recortes: el backend emite su propia sesión, por lo que incorporar un proveedor externo más adelante es agregar una fuente de identidad sin rehacer el mecanismo; y en el dominio de las unidades de convivencia cada persona pertenece a un hogar, de modo que soportar el caso múltiple obliga a resolver la noción de "hogar activo" sin beneficio actual.

---

## 3. Stack tecnológico

| Capa | Tecnología | Justificación |
|---|---|---|
| **Lenguaje backend** | Java 21 (LTS) | Soporte a largo plazo y tipado estático fuerte. Aporta records, usados para los objetos de transferencia. |
| **Framework backend** | Spring Boot 4.1 | Versión estable actual. Integra inyección de dependencias, capa web, acceso a datos y seguridad con configuración por convención. |
| **Persistencia** | Spring Data JPA / Hibernate | Repositorios declarativos, sin impedir consultas nativas donde la agregación lo requiera. |
| **Seguridad** | Spring Security | Autenticación y autorización sin estado; cifrado de contraseñas con bcrypt. |
| **Base de datos** | PostgreSQL 17 | Integridad referencial, transacciones y —determinante aquí— Row-Level Security nativo. Versión dentro del período de soporte oficial. |
| **Migraciones** | Flyway | Esquema versionado y reproducible, aplicado de forma idéntica en desarrollo y en producción. |
| **Construcción** | Maven (wrapper) | Dependencias y ciclo de build incorporados al repositorio, sin depender de la instalación local del desarrollador. |
| **Frontend** | React 19 + TypeScript + Vite | Interfaz por componentes con los contratos de la API tipados en el cliente; build estático apto para CDN. |
| **Visualizaciones** | Recharts | Gráficos declarativos sobre React para la distribución y la evolución mensual. |
| **Pruebas** | JUnit 5 + Testcontainers | Pruebas de integración contra un PostgreSQL real en contenedor, evitando bases en memoria que no reproducen el motor de producción. |
| **CI/CD** | GitHub Actions | Compilación y pruebas en cada cambio; despliegue automático desde la rama principal. |

Se optó por un modelo relacional y no documental porque el dominio lo es (usuarios que pertenecen a hogares, gastos que referencian categorías y personas, reglas asociadas a categorías), las consultas centrales son agregaciones sobre esas relaciones, y la integridad referencial entre un hogar y sus datos es precisamente lo que se busca garantizar.

---

## 4. Arquitectura y módulos

La solución se compone de tres piezas desplegables independientes que se comunican por HTTP: un cliente web de página única servido como contenido estático, que no contiene lógica de negocio; una API REST que concentra la totalidad de las reglas, la validación y las decisiones de autorización; y una instancia gestionada de PostgreSQL que, además de almacenar, hace cumplir el aislamiento entre hogares.

El backend se estructura en capas con dependencias unidireccionales —web (controladores y objetos de transferencia), service (lógica y límites transaccionales), repository (acceso a datos), domain (entidades), security (autenticación y propagación del contexto del hogar) y config (configuración transversal)— de forma que cada una conozca únicamente a la inmediatamente inferior. Las entidades persistentes y los objetos de transferencia son tipos distintos: el modelo interno nunca se expone en la API. Los importes se manejan con tipos decimales de precisión exacta y nunca con punto flotante, y los errores se devuelven con un formato estandarizado resuelto de forma centralizada.

| Módulo | Contenido |
|---|---|
| **M1 — Identidad y acceso** | Registro, verificación de correo por token de un solo uso, inicio y cierre de sesión, recuperación de contraseña, y gestión de tokens para clientes máquina. |
| **M2 — Hogares** | Creación del hogar en el alta, generación y canje de códigos de invitación, listado de integrantes y ajustes del hogar. |
| **M3 — Gastos** | Alta, edición y baja; listado paginado con filtros combinables; normalización defensiva de los importes recibidos desde clientes externos. |
| **M4 — Categorización** | Catálogo de categorías, motor de reglas patrón–categoría, resolución automática en el alta, bandeja de no categorizados y reclasificación retroactiva. |
| **M5 — Análisis** | Total del período, agregaciones por categoría, persona y medio de pago, serie mensual con media histórica y catálogos auxiliares. |
| **M6 — API de integración** | Punto de entrada de captura rápida para automatizaciones externas y su canal de autenticación por token de larga duración. |
| **M7 — Interfaz de usuario** | Tablero con selector de período, indicadores, gráficos de distribución y evolución, listado con carga incremental, filtrado interactivo y los diseños móvil y escritorio. |
| **M8 — Plataforma y calidad** | Migraciones, políticas de aislamiento y rol de base restringido, manejo global de errores, batería de pruebas, integración continua y despliegue. |

El detalle definitivo de cada módulo, junto con el esquema de base de datos, se presentará en la 2.ª entrega.

---

## 5. Estrategia de datos y seguridad

El esquema de la base de datos es propiedad exclusiva de las migraciones versionadas: la herramienta de mapeo se configura para validar que el modelo de clases coincide con el esquema existente, sin permitirle crearlo ni modificarlo. Si entidades y migraciones se desincronizan, la aplicación falla al arrancar de forma deliberada; se prefiere un error inmediato en el despliegue antes que una divergencia silenciosa entre el código y los datos.

Toda la información de negocio pertenece a un hogar, y el aislamiento se implementa con políticas de seguridad a nivel de fila:

- El identificador del hogar viaja como atributo dentro del token de sesión emitido al iniciar sesión.
- Al comenzar cada transacción, la aplicación lo establece como variable de sesión con alcance limitado a esa transacción, para que no se filtre entre peticiones al devolver la conexión al pool.
- Las políticas definidas sobre cada tabla restringen tanto la lectura como la escritura al hogar indicado por esa variable.
- La aplicación se conecta con un rol que no puede omitir esas políticas, de modo que el aislamiento no depende de la corrección del código de aplicación.

La consecuencia práctica es que el comportamiento por defecto es cerrado: si el contexto del hogar no se establece, las consultas no devuelven filas en lugar de devolver las de todos los hogares. Verificar esto se considera el entregable de seguridad más importante del proyecto, y por eso se aborda con pruebas específicas: por cada punto de acceso de la API se comprueba que un usuario del hogar A no puede leer, modificar ni eliminar datos del hogar B, ni siquiera conociendo sus identificadores.

En cuanto a credenciales, las contraseñas se almacenan cifradas con una función de derivación con sal; el registro exige verificación del correo mediante token de un solo uso con vencimiento, y la cuenta no queda operativa hasta confirmarla; la recuperación de contraseña usa el mismo patrón, por lo que la tabla de tokens se diseña desde el inicio para ambos usos. Se aplican requisitos mínimos de longitud y limitación de la tasa de intentos de inicio de sesión.

---

## 6. Repositorio y despliegue

La totalidad del proyecto residirá en un único repositorio de GitHub, declarado en esta entrega, con esta estructura: un `README.md` principal con la descripción, los integrantes, las tecnologías y las instrucciones de instalación y ejecución local; `/backend` con el código de la API organizado por capas y sus pruebas, incluyendo las migraciones versionadas del esquema; `/frontend` con el código del cliente web; `/docs` con la documentación y los informes de todas las entregas; y `/.github/workflows` con las canalizaciones de integración y despliegue continuo.

La rama principal estará protegida: el trabajo se realiza en ramas de funcionalidad e ingresa mediante pull request con revisión de al menos otro integrante, y una ejecución fallida de las pruebas bloquea la incorporación. Por convención, los identificadores del código se escriben en inglés y los valores de datos del dominio permanecen en español.

El requisito de contar con al menos un componente en línea se cubre con holgura: los tres estarán desplegados y operativos.

| Componente | Servicio previsto | Alternativa |
|---|---|---|
| **Cliente web** | Cloudflare Pages, publicado en cada integración a la rama principal. | Vercel / Netlify |
| **API REST** | Servicio de aplicaciones gestionado sobre Linux con soporte de Java 21. | Render / Railway |
| **Base de datos** | Instancia PostgreSQL gestionada, con copias de seguridad y conexión cifrada. | Neon / Supabase / Aiven |

Las migraciones pendientes se aplican automáticamente al arrancar la aplicación desplegada. Ningún secreto —cadenas de conexión, claves de firma, credenciales del proveedor de correo— se versiona en el repositorio: se inyectan como variables de entorno del servicio.

---

## 7. Plan de trabajo

| Iteración | Período | Objetivo |
|---|---|---|
| **Preparación** | 24/08 – 30/08 | Propuesta, creación y estructuración del repositorio, esqueleto de backend y frontend con arranque local verificado. **Hito: 1.ª Entrega.** |
| **Iteración 1** | 31/08 – 13/09 | Modelo de datos, primeras migraciones, entidades y repositorios, políticas de aislamiento y rol restringido, con pruebas de integración operativas. |
| **Iteración 2** | 14/09 – 27/09 | Esquema de base de datos y listado detallado de módulos documentados en el repositorio. **Hito: 2.ª Entrega — condición de regularidad.** |
| **Iteración 3** | 28/09 – 11/10 | Módulos de identidad y hogares: registro, verificación de correo, inicio de sesión, invitaciones. Primer despliegue del backend. |
| **Iteración 4** | 12/10 – 25/10 | Módulos de gastos, categorización y API de integración, con la batería de pruebas de aislamiento completa por punto de acceso. |
| **Iteración 5** | 26/10 – 08/11 | Interfaz completa: tablero, gráficos, filtrado interactivo y ambos diseños. Despliegue del cliente web y verificación de extremo a extremo. |
| **Cierre** | 09/11 – 14/11 | Informe final, video explicativo, documentación y estabilización. **Hito: Entrega Final.** |
| **Reserva** | 15/11 – 21/11 | Margen para correcciones solicitadas y preparación de la defensa oral. |

El plan reserva una semana completa sin trabajo comprometido antes del cierre del cursado, de modo que un retraso en cualquier iteración no comprometa la entrega final. El equipo trabaja en iteraciones de dos semanas con seguimiento mediante issues del repositorio y una reunión semanal de sincronización, más las de seguimiento con el tutor según la periodicidad que este defina.

---

## 8. Riesgos y criterios de éxito

| Riesgo | Mitigación |
|---|---|
| El contexto del hogar no se establece en algún camino de ejecución y se compromete el aislamiento. | Establecimiento centralizado al inicio de cada transacción, rol sin permiso de omisión de políticas y pruebas de aislamiento por cada punto de acceso. |
| El aislamiento a nivel de motor introduce complejidad no prevista en el manejo del pool de conexiones. | Se aborda en la primera iteración, antes de construir funcionalidad sobre él, para que un replanteo no arrastre trabajo ya hecho. |
| Dependencia de un proveedor externo de correo transaccional. | El envío se abstrae detrás de una interfaz propia con una implementación de desarrollo que no requiere el servicio. |
| Alcance excesivo para la duración del cursado. | Alcance delimitado por escrito, con funcionalidades declaradas fuera de alcance que actúan como margen, y una semana de reserva en el cronograma. |
| Disponibilidad desigual de los integrantes. | Iteraciones cortas con objetivos verificables, seguimiento visible en el repositorio y responsabilidades compartidas por área. |

El proyecto se considerará exitoso si al cierre del cursado la aplicación está desplegada y accesible en línea permitiendo el recorrido completo —registro, verificación, invitación de un segundo integrante, carga de gastos y consulta del análisis—; existe evidencia automatizada de que dos hogares no pueden acceder a los datos del otro por ningún punto de acceso; el esquema se reconstruye íntegramente desde las migraciones del repositorio sin pasos manuales; y se entregan el informe escrito y el video explicativo.
