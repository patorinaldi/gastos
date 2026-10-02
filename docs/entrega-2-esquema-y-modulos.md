# TRABAJO FINAL INTEGRADOR

# Gastos

**Aplicación web para el registro y análisis de gastos compartidos en unidades de convivencia**

*Esquema de datos y definición de módulos — 2.ª Entrega · Tecnicatura Universitaria en Programación, UTN*

| | |
|---|---|
| **Integrantes** | Juan Francisco Reales · Elian Rivoira · Patricio Tomás Rinaldi |
| **Tutor/a** | Gerardo Adrián Herrera |
| **Repositorio único** | [https://github.com/patorinaldi/gastos](https://github.com/patorinaldi/gastos) |
| **Fecha** | 27 de septiembre de 2026 |

---

## 1. Contenido de la entrega

Esta entrega presenta el esquema de base de datos definitivo y el listado detallado de los módulos,
ambos documentados en el repositorio. El informe resume cada parte y remite al documento que la
desarrolla. Todos los documentos están en `/docs`, versionados junto con el código.

| Contenido | Dónde está |
|---|---|
| Esquema de base de datos | [`docs/modelo-de-datos.md`](modelo-de-datos.md): cada tabla con sus columnas, restricciones e índices, el aislamiento entre hogares y la siembra del catálogo inicial |
| Diagrama entidad-relación | [`docs/der/`](der/): código DBML y diagrama exportado |
| Migraciones | `backend/src/main/resources/db/migration/`, de la V1 a la V5 |
| Detalle de los módulos M1 a M8 | [`docs/modulos.md`](modulos.md) |
| Requerimientos | [`docs/requerimientos.md`](requerimientos.md): 43 funcionales, 28 no funcionales y 7 de dominio |
| Reglas de negocio | [`docs/reglas-de-negocio.md`](reglas-de-negocio.md): 18 reglas, con el nivel en que se hace cumplir cada una |
| Casos de uso | [`docs/casos-de-uso.md`](casos-de-uso.md): los 8 flujos críticos, con flujo principal, alternativos y de excepción |
| Historias de usuario | [`docs/historias-de-usuario.md`](historias-de-usuario.md): 20 historias con criterios de aceptación |
| Contratos de la API | `backend/src/main/java/io/github/patorinaldi/gastos/api/web/dto/` y su espejo en `frontend/src/types/api.ts` |
| Índice de la documentación | [`docs/README.md`](README.md): qué contiene cada documento y cómo leerlos |

---

## 2. Esquema de base de datos

### 2.1 Tablas

El esquema es propiedad exclusiva de las migraciones versionadas. La capa de mapeo valida al
arrancar que las entidades coinciden con él y no puede modificarlo (RN-15).

| Tabla | Contenido | Aislamiento |
|---|---|---|
| `households` | Hogar: la unidad de convivencia y de aislamiento. Puede archivarse cuando su último integrante se cambia a otro (RN-18). | Sin RLS |
| `users` | Usuario, con su correo único sin distinguir mayúsculas y el hash de su contraseña. Pertenece a un hogar. | Sin RLS |
| `categories` | Catálogo de categorías de cada hogar, con baja lógica (RN-16). | RLS |
| `category_rules` | Reglas patrón-categoría del motor de categorización. | RLS |
| `expenses` | Gastos: importe, comercio, fecha, medio de pago, responsable y categoría, con baja lógica. | RLS |
| `auth_tokens` | Tokens de verificación de correo y de restablecimiento de contraseña (RN-03). | Sin RLS |
| `household_invitations` | Códigos de invitación a un hogar, con vencimiento y registro del canje (RN-05). | Sin RLS |
| `machine_tokens` | Tokens de larga duración para clientes automatizados, revocables. | Sin RLS |

El detalle de cada tabla (columnas, restricciones, índices y el motivo de cada uno) está en
[`modelo-de-datos.md`](modelo-de-datos.md), y el diagrama entidad-relación en [`docs/der/`](der/).

### 2.2 Migraciones

| Versión | Contenido |
|---|---|
| V1 | Esquema base: hogares, usuarios, categorías, reglas y gastos, con sus restricciones de integridad. |
| V2 | Rol de aplicación restringido y políticas de aislamiento por hogar. |
| V2.5 | Índice único de correo sobre `upper(email)`, alineado con las consultas que genera la capa de acceso a datos. |
| V3 | Función que siembra el catálogo inicial de cada hogar: 10 categorías y 43 reglas. |
| V4 | Baja lógica de gastos y categorías, y un disparador que elimina las reglas de una categoría al darla de baja. |
| V5 | Cambio de hogar, archivado de hogares, tablas de acceso (tokens, invitaciones y tokens de cliente máquina), y un disparador que exige que el usuario integre el hogar al registrar gastos, tokens e invitaciones. |

El esquema se reconstruye íntegro desde las migraciones, sin pasos manuales (RNF-18). La batería de
pruebas lo verifica en cada ejecución, levantando una base PostgreSQL 17 vacía.

### 2.3 Aislamiento entre hogares

El aislamiento es el elemento central del proyecto, y se hace cumplir en el motor de base de datos
en varias capas independientes:

1. **Políticas de seguridad a nivel de fila.** `categories`, `category_rules` y `expenses` tienen
   políticas que solo muestran y solo aceptan filas del hogar activo. Están activadas con `FORCE`,
   de modo que tampoco las omite el dueño de las tablas.
2. **Rol restringido.** La aplicación se conecta con `gastos_api`, un rol sin privilegio de
   superusuario ni de omisión de políticas (RNF-04). Las migraciones corren con otro rol.
3. **Contexto por transacción.** Al comenzar cada transacción, la aplicación fija el hogar activo
   con alcance limitado a esa transacción. Al devolver la conexión al pool, el valor ya no existe,
   así que no puede filtrarse entre peticiones. Sin hogar fijado, las tablas con políticas no
   devuelven filas ni aceptan escrituras: el comportamiento por defecto es cerrado (RNF-03).
4. **Integridad referencial dentro del hogar.** La categoría de un gasto se referencia con una
   clave compuesta que incluye el hogar, y un disparador exige que el responsable de un gasto
   integre el hogar en el momento de registrarlo (RN-08). Aunque el código de la aplicación
   tuviera un error, un gasto no puede quedar apuntando a datos de otro hogar.

**Criterio sobre qué tablas llevan políticas.** Las políticas protegen los **datos de negocio**:
gastos, categorías y reglas. Las tablas de **identidad y acceso** (`users`, `households` y las de
tokens e invitaciones) quedan fuera, y las controla la aplicación. Tiene un motivo concreto: esas
tablas se consultan precisamente cuando todavía no hay un hogar activo, como al iniciar sesión, al
canjear un código de otro hogar o al autenticar una automatización. Los tokens y los códigos se
guardan solo como hash, así que una filtración de esas tablas no permite tomar cuentas ni cargar
gastos en hogares ajenos.

La contrapartida es que los **listados** de esas tablas (los integrantes de un hogar, sus
invitaciones y sus tokens de cliente máquina) filtran por hogar en la propia consulta. Por eso las
pruebas de aislamiento por punto de acceso los cubren explícitamente, igual que a los de las
tablas de negocio.

La prueba de aislamiento actual recorre las tres tablas de negocio, en lectura, alta, modificación
y baja, sin hogar activo, con un hogar ajeno y con el propio (RNF-02).

### 2.4 Reglas que sostiene el motor

Cada regla de negocio declara en qué nivel se hace cumplir: en el motor, en la aplicación o en
ambos. Las críticas se sostienen en el motor, porque una restricción del esquema alcanza a toda
escritura, incluidas las que no pasan por la aplicación. Entre ellas:

- importe estrictamente positivo con dos decimales exactos (RN-06);
- comercio obligatorio y medio de pago del conjunto cerrado (RN-07);
- referencias dentro del hogar, sin borrado en cascada (RN-08);
- nombre de categoría y patrón de regla únicos por hogar, sin distinguir mayúsculas (RN-09, RN-10);
- coherencia de la baja lógica y del archivado: una fila dada de baja siempre tiene su fecha, y
  una activa nunca la tiene (RN-16, RN-18);
- eliminación de las reglas de una categoría al darla de baja, para que el motor de categorización
  no siga asignándola (RN-16).

---

## 3. Módulos

El detalle de cada módulo (responsabilidad, puntos de entrada, contratos, decisiones de diseño y
dependencias) está en [`docs/modulos.md`](modulos.md).

| Módulo | Responsabilidad | Estado |
|---|---|---|
| M1 Identidad y acceso | Registro, verificación de correo, inicio de sesión, recuperación de contraseña y tokens de cliente máquina. | Esquema listo. Implementación en la próxima iteración. |
| M2 Hogares | Invitaciones, integrantes, ajustes del hogar y cambio de hogar. | Esquema listo. Implementación en la próxima iteración. |
| M3 Gastos | Alta, edición, baja lógica y listado filtrable de gastos. | Entidades, repositorios y baja lógica implementados. |
| M4 Categorización | Catálogo, motor de reglas, bandeja de no categorizados y reclasificación. | Catálogo inicial y limpieza de reglas implementados. |
| M5 Análisis | Totales, agregaciones y evolución mensual. | Pendiente. |
| M6 API de integración | Captura rápida desde clientes automatizados. | Esquema listo. |
| M7 Interfaz de usuario | Cliente web. | Esqueleto implementado. |
| M8 Plataforma y calidad | Esquema, aislamiento, pruebas, integración continua y despliegue. | Migraciones, aislamiento y pruebas implementados. |

Los contratos de la API de M1 a M6 ya están definidos: cada petición y respuesta es un tipo del
backend con sus validaciones, y el cliente web tiene su espejo tipado. Así la implementación de
cada módulo en la próxima iteración parte de una interfaz acordada entre backend y frontend.

---

## 4. Estado de avance

**Implementado y verificado con pruebas automatizadas:**

- Esquema completo en cinco migraciones, aplicadas automáticamente al arrancar (RF-39).
- Aislamiento entre hogares: políticas, rol restringido, contexto por transacción y prueba
  sistemática de aislamiento.
- Entidades y repositorios validados contra el esquema al arrancar (RNF-19).
- Catálogo inicial sembrado por hogar (RF-19).
- Baja lógica de gastos y categorías.
- Cambio de hogar conservando los gastos en el hogar anterior, y archivado de hogares.
- Disparadores que sostienen en el motor la limpieza de reglas de una categoría dada de baja y la
  pertenencia del usuario al hogar en cada gasto, token e invitación.
- Contratos tipados de la API de M1 a M6, con pruebas que fijan su forma en JSON: importes como
  cadena decimal y nunca como número, medios de pago en español y fechas en ISO-8601.
- Emisor de correo con implementación de desarrollo.
- Punto de verificación de estado sin autenticación ni detalle interno (RF-41).
- Esqueleto del cliente web.

La batería tiene **79 pruebas** y corre contra PostgreSQL 17 real en contenedor, el mismo motor y
versión que producción (RNF-28). Las pruebas se conectan con el rol restringido de la aplicación,
de modo que las políticas de aislamiento se ejercitan de verdad.

**Forma de trabajo.** Cada tarea se desarrolla en su propia rama y se incorpora mediante pull
request con revisión de otro integrante, y de los otros dos cuando toca migraciones, aislamiento o
autenticación. A la fecha, **23 pull requests** incorporados. Las revisiones llevaron a cambios de diseño
concretos, que se describen en la sección 5.

---

## 5. Cambios respecto de la propuesta

El diseño se ajustó en algunos puntos respecto de la 1.ª entrega. Ninguno cambia el alcance: todos
surgen de profundizar el modelo o de las observaciones recibidas.

| Cambio | Motivo |
|---|---|
| **Baja lógica de gastos y categorías** en lugar de borrado físico (RN-16). | En una aplicación de dinero, borrar destruye el historial sobre el que después se pide explicación. Un gasto dado de baja desaparece de listados y análisis. Una categoría dada de baja deja de ofrecerse, pero se sigue viendo en el historial y en el análisis de los gastos que la usaban, y sus reglas se eliminan solas. |
| **Cambio de hogar**, con los gastos del que se va quedando en el hogar anterior, y **archivado** del hogar cuando se va su último integrante (RN-18). | La propuesta no definía qué pasaba con el hogar propio de alguien que se une a otro. El gasto compartido pertenece al hogar, no a quien lo cargó. |
| **Precedencia entre reglas de categorización**: gana la de patrón más largo (RN-17). | Cuando varias reglas coinciden con un comercio, el resultado tiene que ser determinista y explicable, como promete la propuesta. |
| **Tokens y códigos guardados solo como hash.** | Una filtración de la base no debe permitir tomar cuentas ni cargar gastos en hogares ajenos. |
| **Las tablas de identidad y acceso quedan fuera de las políticas de aislamiento**, y las controla la aplicación. | Se consultan antes de que exista un hogar activo. Las políticas protegen todos los datos de negocio, que es lo que la propuesta compromete. Ver §2.3. |
| **Responsable del gasto garantizado por un disparador** en lugar de una clave compuesta. | La clave compuesta impedía cambiarse de hogar a quien tuviera gastos. El disparador conserva la garantía al registrar sin esa restricción. |

---

## 6. Respuesta a las observaciones recibidas

### 6.1 Sobre la propuesta

| Observación | Respuesta |
|---|---|
| Falta cuantificar el usuario objetivo. | El usuario objetivo es un hogar de 2 a 4 integrantes, que registra entre 60 y 150 gastos por mes entre todos: entre 700 y 1.800 gastos por año. El volumen de referencia de los requisitos de rendimiento (10.000 gastos, RNF-10 y RNF-11) cubre así más de cinco años de uso de un hogar. |
| La API de integración (M6) puede quedar sobredimensionada. | Se mantiene. Su esquema ya está resuelto (tokens de cliente máquina en la V5), y la captura rápida es la forma más directa de bajar el costo de registrar un gasto, que es el problema central que el proyecto ataca. Si el cronograma se complica, es el primer módulo a recortar: la emisión y revocación de tokens (RF-07) ya tiene prioridad "Debería". |
| Falta una sección de casos de uso. | Se agregó [`casos-de-uso.md`](casos-de-uso.md), con los 8 flujos críticos (flujo principal, alternativos y de excepción), y [`historias-de-usuario.md`](historias-de-usuario.md), con 20 historias con criterios de aceptación en formato dado-cuando-entonces. Ambos están trazados contra requisitos y reglas. |

### 6.2 Sobre las reglas de negocio

| Observación | Respuesta |
|---|---|
| No se define qué pasa si dos patrones coinciden con el mismo comercio. | RN-17: gana el patrón más largo; a igual longitud, la regla más antigua. No hace falta una columna de prioridad. |
| Falta una política de modificación de categorías y su efecto sobre los gastos históricos. | RN-16 cubre las dos modificaciones. La baja es lógica: los gastos conservan su categoría, en el historial y en el análisis, y sus reglas se eliminan. Renombrar alcanza a todo el historial, porque los gastos referencian la categoría por identificador. Para separar los gastos viejos de los nuevos, se da de baja la categoría y se crea otra. |
| Falta definir auditoría. | Un registro de auditoría dedicado queda fuera de alcance, y así figura en `requerimientos.md` (§5). El esquema ya conserva la información mínima para reconstruir qué pasó: fechas de alta, modificación, baja y archivado, el responsable de cada gasto, y quién creó y quién canjeó cada invitación. |

### 6.3 Sobre los requerimientos

| Observación | Respuesta |
|---|---|
| RF-16 no aclara si la eliminación es lógica o física. | RF-16 se reformuló como baja lógica, respaldada por RN-16 y la migración V4. |
| Falta exportación de datos. | Queda fuera de alcance, y así figura en `requerimientos.md` (§5). No afecta el modelo de datos, así que puede sumarse como evolución posterior. |
| No queda claro si pueden agregarse medios de pago. | El conjunto es cerrado por decisión de dominio (RD-05) y lo sostiene el motor (RN-07). Agregar un medio de pago requiere una migración. |
| Las métricas de rendimiento pueden ser difíciles de demostrar. | Se mantienen, con un plan de medición concreto: cargar 10.000 gastos en un hogar del entorno desplegado y medir el percentil 95 del tiempo de respuesta de la captura rápida (RNF-10) y de las consultas de análisis (RNF-11). La evidencia se presentará en el informe final. |

### 6.4 Sobre el diagrama entidad-relación

| Observación | Respuesta |
|---|---|
| Falta la tabla de invitaciones. | `household_invitations`, migración V5. |
| Falta la tabla de tokens de verificación y restablecimiento. | `auth_tokens`, migración V5: una sola tabla para ambos usos, como pide RN-03. |
| Falta el soporte para clientes máquina. | `machine_tokens`, migración V5. |
| Las reglas podrían requerir prioridad. | RN-17 resuelve la precedencia sin columna adicional. |
| Falta baja lógica en categorías. | `active` y `deleted_at` en `categories` y `expenses`, migración V4. |
| `users` podría necesitar `updated_at`. | Se incorporará junto con el módulo de identidad, cuando existan las operaciones que modifican al usuario (verificar el correo, cambiar el nombre o la contraseña). Hoy ninguna operación la actualizaría. |
| Entidades futuras: presupuestos, auditoría, notificaciones. | Presupuestos y alertas ya figuran fuera de alcance en la propuesta. Auditoría: ver §6.2. |

### 6.5 Sobre el uso del repositorio

| Observación | Respuesta |
|---|---|
| Los archivos tienen un solo commit y no muestran evolución. | Desde entonces los cambios se incorporan en commits parciales, cada uno con su motivo, y las correcciones de cada revisión van en commits propios. Por ejemplo, las historias de usuario y la definición de módulos muestran su evolución a lo largo de las revisiones. |

---

## 7. Próximos pasos

Según el plan de trabajo, la próxima iteración aborda los módulos de identidad y hogares y el
primer despliegue del backend. Además de los requisitos de esos módulos, quedan comprometidos por
RN-18:

- resolver el hogar de cada petición desde el usuario, y no desde un atributo del token de sesión,
  para que un token emitido antes de un cambio de hogar no siga operando sobre el hogar anterior;
- validar que el hogar de un token de cliente máquina sea el hogar actual de su dueño;
- rechazar operaciones sobre hogares archivados.

Y quedan registrados, para resolver junto con esos módulos, los puntos que surgieron de la revisión
del esquema:

- evaluar si invitaciones y tokens de cliente máquina pasan a tener políticas de aislamiento, con
  funciones dedicadas para la búsqueda por hash, o si sus listados se cubren con las pruebas de
  aislamiento por punto de acceso;
- revocar en el motor los tokens de cliente máquina de quien se cambia de hogar, además de hacerlo
  en la aplicación;
- sostener en el motor que un código de invitación se canjea una sola vez;
- en el análisis, unir los gastos con sus responsables solo por identificador de usuario: los
  gastos anteriores de quien se cambió de hogar apuntan a un usuario que hoy integra otro;
- purgar periódicamente los tokens de verificación y restablecimiento vencidos.
