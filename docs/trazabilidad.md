# Trazabilidad

Proyecto Gastos. Trabajo Final Integrador, TUP UTN.

Relaciona los artefactos del sistema en un solo lugar: requisitos funcionales (RF), historias de
usuario (HU), casos de uso (CU), reglas de negocio (RN) y el esquema de la base de datos. Cada
documento ya declara sus propias referencias; esta matriz las reúne para poder seguir un requisito
desde su origen hasta la tabla que lo sostiene, y una regla desde el motor hasta las historias que
la ejercitan.

Las relaciones de cada fila salen de lo que declaran
[historias-de-usuario.md](historias-de-usuario.md), [casos-de-uso.md](casos-de-uso.md) y
[reglas-de-negocio.md](reglas-de-negocio.md). El estado de cada requisito está en
[requerimientos.md](requerimientos.md), y la relación de cada módulo con sus requisitos no
funcionales, en su matriz por módulo. El detalle de cada tabla está en
[modelo-de-datos.md](modelo-de-datos.md).

Un guion indica que no hay relación directa. El aislamiento entre hogares (RNF-01, migración V2) no
figura fila por fila porque alcanza a todas las tablas de negocio: ver
[modelo-de-datos.md](modelo-de-datos.md) y la condición transversal de
[casos-de-uso.md](casos-de-uso.md).

## 1. Por requisito funcional

| Requisito | Módulo | Historias | Casos de uso | Reglas | Tablas |
|---|---|---|---|---|---|
| RF-01 Registro de usuario | M1 | HU-01 | CU-01 | RN-01, RN-11 | `users` |
| RF-02 Hogar propio en el alta | M1 | HU-01 | CU-01 | RN-04, RN-11 | `households`, `users` |
| RF-03 Correo de verificación | M1 | HU-01, HU-02 | CU-01 | RN-03 | `auth_tokens` |
| RF-04 Rechazo sin correo verificado | M1 | HU-02 | CU-02 | RN-02 | `users` |
| RF-05 Inicio de sesión y token | M1 | HU-03 | CU-02 | RN-02, RN-18 | `users` |
| RF-06 Restablecimiento de contraseña | M1 | HU-04 | CU-08 | RN-03 | `auth_tokens`, `users` |
| RF-07 Tokens de cliente máquina | M1 | HU-17 | CU-07 | RN-18 | `machine_tokens` |
| RF-08 Código de invitación | M2 | HU-05 | — | RN-05 | `household_invitations` |
| RF-09 Canje e incorporación | M2 | HU-06 | CU-03 | RN-04, RN-05, RN-18 | `household_invitations`, `users`, `households`, `machine_tokens` |
| RF-10 Integrantes del hogar | M2 | HU-07 | — | RN-04 | `users` |
| RF-11 Nombre del hogar | M2 | HU-18 | — | — | `households` |
| RF-12 Invalidar la invitación | M2 | HU-05 | CU-03 | RN-05 | `household_invitations` |
| RF-13 Alta de gasto | M3 | HU-08 | CU-04 | RN-06, RN-07, RN-08 | `expenses` |
| RF-14 Fecha y responsable automáticos | M3 | HU-08 | CU-04 | RN-08 | `expenses` |
| RF-15 Edición de gasto | M3 | HU-09 | — | RN-06, RN-07, RN-08 | `expenses` |
| RF-16 Baja lógica de gasto | M3 | HU-09 | — | RN-16 | `expenses` |
| RF-17 Listado paginado | M3 | HU-10 | — | RN-16 | `expenses` |
| RF-18 Filtros del listado | M3 | HU-10, HU-20 | — | RN-16 | `expenses` |
| RF-19 Catálogo inicial | M4 | HU-01 | CU-01 | RN-11 | `categories`, `category_rules` |
| RF-20 Categoría en el alta | M4 | HU-08 | CU-04 | RN-12, RN-17 | `category_rules`, `expenses` |
| RF-21 Sin coincidencia, sin categoría | M4 | HU-08 | CU-04 | RN-12 | `expenses` |
| RF-22 Bandeja de no categorizados | M4 | HU-11 | — | RN-12 | `expenses` |
| RF-23 Regla con reclasificación retroactiva | M4 | HU-11 | CU-05 | RN-10, RN-13 | `category_rules`, `expenses` |
| RF-24 Catálogo de categorías | M4 | HU-12 | — | RN-09, RN-16 | `categories`, `category_rules` |
| RF-43 Listado y baja de reglas | M4 | — | CU-05 | RN-10, RN-13 | `category_rules` |
| RF-25 Total del período | M5 | HU-13 | CU-06 | RN-16 | `expenses` |
| RF-26 Agregados por categoría, integrante y medio | M5 | HU-13 | CU-06 | RN-08, RN-16, RN-18 | `expenses`, `categories`, `users` |
| RF-27 Serie mensual y media histórica | M5 | HU-14 | CU-06 | RN-14, RN-16 | `expenses` |
| RF-28 Comercios con más gasto | M5 | HU-19 | CU-06 | RN-16 | `expenses` |
| RF-29 Filtros del análisis | M5 | HU-20 | CU-06 | RN-16 | `expenses` |
| RF-30 Captura rápida | M6 | HU-16 | CU-07 | RN-06, RN-07, RN-17 | `expenses` |
| RF-31 Autenticación por token de máquina | M6 | HU-16, HU-17 | CU-07 | RN-18 | `machine_tokens` |
| RF-32 Normalización de importes | M6 | HU-16 | CU-07 | RN-06 | — |
| RF-33 Pantallas de registro y sesión | M7 | — | — | — | — |
| RF-34 Tablero del período | M7 | — | — | — | — |
| RF-35 Gráficos de distribución | M7 | — | — | — | — |
| RF-36 Gráfico de evolución mensual | M7 | — | — | — | — |
| RF-37 Filtrado interactivo | M7 | HU-15 | CU-06 | — | — |
| RF-38 Pantallas del hogar | M7 | — | — | — | — |
| RF-39 Migraciones versionadas | M8 | — | — | RN-15 | todas, con el historial de Flyway |
| RF-40 Errores estandarizados | M8 | — | — | — | — |
| RF-41 Verificación de estado | M8 | — | — | — | — |
| RF-42 Pruebas en cada incorporación | M8 | — | — | — | — |

Las pantallas de M7 (RF-33 a RF-38) son la interfaz de las historias de los demás módulos, y por
eso no tienen historias propias salvo el filtrado interactivo. Los requisitos de M8 (RF-39 a RF-42)
son de plataforma: no tienen un flujo de usuario, y se verifican con la integración continua y las
pruebas automatizadas.

## 2. Por regla de negocio

"Dónde" indica quién hace cumplir la regla: la aplicación, el motor de base de datos o ambos.

| Regla | Dónde | Módulos | Historias | Casos de uso | Base de datos |
|---|---|---|---|---|---|
| RN-01 Correo único | Ambos | M1 | HU-01 | CU-01 | Índice único `users_email_key` sobre `upper(email)` (V1, corregido en V2.5) |
| RN-02 Sin correo verificado no opera | Aplicación | M1 | HU-02, HU-03 | CU-02 | `users.email_verified`, falso por defecto (V1) |
| RN-03 Tokens de un solo uso que vencen | Ambos | M1 | HU-02, HU-04 | CU-01, CU-08 | `auth_tokens`: solo el hash, con `check` de 32 bytes, y vencimiento de 24 horas por defecto (V5) |
| RN-04 Un usuario, un hogar | Motor | M2 | HU-06 | CU-03 | `users.household_id` obligatorio, con clave foránea a `households` (V1) |
| RN-05 Invitación de un solo canje que vence | Ambos | M2 | HU-05, HU-06 | CU-03 | `household_invitations`: hash, vencimiento de 7 días y `check` del canje (V5); disparador que impide modificar un canje registrado (V7) |
| RN-06 Importe positivo con dos decimales | Ambos | M3, M6 | HU-08, HU-09, HU-16 | CU-04, CU-07 | `expenses.amount` `numeric(12,2)` con `check (amount > 0)` (V1) |
| RN-07 Comercio y medio de pago válidos | Motor | M3, M6 | HU-08, HU-09, HU-16 | CU-04, CU-07 | `check` de comercio no vacío y de medio de pago del conjunto cerrado en `expenses` (V1) |
| RN-08 Referencias dentro del hogar, sin cascada | Motor | M3, M5 | HU-09 | CU-04 | Clave foránea compuesta de la categoría (V1); disparador `require_user_in_household` para el responsable (V5) |
| RN-09 Nombre de categoría único | Motor | M4 | HU-12 | CU-05 | Índice único `categories_household_name_key` (V1), parcial sobre las activas desde V4 |
| RN-10 Patrón de regla único | Motor | M4 | — | CU-05 | Índice único `category_rules_household_pattern_key` (V1) |
| RN-11 Alta atómica de usuario, hogar y catálogo | Ambos | M1 | HU-01 | CU-01 | Función `seed_household_defaults()` (V3), dentro de la transacción del alta |
| RN-12 Sin coincidencia, sin categoría | Ambos | M3, M4 | HU-08 | CU-04 | `expenses.category_id` admite nulo, con clave foránea `MATCH SIMPLE` (V1) |
| RN-13 La regla nueva reclasifica solo lo no categorizado | Aplicación | M4 | HU-11 | CU-05 | — |
| RN-14 Media sobre los meses con gasto | Aplicación | M5 | HU-14 | CU-06 | — |
| RN-15 El esquema es de las migraciones | Aplicación | M8 | — | — | Historial de Flyway; la aplicación valida el esquema al arrancar (`ddl-auto: validate`) |
| RN-16 Baja lógica de gastos y categorías | Ambos | M3, M4, M5 | HU-09, HU-12, HU-13, HU-19 | CU-04, CU-05, CU-06 | `active` y `deleted_at` con `check` de consistencia, índices únicos parciales y disparador que elimina las reglas de una categoría dada de baja (V4) |
| RN-17 Gana el patrón más largo | Aplicación | M4, M6 | HU-08 | CU-04, CU-07 | — |
| RN-18 Al cambiarse de hogar, los gastos quedan | Ambos | M1, M2, M5, M6 | HU-03, HU-06, HU-17 | CU-02, CU-03, CU-04, CU-06, CU-07 | Clave foránea simple del responsable contra `users (id)`, y `households.active` y `archived_at` con `check` de consistencia (V5) |

## 3. Por tabla

| Tabla | Migraciones | Reglas que sostiene |
|---|---|---|
| `households` | V1; archivado en V5 | RN-04, RN-18 |
| `users` | V1; índice de correo en V2.5; versión de sesión en V6 | RN-01, RN-02, RN-04 |
| `categories` | V1; siembra del catálogo inicial en V3; baja lógica en V4 | RN-08, RN-09, RN-11, RN-16 |
| `category_rules` | V1; siembra del catálogo inicial en V3 | RN-10, RN-11, RN-16 |
| `expenses` | V1; baja lógica en V4; responsable por disparador en V5 | RN-06, RN-07, RN-08, RN-12, RN-16, RN-18 |
| `household_invitations` | V5; canje único en V7 | RN-05 |
| `auth_tokens` | V5 | RN-03 |
| `machine_tokens` | V5 | RN-18 |
