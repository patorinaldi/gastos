# TRABAJO FINAL INTEGRADOR

# Gastos

**Aplicación web para el registro y análisis de gastos compartidos en unidades de convivencia**

*Informe final — Entrega final · Tecnicatura Universitaria en Programación, UTN*

| | |
|---|---|
| **Integrantes** | Juan Francisco Reales · Elian Rivoira · Patricio Tomás Rinaldi |
| **Tutor/a** | Gerardo Adrián Herrera |
| **Repositorio único** | [https://github.com/patorinaldi/gastos](https://github.com/patorinaldi/gastos) |
| **Aplicación en línea** | Cliente web: *a completar* · API: [gastos-api-rrr…azurewebsites.net](https://gastos-api-rrr-d2eyhtcmcabudrgk.canadacentral-01.azurewebsites.net/actuator/health) |
| **Fecha** | 14 de noviembre de 2026 |

---

## 1. Contenido de la entrega

Este informe resume el sistema construido, cómo se verificó y cómo se trabajó, y remite a los
documentos que desarrollan cada parte. Todos están en `/docs`, versionados junto con el código.

| Contenido | Dónde está |
|---|---|
| Código fuente | El repositorio: `backend/` (API), `frontend/` (cliente web), `.github/workflows/` (integración continua y despliegue) |
| Requerimientos y trazabilidad | [`requerimientos.md`](requerimientos.md), [`trazabilidad.md`](trazabilidad.md) |
| Reglas de negocio | [`reglas-de-negocio.md`](reglas-de-negocio.md) |
| Casos de uso e historias de usuario | [`casos-de-uso.md`](casos-de-uso.md), [`historias-de-usuario.md`](historias-de-usuario.md) |
| Modelo de datos y aislamiento | [`modelo-de-datos.md`](modelo-de-datos.md), [`der/`](der/) |
| Módulos y contratos de la API | [`modulos.md`](modulos.md) |
| Diagramas | [`diagramas.md`](diagramas.md) |
| Despliegue | [`despliegue.md`](despliegue.md) |
| Video explicativo | *A completar antes de la entrega* |
| Entregas anteriores | [`entrega-1-propuesta.md`](entrega-1-propuesta.md), [`entrega-2-esquema-y-modulos.md`](entrega-2-esquema-y-modulos.md) |

---

## 2. El sistema

En una unidad de convivencia el dinero se gasta de forma distribuida: cada integrante paga con su
propio medio, en momentos y comercios distintos, y nadie tiene la imagen completa del gasto
conjunto. Las herramientas existentes fallan por su costo de uso y porque están pensadas para un
individuo. Gastos ataca las dos cosas: un hogar compartido donde todos los gastos alimentan una
misma vista, y un registro que pide solo el importe y el comercio.

El recorrido de una persona es este:

1. **Crea su cuenta**, y con ella su hogar y un catálogo inicial de categorías y reglas. Confirma
   su correo con un enlace de un solo uso.
2. **Invita al resto** del hogar con un código, que cada uno canjea desde su cuenta.
3. **Carga gastos** con importe y comercio. El sistema completa la fecha y el responsable, y
   asigna la categoría con las reglas del hogar. Lo que ninguna regla reconoce queda en una bandeja,
   desde donde una regla nueva lo clasifica de una vez, también hacia atrás.
4. **Carga gastos sin abrir la aplicación**, desde una automatización del teléfono, con un token de
   cliente máquina que emite y revoca desde su sesión.
5. **Consulta el tablero**: el total del período, su distribución por categoría, persona o medio de
   pago, la evolución mensual contra la media histórica, y los últimos movimientos. Tocar un mes o
   una porción del gráfico filtra el resto de la pantalla.

Los tres elementos originales de la propuesta quedaron implementados: el **aislamiento entre
hogares en el motor de base de datos** (§5), la **categorización determinista** por reglas, sin
aprendizaje automático ni servicios externos, y la **captura desde clientes automatizados** con un
canal de autenticación propio (§6).

---

## 3. Alcance logrado

### 3.1 Requerimientos funcionales

De los 43 requerimientos funcionales, 41 están implementados al momento de escribir este informe.
El estado de cada uno está en [`requerimientos.md`](requerimientos.md).

| Área | Requisitos | Estado |
|---|---|---|
| M1 Identidad y acceso | RF-01 a RF-07 | Implementados: alta con hogar, verificación de correo, inicio de sesión, recuperación de contraseña, tokens de cliente máquina |
| M2 Hogares | RF-08 a RF-12 | Implementados |
| M3 Gastos | RF-13 a RF-18 | Implementados |
| M4 Categorización | RF-19 a RF-24, RF-43 | Implementados |
| M5 Análisis | RF-25 a RF-29 | Implementados |
| M6 API de integración | RF-30 a RF-32 | Implementados |
| M7 Interfaz de usuario | RF-33 a RF-38 | RF-33 a RF-37 implementados; RF-38, pantallas del hogar, en revisión |
| M8 Plataforma y calidad | RF-39 a RF-42 | RF-39 a RF-41 implementados; RF-42, ver abajo |

> **A completar antes de la entrega:** el estado final de RF-38 (PR de la pantalla del hogar) y de
> RF-42. Las pruebas corren en cada PR desde la iteración 2; que una ejecución en rojo bloquee la
> incorporación depende de la protección de la rama `main`, que se configura en GitHub.
> También el estado de las pantallas de gastos, bandeja y reglas, que no son un requerimiento
> propio pero sí parte del recorrido del §2.

### 3.2 Requerimientos no funcionales

Los 33 requerimientos no funcionales se verifican de tres formas: con pruebas automatizadas (límites
de intentos, vencimiento y uso único de los tokens, aislamiento, validaciones), con la configuración
del repositorio (capas, convenciones, migraciones, integración continua) o en el entorno desplegado.

Dos quedan sin medir: **RNF-10** (captura en menos de 1 segundo) y **RNF-11** (análisis en menos de 2
segundos con 10.000 gastos) exigen una medición sobre el entorno desplegado con datos cargados, que
no se hizo. Las consultas de análisis agrupan en la base y usan los índices por hogar, pero eso es
diseño, no medición.

### 3.3 Fuera de alcance

Se mantuvieron fuera de alcance las mismas funcionalidades que declaró la propuesta: proveedores
externos de identidad, la pertenencia a varios hogares, la sincronización bancaria, presupuestos y
alertas, la liquidación de deudas entre integrantes, múltiples monedas y aplicaciones nativas.

---

## 4. Arquitectura

| Capa | Tecnología |
|---|---|
| API | Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA con Hibernate, Flyway |
| Base de datos | PostgreSQL 17, con Row-Level Security |
| Cliente web | React 19, TypeScript, Vite 8, React Router 7, Recharts (solo el gráfico de distribución) |
| Pruebas | JUnit, Testcontainers con PostgreSQL 17, Vitest |
| Infraestructura | Azure App Service y Azure Database for PostgreSQL – Flexible Server para la API; Cloudflare Pages para el cliente web; correo por SMTP |

La API se organiza en capas con dependencias en un solo sentido, `web → service → repository →
domain` (RNF-22): los controladores traducen entre los contratos de la API y los tipos del servicio,
que no conoce los DTOs. Se divide en ocho módulos, detallados en [`modulos.md`](modulos.md):

| Módulo | Responsabilidad |
|---|---|
| M1 Identidad y acceso | Quién es el usuario y a qué hogar pertenece |
| M2 Hogares | Integrantes, invitaciones y cambio de hogar |
| M3 Gastos | Alta, edición, baja lógica y listado |
| M4 Categorización | Catálogo y motor de reglas |
| M5 Análisis | Totales, desgloses, serie mensual y comercios |
| M6 API de integración | Captura desde clientes automatizados |
| M7 Interfaz de usuario | El cliente web |
| M8 Plataforma y calidad | Esquema, aislamiento, errores, salud, pruebas |

---

## 5. Aislamiento entre hogares

Es el entregable de seguridad más importante del proyecto, y se construyó primero, en la iteración
1, antes que cualquier funcionalidad encima. El detalle está en
[`modelo-de-datos.md`](modelo-de-datos.md).

- **Las políticas están en el motor.** Gastos, categorías y reglas tienen Row-Level Security con
  políticas de lectura y escritura sobre el hogar de la transacción. Una consulta que olvide filtrar
  por hogar no devuelve filas de otro: devuelve solo las del hogar en curso, o ninguna.
- **La aplicación no puede saltearlas.** Se conecta con un rol sin `BYPASSRLS`, distinto del que
  corre las migraciones.
- **El hogar se fija en un solo lugar.** Al empezar cada transacción, con alcance limitado a ella, así
  no se filtra entre peticiones que reusan una conexión del pool. Sin hogar en contexto, las tablas
  protegidas se comportan como vacías.
- **El hogar sale del usuario, no del token.** Se lee de `users.household_id` en cada petición, para
  que quien se cambió de hogar no siga operando en el anterior con un token emitido antes (RN-18).
- **El motor agrega una última barrera.** Un disparador rechaza un gasto, un token de cliente máquina
  o una invitación cuyo usuario no integre hoy el hogar de la fila.

**Evidencia.** La propuesta pedía demostrar que dos hogares no pueden acceder a los datos del otro
por ningún punto de acceso. Hay pruebas de aislamiento en tres niveles: la base, sin contexto y con
el contexto de otro hogar; los puntos de acceso de identidad y hogares; y la batería completa por
punto de acceso, que intenta leer, modificar y eliminar datos de otro hogar conociendo sus
identificadores.

---

## 6. Identidad, cuentas y seguridad

- **Contraseñas** cifradas con bcrypt, con un máximo de 72 bytes medido en bytes y no en caracteres,
  que es lo que bcrypt alcanza a leer.
- **Sesión** con un token firmado que vence a los 7 días. Lleva una versión de sesión: restablecer la
  contraseña la incrementa y cierra todas las sesiones abiertas, en todos los dispositivos (RNF-29).
- **Verificación de correo y recuperación de contraseña** con tokens de un solo uso que vencen a las
  24 horas (RN-03). La base guarda solo su hash, y el enlace lleva el token en el fragmento de la
  dirección, que el navegador no manda a ningún servidor. Pedir un enlace responde igual exista o no
  la cuenta, para no revelar quién está registrado.
- **Límites de intentos** sobre el inicio de sesión, las altas, los códigos de invitación, los pedidos
  de enlaces por correo y la emisión de tokens de cliente máquina (RNF-06, RNF-30 a RNF-33).
- **Tokens de cliente máquina** con un canal propio: solo autentican la captura, se rechazan si fueron
  revocados o si su hogar ya no es el de su dueño, y se revocan solos al cambiarse de hogar o al
  restablecer la contraseña.
- **Ningún secreto en el log** (RNF-09): los correos aparecen como un hash con clave, y el envío por
  consola, que escribe los correos en el log, no acepta los que llevan tokens salvo que se habilite
  a propósito, como hacen el perfil de desarrollo y las pruebas.
- **Errores estandarizados** en formato `problem+json` en toda la API (RF-40).

---

## 7. Calidad

**Pruebas.** El backend tiene 510 pruebas en 72 clases. Las de integración corren contra PostgreSQL
17 real con Testcontainers, la misma versión mayor que producción (RNF-28): las políticas de
aislamiento, los disparadores y los índices parciales no se pueden probar contra una base en
memoria. El cliente web tiene 100 pruebas con Vitest sobre la lógica que no es dibujo: formato de
importes y fechas, período del tablero, porciones de los gráficos, sesión y llamadas a la API.

> **A completar antes de la entrega:** actualizar los números con el estado final de `main`.

**Integración continua.** Cada PR y cada incorporación a `main` compilan y corren toda la batería del
backend y del cliente web. Cada incorporación a `main` que pasa la batería se despliega sola.

**Revisión.** Todo cambio entró por un pull request revisado por al menos otro integrante, y los que
tocan migraciones, políticas, autenticación o aislamiento, por los otros dos (RNF-24). Al cierre de
este borrador se incorporaron 51 pull requests. Las revisiones dejaron correcciones concretas que
quedaron en el historial de cada PR, por ejemplo un bloqueo mutuo entre dos canjes simultáneos de
enlaces de restablecimiento, o un enlace de un solo uso que quedaba escrito en el log de producción.

**El esquema se reconstruye desde las migraciones.** Cada ejecución de las pruebas crea una base
vacía y aplica las migraciones de la V1 a la V7, y la aplicación valida al arrancar que las entidades
coinciden con el esquema (`ddl-auto: validate`). Ninguna tabla se crea a mano.

---

## 8. Despliegue

| Parte | Dónde |
|---|---|
| API | Azure App Service, Java 21. Las migraciones corren al arrancar |
| Base de datos | Azure Database for PostgreSQL – Flexible Server, PostgreSQL 17 |
| Cliente web | Cloudflare Pages, publicado desde `main` |
| Correo | SMTP de Gmail |

La API se despliega desde la integración continua, que publica el mismo ejecutable que acaba de
probar y espera a que el punto de salud responda antes de dar el despliegue por bueno. GitHub entra
a Azure por OIDC, sin contraseñas guardadas. Las variables de entorno, el primer despliegue, la
verificación posterior y los problemas frecuentes están en [`despliegue.md`](despliegue.md).

---

## 9. Cambios respecto de la propuesta

Los cambios de la etapa de diseño están en el §5 de la 2.ª entrega. Durante la construcción hubo
estos otros, ninguno de los cuales reduce el alcance:

| Cambio | Motivo |
|---|---|
| **El hogar de cada petición sale del usuario y no del token** (RF-05, RN-18). | Con el hogar dentro del token, quien se cambia de hogar seguiría operando en el anterior hasta que el token venza. |
| **Restablecer la contraseña cierra todas las sesiones, revoca los tokens de cliente máquina y verifica el correo.** | Quien restablece suele sospechar que otra persona tiene la contraseña, y el enlace ya demuestra que la casilla es suya. |
| **Límites de intentos sobre altas, enlaces por correo y emisión de tokens** (RNF-31 a RNF-33), además del de inicio de sesión. | Cada uno manda correos o emite credenciales: sin límite, cualquiera podría llenar una casilla ajena o generar credenciales sin tope. |
| **Con el envío de correos de desarrollo, el alta y los enlaces responden 503.** | Una cuenta que no puede recibir su enlace no se podría verificar nunca, y el enlace no puede quedar escrito en el log. |
| **Integración continua, shell del cliente web y pantallas de autenticación adelantadas**; recuperación de contraseña desplazada a la iteración 4. | Repartir la carga: las iteraciones 3 y 5 del plan original concentraban demasiado. |

---

## 10. Desarrollo del trabajo

El trabajo se organizó en cinco iteraciones de dos semanas desde el 31 de agosto, con la rama
principal protegida y revisión cruzada según la rotación del cronograma. La tabla ubica cada hito en
la iteración en que se incorporó a `main`, según el historial del repositorio, que no siempre es la
que preveía el plan.

| Iteración | Fechas | Incorporado a `main` |
|---|---|---|
| 1 · Fundaciones | 31/08 – 13/09 | Repositorio, entorno de pruebas con PostgreSQL real, migraciones V1 y V2 con RLS, contexto del hogar por transacción |
| 2 · Esquema y documentación | 14/09 – 27/09 | Entidades, catálogo inicial, primeras pruebas de aislamiento, baja lógica y esquema definitivo, requerimientos, módulos e historias de usuario; 2.ª entrega |
| 3 · Identidad y hogares | 28/09 – 11/10 | Modelo de datos, casos de uso, contratos de la API, inicio de sesión, integración continua y primer despliegue de la API |
| 4 · Gastos, categorización e integración | 12/10 – 25/10 | Sistema de diseño del cliente web, errores estandarizados, límites de intentos, hogares e invitaciones, pruebas de aislamiento de identidad y hogares, autenticación por token de cliente máquina, alta de cuenta, recuperación de contraseña |
| 5 · Gráficos y cierre de la interfaz | 26/10 – 08/11 | Trazabilidad, verificación de correo, emisión de tokens, análisis, pantallas de autenticación, gastos, categorización, captura rápida, pantallas de recuperación de contraseña, tablero, batería de aislamiento por punto de acceso |

> **A completar antes de la entrega:** una reflexión del equipo sobre el plan contra lo real. Del
> historial surge que parte de la iteración 3 (alta de cuenta, verificación de correo) y de la 4
> (gastos, categorización, análisis) se incorporó recién entre el 24 y el 29 de octubre, y que la
> semana de reserva del cronograma absorbe ese corrimiento.

---

## 11. Limitaciones y trabajo futuro

- **Rendimiento sin medir.** RNF-10 y RNF-11 piden una medición en el entorno desplegado con datos
  cargados.
- **Una sola instancia.** Los límites de intentos cuentan en memoria: con más de una instancia de la
  API, cada una contaría por su lado.
- **Arranque en frío.** El plan gratuito de App Service detiene la aplicación sin uso, y el primer
  pedido siguiente tarda entre 15 y 30 segundos.
- **Pruebas de componentes.** El cliente web prueba su lógica, pero no tiene una librería para probar
  las pantallas: los recorridos se verificaron a mano.
- **Evolución.** Las funcionalidades declaradas fuera de alcance en la propuesta (§3.3) siguen siendo
  el camino natural: la sesión propia permite sumar proveedores externos de identidad sin rehacer el
  mecanismo.

> **A completar antes de la entrega:** lo que quede pendiente del alcance al cierre.

---

## 12. Criterios de éxito

La propuesta definió cuatro criterios para considerar exitoso el proyecto al cierre del cursado:

| Criterio | Estado |
|---|---|
| La aplicación está desplegada y permite el recorrido completo: registro, verificación, invitación de un segundo integrante, carga de gastos y consulta del análisis | *A completar*: la API está desplegada; el recorrido completo depende de las pantallas del hogar y de gastos, y se verifica en la iteración 5 (tarea 8) |
| Hay evidencia automatizada de que dos hogares no acceden a los datos del otro por ningún punto de acceso | Cumplido (§5) |
| El esquema se reconstruye íntegramente desde las migraciones, sin pasos manuales | Cumplido (§7) |
| Se entregan el informe escrito y el video explicativo | *A completar* |

---

## 13. Conclusiones

> **A completar antes de la entrega**, en equipo: qué se aprendió, qué decisiones se volverían a
> tomar y cuáles no, y cómo resultó trabajar con revisión cruzada.
