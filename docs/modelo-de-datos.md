# Modelo de datos

Proyecto Gastos. Trabajo Final Integrador, TUP UTN.

Descripción del esquema de base de datos: cada tabla, sus restricciones y las políticas de
aislamiento. El diagrama entidad-relación está en [der/](der/), con el código DBML en
[DER-gastos-code.md](der/DER-gastos-code.md) y el diagrama exportado en
[DER-gastos.svg](der/DER-gastos.svg).

Este documento cubre lo que el DER no puede expresar. DBML no modela roles, funciones, políticas
de seguridad a nivel de fila ni disparadores, y esas son las piezas centrales del diseño.

Los identificadores `RN-xx` remiten a [reglas-de-negocio.md](reglas-de-negocio.md) y los `RD-xx` a
[requerimientos.md](requerimientos.md).

## Principios del esquema

1. **El esquema pertenece a las migraciones** (RN-15). La capa de mapeo se configura en modo
   validación: comprueba que el modelo de clases coincida con el esquema, sin poder crearlo ni
   modificarlo. Si divergen, la aplicación falla al arrancar.
2. **Todo dato de negocio pertenece a un hogar** (RD-03). Cada tabla de negocio lleva
   `household_id` con clave foránea directa a `households`.
3. **Defensa en capas.** El aislamiento se hace cumplir dos veces: con claves foráneas compuestas
   que impiden referencias entre hogares, y con políticas de seguridad a nivel de fila que filtran
   toda consulta. Si una fallara, la otra sigue en pie.
4. **Nada se borra en cascada.** Todas las claves foráneas son `on delete restrict`. Eliminar una
   categoría no puede llevarse por delante el historial de gastos.
5. **Los gastos y las categorías no se borran** (RN-16). Se marcan como dados de baja y se
   conservan. El hecho económico registrado es la materia prima del análisis, y el análisis se
   consulta sobre períodos ya cerrados.
6. **Identificadores opacos.** Las claves primarias son UUID generados, no enteros secuenciales.
   No revelan volumen ni permiten recorrer registros por tanteo.

## Migraciones

| Versión | Nombre | Contenido |
|---|---|---|
| V1 | `core_schema` | Las cinco tablas de negocio, sus restricciones e índices. |
| V2 | `api_role_and_rls` | Rol de aplicación restringido, función de contexto y políticas de aislamiento. |
| V2.5 | `users_email_index_upper` | Corrección del índice de correo para alinearlo con las consultas de inicio de sesión. |
| V3 | `household_defaults` | Función que siembra el catálogo inicial de categorías y reglas de un hogar. |
| V4 | `soft_delete` | Marca de baja en gastos y categorías, índice único parcial y disparador de limpieza de reglas. |

Este documento describe el esquema hasta la V4, que es lo que está en la rama principal. Las tablas
de acceso que necesitan M1 y M2 —tokens de verificación y restablecimiento, invitaciones y tokens
de cliente máquina— están en revisión y se documentan al incorporarse.

---

## Tablas

### `households`, hogares

La unidad de convivencia y, a la vez, la unidad de aislamiento del sistema. Todo lo demás depende
de esta tabla.

| Columna | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `uuid` | PK, por defecto generado | Identificador del hogar. Viaja dentro del token de sesión. |
| `name` | `text` | No nulo, no vacío | Nombre visible, elegido por quien crea el hogar. |
| `created_at` | `timestamptz` | No nulo, por defecto ahora | Momento del alta. |

Restricciones: `households_name_not_blank` impide nombres formados solo por espacios.

**Sin políticas de aislamiento.** Es intencional. El hogar se resuelve a partir del token antes de
que exista contexto, de modo que la propia consulta que lo determina no puede depender de él. El
acceso queda acotado porque la aplicación solo consulta el hogar del usuario autenticado.

### `users`, usuarios

Las personas que integran un hogar. Un usuario pertenece a exactamente un hogar (RN-04).

| Columna | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `uuid` | PK, por defecto generado | Identificador del usuario. |
| `household_id` | `uuid` | No nulo, FK a `households(id)` restrict | Hogar al que pertenece. |
| `email` | `varchar(320)` | No nulo, no vacío, único sin distinguir mayúsculas | Credencial de acceso y canal de verificación. |
| `password_hash` | `text` | No nulo, no vacío | Hash bcrypt. Nunca la contraseña en claro. |
| `email_verified` | `boolean` | No nulo, por defecto `false` | Mientras sea falso, la cuenta no puede iniciar sesión (RN-02). |
| `name` | `varchar(200)` | No nulo, no vacío | Nombre visible en los listados y en el análisis por integrante. |
| `created_at` | `timestamptz` | No nulo, por defecto ahora | Momento del alta. |

Índices:

- `users_email_key`, único sobre `upper(email)`. Hace cumplir RN-01 y sostiene la búsqueda del
  inicio de sesión.
- `users_household_idx`, sobre `household_id`, para listar integrantes.
- `users_household_id_id_key`, único sobre `(household_id, id)`. No aporta unicidad nueva, porque
  `id` ya es clave primaria. Existe para ser el destino de las claves foráneas compuestas de
  `expenses`.

**Por qué `upper(email)` y no `lower(email)`.** La V1 creó el índice sobre `lower(email)`, pero
Spring Data JPA genera `upper(...)` para el modificador `IgnoreCase` en las consultas derivadas del
nombre del método. Con `lower()`, las dos consultas del inicio de sesión no podían usar el índice y
recorrían la tabla entera. La V2.5 lo alineó.

**Sin políticas de aislamiento**, por el mismo motivo que `households`. El inicio de sesión
resuelve el correo antes de que exista contexto de hogar. Se cierra con el módulo de identidad.

### `categories`, categorías

El catálogo de clasificación de cada hogar. Cada hogar tiene el suyo y lo edita libremente.

| Columna | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `uuid` | PK, por defecto generado | Identificador de la categoría. |
| `household_id` | `uuid` | No nulo, FK a `households(id)` restrict | Hogar dueño. |
| `name` | `varchar(200)` | No nulo, no vacío | Nombre visible, por ejemplo `Supermercado`. |
| `created_at` | `timestamptz` | No nulo, por defecto ahora | Momento del alta. |
| `updated_at` | `timestamptz` | No nulo, por defecto ahora | Última modificación. Agregada en la V4. |
| `active` | `boolean` | No nulo, por defecto `true` | Marca de baja lógica (RN-16). |
| `deleted_at` | `timestamptz` | Admite nulo | Momento de la baja. Nulo mientras la categoría está activa. |

Restricciones e índices:

- `categories_active_deleted_at_consistent`, `check (active = (deleted_at is null))`. Impide que
  las dos columnas de la baja se contradigan.
- `categories_household_name_key`, único **parcial** sobre `(household_id, lower(name))`, con la
  condición `where active`. Hace cumplir RN-09 entre las categorías activas: `Comida` y `comida`
  no pueden coexistir partiendo el análisis en dos. Como es parcial, una categoría dada de baja
  deja libre su nombre y el hogar puede volver a crearlo.
- `categories_household_id_id_key`, único sobre `(household_id, id)`, destino de las claves
  foráneas compuestas de `category_rules` y `expenses`.

Disparador `categories_delete_rules_on_deactivate`: cuando una categoría pasa de activa a inactiva,
elimina físicamente sus reglas (ver [Baja lógica](#baja-lógica-de-gastos-y-categorías)).

Con políticas de aislamiento activas.

### `category_rules`, reglas de categorización

Las reglas patrón-categoría del motor. Asocian un fragmento de texto del nombre del comercio con
una categoría.

| Columna | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `uuid` | PK, por defecto generado | Identificador de la regla. |
| `household_id` | `uuid` | No nulo, FK a `households(id)` restrict | Hogar dueño. |
| `pattern` | `varchar(200)` | No nulo, no vacío | Patrón a buscar en el comercio. En minúscula y sin tildes. |
| `category_id` | `uuid` | No nulo, FK compuesta | Categoría que se asigna al coincidir. |
| `created_at` | `timestamptz` | No nulo, por defecto ahora | Momento del alta. |

Restricciones e índices:

- `category_rules_category_fk`, clave foránea compuesta `(household_id, category_id)` contra
  `categories (household_id, id)`. Impide que una regla apunte a una categoría de otro hogar.
- `category_rules_household_pattern_key`, único sobre `(household_id, lower(pattern))`. Hace
  cumplir RN-10. Sin esta restricción, dos reglas con el mismo patrón y distinta categoría harían
  que el resultado dependiera del orden de evaluación.
- `category_rules_household_category_idx`, sobre `(household_id, category_id)`, para listar las
  reglas de una categoría.

**Sin marca de baja.** Es la única tabla de negocio que se borra físicamente. Una regla no es un
hecho económico sino una preferencia de clasificación, y eliminarla no altera ningún gasto ya
clasificado (RN-16). El índice único es total, no parcial, por el mismo motivo: un patrón eliminado
queda disponible de inmediato.

Con políticas de aislamiento activas.

### `expenses`, gastos

El hecho económico registrado. Es la tabla central del sistema y la de mayor volumen.

| Columna | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `uuid` | PK, por defecto generado | Identificador del gasto. |
| `household_id` | `uuid` | No nulo, FK a `households(id)` restrict | Hogar dueño. |
| `owner_id` | `uuid` | No nulo, FK compuesta | Integrante que registró o realizó el gasto. |
| `category_id` | `uuid` | Admite nulo, FK compuesta | Categoría asignada. Nulo si ninguna regla coincidió. |
| `merchant` | `varchar(200)` | No nulo, no vacío | Comercio. Única entrada del motor de categorización. |
| `amount` | `numeric(12,2)` | No nulo, mayor que cero | Importe. Decimal exacto, nunca punto flotante (RD-01). |
| `expense_date` | `date` | No nulo | Fecha del gasto. Por defecto, la del día. |
| `payment_method` | `text` | No nulo, valor de un conjunto cerrado | `Efectivo`, `Tarjeta` o `Transferencia`. |
| `created_at` | `timestamptz` | No nulo, por defecto ahora | Momento del registro. |
| `updated_at` | `timestamptz` | No nulo, por defecto ahora | Última modificación. |
| `active` | `boolean` | No nulo, por defecto `true` | Marca de baja lógica (RN-16). |
| `deleted_at` | `timestamptz` | Admite nulo | Momento de la baja. Nulo mientras el gasto está activo. |

Restricciones:

- `expenses_owner_fk`, clave foránea compuesta `(household_id, owner_id)` contra
  `users (household_id, id)`. El responsable de un gasto debe integrar el mismo hogar (RN-08).
- `expenses_category_fk`, clave foránea compuesta `(household_id, category_id)` contra
  `categories (household_id, id)`. Es `MATCH SIMPLE`, por lo que no se evalúa mientras
  `category_id` sea nulo. Así convive la integridad referencial con la bandeja de no categorizados
  (RN-12).
- `expenses_amount_positive`, `amount > 0` (RN-06).
- `expenses_merchant_not_blank`, comercio no vacío (RN-07).
- `expenses_payment_method_valid`, medio de pago dentro del conjunto cerrado (RN-07, RD-05).
- `expenses_active_deleted_at_consistent`, `check (active = (deleted_at is null))`.

Índices:

| Índice | Columnas | Consulta que sostiene |
|---|---|---|
| `expenses_household_date_idx` | `(household_id, expense_date desc)` | Listado paginado por período, que es la consulta más frecuente. |
| `expenses_household_category_idx` | `(household_id, category_id)` | Filtro por categoría y agregación del análisis. |
| `expenses_household_owner_idx` | `(household_id, owner_id)` | Filtro por integrante y agregación por persona. |

Ninguno incluye `active`, aunque desde la V4 toda consulta de la aplicación lo filtra. Con el
volumen esperado no se justifica: los gastos dados de baja son una fracción mínima de la tabla. Si
el listado se degradara, la alternativa es volverlos parciales con `where active`.

Con políticas de aislamiento activas.

**Sobre el medio de pago.** La columna es `text` con una restricción de valores admitidos en
español, no un tipo enumerado de la base. La aplicación lo mapea a un enumerado del código cuyas
constantes están en inglés, con un conversor que traduce en ambos sentidos. Así se respeta la
convención de identificadores en inglés y valores del dominio en español (RD-06).

---

## Baja lógica de gastos y categorías

La V4 agrega la marca de baja a `expenses` y `categories`. La regla completa, con sus motivos, es
RN-16; acá va cómo queda implementada en el esquema.

**Tres columnas.** `active` es la marca que filtra la aplicación y la condición del índice parcial.
`deleted_at` registra cuándo se dio de baja. `updated_at` es la última modificación, sea o no la
baja, y por eso no alcanza como fecha de baja: cualquier edición posterior la pisa. Como `active` y
`deleted_at` dicen lo mismo de dos maneras, un `check` en cada tabla impide que se contradigan.

**El filtrado es asimétrico, y es deliberado.** Un gasto dado de baja desaparece de todo, incluidos
los totales. Una categoría dada de baja deja de ofrecerse, pero los gastos que ya la tenían la
conservan: si también desapareciera, un gasto de agosto quedaría sin categoría —o fuera del total—
por una baja hecha en septiembre. En el mapeo, esa asimetría es una restricción de lectura presente
en `Expense` y ausente en `Category`; las categorías activas se filtran con consultas explícitas
solo donde se ofrecen categorías.

**Las reglas de una categoría dada de baja se eliminan.** No con código de aplicación, sino con el
disparador `categories_delete_rules_on_deactivate`, para que valga por cualquier camino que dé de
baja la categoría. Si quedaran, el motor seguiría asignando gastos a una categoría que ya no se
ofrece, y el patrón seguiría ocupando su lugar en el índice único de RN-10, de modo que no se
podría reasignar a otra categoría. El disparador ejecuta con los permisos de quien escribe, así que
la eliminación pasa por las mismas políticas de aislamiento que cualquier otra escritura.

**Qué garantiza el motor y qué no.** El motor sostiene la coherencia entre `active` y `deleted_at`,
la unicidad entre filas activas y la eliminación de las reglas. El filtrado de las filas dadas de
baja, en cambio, lo aplica la capa de mapeo: una consulta nativa o por `JdbcTemplate` ve las filas
dadas de baja y tiene que agregar `where active`. Vale sobre todo para las agregaciones del
análisis, que se resuelven con consultas que agrupan en la base. Por el mismo motivo, una sentencia
`delete` directa sí borra la fila: la baja lógica es el camino del mapeo, no una prohibición del
motor.

---

## Aislamiento entre hogares

Es la pieza central del diseño y el entregable de seguridad más importante del proyecto. Se apoya
en tres mecanismos que se refuerzan entre sí.

### 1. El rol de aplicación restringido

La aplicación no se conecta con el rol que ejecuta las migraciones. Se conecta con `gastos_api`,
creado en la V2 con privilegios explícitamente recortados:

```sql
alter role gastos_api nosuperuser nocreatedb nocreaterole nobypassrls noreplication;
grant select, insert, update, delete
    on households, users, categories, category_rules, expenses
    to gastos_api;
```

`nobypassrls` es la cláusula que decide. Un rol con ese privilegio ignora las políticas por
completo. Sin él, aunque el código de la aplicación tuviera un error y olvidara propagar el
contexto, la conexión en sí misma es incapaz de ver o modificar datos de otro hogar. El aislamiento
deja de depender de la corrección del código.

### 2. La variable de contexto y su función de lectura

El hogar activo viaja en una variable de sesión de PostgreSQL, `app.current_household`, que la
aplicación fija al inicio de cada transacción con alcance transaccional (`SET LOCAL`). El alcance
importa. Sin él, el valor sobreviviría al devolver la conexión al pool y se filtraría a la petición
siguiente, que podría ser de otro usuario.

```sql
create function app_current_household() returns uuid
    language sql stable
as $$
    select nullif(current_setting('app.current_household', true), '')::uuid
$$;
```

El segundo argumento `true` de `current_setting` hace que devuelva nulo en lugar de fallar cuando
la variable no está definida, y `nullif` evita que una cadena vacía rompa la conversión a UUID.

Del lado de la aplicación, un escucha de transacciones lee el hogar del hilo actual y lo fija en
`afterBegin`. Si no hay hogar en contexto, no fija nada. La transacción queda sin hogar y, por las
políticas de abajo, no ve ninguna fila.

### 3. Las políticas de seguridad a nivel de fila

Se aplican sobre las tres tablas de negocio:

```sql
alter table expenses enable row level security;
alter table expenses force  row level security;

create policy expenses_household_isolation on expenses
    using      (household_id = app_current_household())
    with check (household_id = app_current_household());
```

| Cláusula | Qué controla |
|---|---|
| `using` | Qué filas son visibles. Alcanza a `select`, y también a las filas candidatas de `update` y `delete`. |
| `with check` | Qué filas se pueden escribir. Alcanza a `insert` y al resultado de `update`. |

Hacen falta las dos. Solo con `using`, un usuario no podría leer filas ajenas pero sí podría
insertar filas atribuidas a otro hogar.

`force row level security` es igualmente necesario. Por defecto, las políticas no se aplican al
dueño de la tabla. Sin esa cláusula, el rol que corre las migraciones vería todo.

Las columnas que agregó la V4 no necesitaron políticas nuevas: son columnas más de tablas que ya
están aisladas, y los permisos de `gastos_api` se otorgaron sobre la tabla.

### La propiedad que se obtiene: fallar cerrado

Si el contexto no se establece, `app_current_household()` devuelve nulo, la comparación
`household_id = null` no da verdadera para ninguna fila, y las consultas devuelven cero filas.

Es lo contrario de lo que ocurre con el filtrado manual. Una condición `where household_id = ?`
olvidada devuelve todas las filas de todos los hogares. Acá, un error se manifiesta como ausencia
de datos, que es visible e inofensiva, y nunca como acceso a datos ajenos.

### Verificación

`HouseholdIsolationTest` cubre las tres tablas por lectura, alta, modificación y baja, en tres
contextos: sin hogar activo, con el hogar ajeno y con el propio. Incluye el escenario explícito de
la propuesta, donde un integrante del hogar A no alcanza datos del hogar B ni conociendo sus
identificadores exactos.

Las pruebas se ejecutan con la aplicación conectada como `gastos_api`, de modo que atraviesan las
políticas reales y no las esquivan con un rol privilegiado. Se verificó además que las pruebas
fallan si se desactivan las políticas: al neutralizarlas, los casos sensibles al aislamiento fallan
y los controles positivos siguen pasando, que es lo esperado.

Lo que sostiene la base por su cuenta tiene sus propias pruebas, que escriben con SQL directo en
lugar de pasar por las entidades: `SoftDeleteSchemaTest` para el `check` de coherencia, el índice
parcial y el disparador de reglas, y `SoftDeleteRepositoryTest` para el comportamiento visto desde
los repositorios.

---

## Siembra del catálogo inicial

La V3 no inserta filas. Define la función `seed_household_defaults()`, que la aplicación invoca en
la misma transacción que crea el hogar (RN-11). El diseño tiene tres decisiones deliberadas:

1. **No recibe el hogar por parámetro.** Siembra el de `app.current_household`, así que no puede
   apuntar a un hogar ajeno aunque se la invoque mal.
2. **Ejecuta con los permisos de quien la llama** (`SECURITY INVOKER`, el comportamiento por
   defecto). Las políticas de aislamiento le aplican igual que a cualquier otra escritura.
3. **Falla si se la invoca dos veces** sobre el mismo hogar, por los índices únicos de nombre y
   patrón. Es un error del llamador y no algo que la función deba absorber en silencio.

Siembra 10 categorías y 43 reglas de comercios habituales en Argentina. Dos categorías, Ropa y
Educación, se siembran sin reglas. No hay patrones de comercio confiables para ellas y quedan en el
catálogo para asignarlas a mano desde la bandeja.

Los patrones evitan palabras cortas o comunes que coincidirían con comercios no relacionados. Por
eso no se incluyen `dia`, `disco`, `personal` ni `claro`.
