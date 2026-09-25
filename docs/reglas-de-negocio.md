# Reglas de negocio

Proyecto Gastos. Trabajo Final Integrador, TUP UTN.

Las reglas de negocio son las restricciones del dominio que el sistema debe hacer cumplir siempre,
con independencia de la interfaz desde la que se opere. Se distinguen de los requisitos funcionales
de [requerimientos.md](requerimientos.md) en que no describen una función sino una condición que
ninguna función puede violar.

## Dónde se hace cumplir cada regla

El proyecto toma una decisión transversal. Las reglas críticas se hacen cumplir en el motor de base
de datos, no solamente en el código de la aplicación. La diferencia importa. Una validación
olvidada en una rama del código es una regla incumplida, mientras que una restricción del motor
alcanza a toda escritura, incluidas las de mantenimiento manual.

| Nivel | Significado |
|---|---|
| Motor | Restricción declarada en el esquema (`check`, índice único, clave foránea, política RLS). Imposible de violar. |
| Aplicación | La valida la capa de servicio. El motor no puede expresarla. |
| Ambos | La valida la aplicación para dar un mensaje útil, y la respalda el motor para que no dependa de ella. |

---

## Identidad y acceso

### RN-01. El correo electrónico identifica unívocamente a un usuario

Dos usuarios no pueden registrarse con el mismo correo, sin distinguir mayúsculas de minúsculas.
`Ana@ejemplo.com` y `ana@ejemplo.com` son la misma identidad.

**Nivel.** Ambos. Índice único `users_email_key` sobre `upper(email)`, más una validación previa en
el alta para responder con un mensaje claro en lugar de un error de integridad.

**Motivo.** El correo es la credencial de inicio de sesión y el canal de verificación y de
recuperación. Si admitiera duplicados por diferencia de capitalización, dos cuentas competirían por
el mismo buzón.

**Nota de implementación.** El índice está sobre `upper(email)` y no sobre `lower(email)` porque
Spring Data JPA genera `upper(...)` para el modificador `IgnoreCase` en consultas derivadas del
nombre del método. Con `lower()`, las consultas de inicio de sesión no podían usar el índice y
recorrían la tabla entera. Lo corrigió la migración V2.5.

### RN-02. Una cuenta sin correo verificado no puede operar

Hasta que el usuario confirme su correo, la cuenta existe pero no puede iniciar sesión.

**Nivel.** Aplicación. `users.email_verified` arranca en `false` por defecto en el esquema, y el
servicio de autenticación rechaza el inicio de sesión mientras siga en ese estado.

**Motivo.** Impide registrar cuentas con correos ajenos o inexistentes, y garantiza que el canal de
recuperación de contraseña es alcanzable por la persona titular.

### RN-03. Los tokens de verificación y de restablecimiento son de un solo uso y vencen

Un token queda invalidado al usarse, y en todos los casos a las 24 horas de emitido.

**Nivel.** Aplicación.

**Motivo.** Acota la ventana en la que un token filtrado, por ejemplo desde el historial del
navegador o desde un correo reenviado, sigue sirviendo para tomar el control de una cuenta.

**Diseño.** Una única tabla de tokens cubre ambos usos, porque comparten ciclo de vida.

### RN-11. El alta de un usuario crea su hogar y su catálogo inicial, todo o nada

Registrar un usuario implica, en una sola transacción, crear el usuario, crear su hogar, asociarlos
y sembrar el catálogo inicial de categorías y reglas de ese hogar.

**Nivel.** Ambos. Transacción de la aplicación, con la siembra resuelta por la función
`seed_household_defaults()` de la migración V3.

**Motivo.** Un usuario sin hogar no puede registrar ningún gasto, y un hogar sin catálogo obligaría
a clasificar a mano desde el primer movimiento. Si cualquiera de los pasos falla, no debe quedar
una cuenta a medio crear.

**Detalle.** `seed_household_defaults()` siembra el hogar que esté fijado en el contexto de la
transacción y no recibe el hogar por parámetro, de modo que no puede sembrar un hogar ajeno.
Ejecuta con los permisos de quien la llama, así que las políticas de aislamiento le aplican igual
que a cualquier otra escritura.

---

## Hogares

### RN-04. Un usuario pertenece a exactamente un hogar

**Nivel.** Motor. `users.household_id` es obligatorio y referencia a `households`.

**Motivo.** En el dominio de las unidades de convivencia una persona comparte economía con un solo
grupo. Admitir varios obligaría a resolver la noción de hogar activo en cada operación, sin
beneficio actual. Ver RD-04.

### RN-05. Un código de invitación pertenece a un hogar, se canjea una sola vez y vence

**Nivel.** Aplicación.

**Motivo.** El código circula por canales que el sistema no controla, como mensajería o papel. Que
sea de un solo uso evita que quien lo reenvíe incorpore gente no prevista, y el vencimiento limita
el daño de un código olvidado en una conversación.

---

## Gastos

### RN-06. Todo importe es estrictamente mayor que cero, con dos decimales exactos

**Nivel.** Ambos. `check (amount > 0)` y tipo `numeric(12,2)`.

**Motivo.** Un importe nulo o negativo no representa un hecho económico registrable. Las
devoluciones y notas de crédito están fuera de alcance. La precisión exacta es obligatoria porque
el punto flotante introduce diferencias de centavos que, acumuladas en las agregaciones del módulo
de análisis, producen totales que no cierran. Ver RD-01.

### RN-07. Un gasto requiere comercio y medio de pago válidos

El comercio no puede quedar vacío, y el medio de pago debe ser `Efectivo`, `Tarjeta` o
`Transferencia`.

**Nivel.** Motor. `check (length(trim(merchant)) > 0)` y
`check (payment_method in ('Efectivo', 'Tarjeta', 'Transferencia'))`.

**Motivo.** El comercio es la única entrada con la que el motor de categorización puede trabajar.
Sin él, el gasto es inclasificable. El conjunto cerrado de medios de pago mantiene comparables las
agregaciones y evita que la misma forma de pago se escriba de cinco maneras.

### RN-08. Las referencias de un gasto no pueden cruzar hogares, y nada se borra en cascada

El responsable y la categoría de un gasto deben pertenecer al mismo hogar que el gasto. Además,
ninguna baja arrastra filas dependientes.

**Nivel.** Motor. Claves foráneas compuestas `(household_id, owner_id)` contra
`users (household_id, id)` y `(household_id, category_id)` contra `categories (household_id, id)`,
todas con `on delete restrict`.

**Motivo.** Es defensa en capas. Aunque el aislamiento por políticas fallara o el código de negocio
tuviera un error, la clave compuesta impide que un gasto del hogar A quede apuntando a una
categoría del hogar B. El `restrict` es una segunda barrera: la aplicación no borra físicamente
gastos ni categorías (RN-16), y si alguien lo intentara por fuera de ella, la base rechaza eliminar
una categoría que todavía tiene gastos.

### RN-16. Gastos y categorías se dan de baja de forma lógica

Dar de baja un gasto o una categoría marca la fila como dada de baja y la conserva. Nunca se borra
físicamente. Las reglas de categorización, en cambio, sí se eliminan físicamente.

Consecuencias de cada baja:

- **Gasto dado de baja.** Deja de aparecer en listados, en la bandeja de no categorizados y en el
  análisis. La fila conserva importe, comercio, responsable y fechas.
- **Categoría dada de baja.** Deja de ofrecerse para nuevas asignaciones y sus reglas dejan de
  aplicarse. Los gastos que ya la tenían la conservan, en el historial y en el análisis del período
  que corresponda.
- **Nombre liberado.** Un nombre de categoría dado de baja no sigue ocupando su lugar: el hogar
  puede crear otra categoría activa con el mismo nombre (ver RN-09).

**Nivel.** Ambos. La aplicación filtra las filas dadas de baja en todas las consultas de gastos y
categorías. El motor sostiene la unicidad solo entre filas activas, con índices únicos parciales.

**Cómo se registra.** `expenses` y `categories` llevan tres columnas:

- `active`: la marca de baja. Es la condición que filtra la aplicación y la que usan los índices
  parciales.
- `deleted_at`: el momento de la baja. Es null mientras la fila está activa.
- `updated_at`: la última modificación de la fila, sea o no la baja.

`active` y `deleted_at` dicen lo mismo de dos maneras. Un `check` en cada tabla
(`expenses_active_deleted_at_consistent`, `categories_active_deleted_at_consistent`) impide que se
contradigan: una fila activa no tiene fecha de baja y una dada de baja siempre la tiene.
`updated_at` no alcanza como fecha de baja porque cualquier edición posterior la pisa.

**Motivo.** En una aplicación de dinero, el borrado físico destruye el historial sobre el que
después se pide explicación: quién cargó qué, cuándo y por cuánto. Con una categoría pasa lo mismo:
borrarla dejaría sin clasificar gastos de meses ya analizados. Las reglas no tienen ese problema
porque no son un hecho económico sino una preferencia de clasificación, y eliminar una no altera
ningún gasto ya clasificado.

---

## Categorización

### RN-09. El nombre de categoría es único dentro del hogar

Sin distinguir mayúsculas. `Comida` y `comida` son la misma categoría. La unicidad rige solo entre
las categorías activas: una categoría dada de baja no bloquea su nombre (RN-16), y el hogar puede
crear otra con el mismo nombre.

**Nivel.** Motor. Índice único parcial `categories_household_name_key` sobre
`(household_id, lower(name))`, con la condición `where active`. Las filas dadas de baja
quedan fuera del índice, así que pueden repetir el nombre entre sí y con la categoría activa.

**Motivo.** Las categorías duplicadas por capitalización parten el gasto de un mismo concepto en
dos filas del análisis, que es el problema que el sistema quiere resolver. El índice es parcial
porque un índice total haría que una categoría dada de baja siguiera ocupando su nombre para
siempre: el hogar no podría volver a crear `Transporte` después de dar de baja la anterior, y la
baja lógica se sentiría como un borrado que no libera nada.

**Consecuencia.** Dos filas pueden tener el mismo nombre en el mismo hogar, siempre que como mucho
una esté activa. Los gastos siguen apuntando por identificador a la categoría que tenían, así que
el historial distingue la categoría vieja de la nueva aunque se llamen igual.

### RN-10. El patrón de regla es único dentro del hogar

**Nivel.** Motor. Índice único `category_rules_household_pattern_key` sobre
`(household_id, lower(pattern))`.

**Motivo.** Dos reglas con el mismo patrón apuntando a categorías distintas no tendrían forma de
desempatarse.
Patrones distintos que coinciden con el mismo comercio sí pueden convivir, y los resuelve
RN-17.

### RN-12. Si ningún patrón coincide, el gasto queda sin categoría

No se asigna una categoría genérica de descarte.

**Nivel.** Ambos. `expenses.category_id` admite nulo. La clave foránea compuesta es `MATCH SIMPLE`,
por lo que no se evalúa mientras la categoría sea nula.

**Motivo.** Un gasto archivado en una categoría "Otros" deja de aparecer como pendiente y nadie lo
corrige. Sin categoría queda visible en la bandeja de no categorizados, que el usuario vacía
enseñándole al sistema un comercio por vez.

### RN-13. Crear una regla reclasifica retroactivamente, solo los gastos sin categoría

Al dar de alta una regla, se aplica a los gastos activos del hogar que coincidan con el patrón y
que no tengan categoría asignada. No se reasignan los que ya la tienen, ni los dados de baja
(RN-16).

**Nivel.** Aplicación.

**Motivo.** El objetivo es que enseñar un comercio se haga una sola vez, vaciando la bandeja de
golpe. Reasignar gastos ya clasificados alteraría análisis que el usuario ya dio por buenos,
incluso los que corrigió a mano. Una clasificación manual es una decisión explícita y una regla
nueva no debe sobrescribirla.


### RN-17. Si coinciden varias reglas, gana la del patrón más largo

Cuando más de una regla del hogar coincide con el comercio, se aplica la de patrón más largo. Si dos
patrones empatan en longitud, se aplica la regla más antigua.

Ejemplos:

- Con los patrones `super` y `supermercado`, el comercio "Super Mercado Rosario" se resuelve por
  `supermercado`.
- Con `uber` y `uber eats`, un pedido de comida no queda clasificado como transporte.

**Nivel.** Aplicación.

**Motivo.** El patrón más específico manda, que es lo que el usuario espera cuando agrega una regla
más precisa sobre un comercio que ya estaba cubierto. El desempate por antigüedad hace que agregar
una regla nueva nunca cambie cómo se resuelve un comercio que ya tenía un ganador con un patrón de
la misma longitud. No hace falta una columna de prioridad: el orden se deduce del patrón y de la
fecha de alta, y no hay un orden que el usuario deba mantener a mano.

**Relación con otras reglas.** Solo compiten las reglas de categorías activas (RN-16). Para un
patrón idéntico no hay desempate posible, y por eso RN-10 lo impide.

---

## Análisis

### RN-14. La media histórica se calcula sobre los meses con gasto registrado

Los meses sin ningún gasto no cuentan como cero en el promedio.

**Nivel.** Aplicación.

**Motivo.** Un hogar que empezó a usar el sistema en marzo no tuvo gasto cero en enero y febrero.
No tuvo registro. Contarlos como cero baja la media y hace que cualquier mes real parezca un
exceso.

---

## Plataforma

### RN-15. El esquema es propiedad exclusiva de las migraciones versionadas

La capa de mapeo objeto-relacional valida que el modelo de clases coincida con el esquema
existente, pero no puede crearlo ni modificarlo.

**Nivel.** Aplicación. `ddl-auto: validate`.

**Motivo.** Si entidades y migraciones se desincronizan, la aplicación falla al arrancar. Es
deliberado. Se prefiere un error inmediato y visible en el despliegue antes que una divergencia
silenciosa entre el código y los datos, que solo se descubre cuando una consulta devuelve algo
inesperado en producción.

---

## Resumen

| ID | Regla | Nivel | Módulo |
|---|---|---|---|
| RN-01 | El correo identifica unívocamente a un usuario | Ambos | M1 |
| RN-02 | Una cuenta sin verificar no puede operar | Aplicación | M1 |
| RN-03 | Tokens de un solo uso, con vencimiento de 24 h | Aplicación | M1 |
| RN-04 | Un usuario pertenece a exactamente un hogar | Motor | M2 |
| RN-05 | La invitación se canjea una sola vez y vence | Aplicación | M2 |
| RN-06 | Importe mayor que cero, con dos decimales exactos | Ambos | M3 |
| RN-07 | Comercio obligatorio y medio de pago del conjunto cerrado | Motor | M3 |
| RN-08 | Referencias dentro del hogar, sin borrado en cascada | Motor | M3 |
| RN-09 | Nombre de categoría único por hogar | Motor | M4 |
| RN-10 | Patrón de regla único por hogar | Motor | M4 |
| RN-11 | Alta atómica de usuario, hogar y catálogo inicial | Ambos | M1 |
| RN-12 | Sin coincidencia, el gasto queda sin categoría | Ambos | M4 |
| RN-13 | La regla nueva reclasifica solo lo no categorizado | Aplicación | M4 |
| RN-14 | La media histórica ignora los meses sin registro | Aplicación | M5 |
| RN-15 | El esquema pertenece a las migraciones | Aplicación | M8 |
| RN-16 | Gastos y categorías se dan de baja de forma lógica | Ambos | M3, M4 |
| RN-17 | Entre reglas que coinciden gana el patrón más largo | Aplicación | M4 |
