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
categoría del hogar B. El `restrict` evita que eliminar una categoría borre el historial de gastos
asociado.

---

## Categorización

### RN-09. El nombre de categoría es único dentro del hogar

Sin distinguir mayúsculas. `Comida` y `comida` son la misma categoría.

**Nivel.** Motor. Índice único `categories_household_name_key` sobre `(household_id, lower(name))`.

**Motivo.** Las categorías duplicadas por capitalización parten el gasto de un mismo concepto en
dos filas del análisis, que es el problema que el sistema quiere resolver.

### RN-10. El patrón de regla es único dentro del hogar

**Nivel.** Motor. Índice único `category_rules_household_pattern_key` sobre
`(household_id, lower(pattern))`.

**Motivo.** Dos reglas con el mismo patrón apuntando a categorías distintas harían que la
clasificación dependiera del orden de evaluación, y el motor dejaría de producir siempre el mismo
resultado para la misma entrada.

### RN-12. Si ningún patrón coincide, el gasto queda sin categoría

No se asigna una categoría genérica de descarte.

**Nivel.** Ambos. `expenses.category_id` admite nulo. La clave foránea compuesta es `MATCH SIMPLE`,
por lo que no se evalúa mientras la categoría sea nula.

**Motivo.** Un gasto archivado en una categoría "Otros" deja de aparecer como pendiente y nadie lo
corrige. Sin categoría queda visible en la bandeja de no categorizados, que el usuario vacía
enseñándole al sistema un comercio por vez.

### RN-13. Crear una regla reclasifica retroactivamente, solo los gastos sin categoría

Al dar de alta una regla, se aplica a los gastos del hogar que coincidan con el patrón y que no
tengan categoría asignada. No se reasignan los que ya la tienen.

**Nivel.** Aplicación.

**Motivo.** El objetivo es que enseñar un comercio se haga una sola vez, vaciando la bandeja de
golpe. Reasignar gastos ya clasificados alteraría análisis que el usuario ya dio por buenos,
incluso los que corrigió a mano. Una clasificación manual es una decisión explícita y una regla
nueva no debe sobrescribirla.

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
