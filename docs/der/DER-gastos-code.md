# Diagrama entidad-relación (DBML) — Gastos

Este archivo contiene el modelo de datos de la app **Gastos** escrito en
[DBML](https://dbml.dbdiagram.io/docs) (Database Markup Language).

DBML no es el diagrama en sí, sino el código fuente para generarlo: describe
tablas, columnas, índices y relaciones en texto plano. `dbdiagram.io` lo lee y
dibuja el ERD interactivo (se puede reordenar, hacer zoom, exportar a
imagen/SQL, etc.).

El diagrama ya generado a partir de este código está exportado en
[DER-gastos.svg](./DER-gastos.svg), en este mismo directorio.

## Cómo generarlo

1. Entrar a [dbdiagram.io](https://dbdiagram.io) y crear un diagrama nuevo
   (o abrir uno existente).
2. Borrar el contenido del editor de la izquierda.
3. Copiar y pegar el bloque de código de abajo tal cual.
4. El diagrama se renderiza solo, del lado derecho.

> **Nota:** este DBML no representa el rol `gastos_api`, la función
> `app_current_household()`, las políticas de Row Level Security, los disparadores ni la condición
> de los índices parciales — DBML no modela nada de eso. Esas reglas quedan documentadas como
> `Note` en las tablas que afectan (`categories`, `category_rules`, `expenses`) y, en prosa, en
> [modelo-de-datos.md](../modelo-de-datos.md).

```dbml
Project gastos {
  database_type: 'PostgreSQL'
  Note: '''
    Aislamiento por hogar en dos capas:
    1. Todas las tablas de negocio llevan household_id y las referencias entre
       ellas son FK compuestas (household_id, x) contra unique (household_id, id).
    2. RLS (V2) sobre categories, category_rules y expenses, con
       household_id = app_current_household().
    Borrado: NO ACTION / restrict en todos los FK.
    Baja logica (V4): expenses y categories llevan active + deleted_at y no se
    borran fisicamente. category_rules si se elimina.
  '''
}

Table households {
  id         uuid        [pk, default: `gen_random_uuid()`]
  name       text        [not null, note: 'check: length(trim(name)) > 0']
  created_at timestamptz [not null, default: `now()`]
}

Table users {
  id             uuid         [pk, default: `gen_random_uuid()`]
  household_id   uuid         [not null]
  email          varchar(320) [not null, note: 'identidad de login; único global case-insensitive']
  password_hash  text         [not null]
  email_verified boolean      [not null, default: false]
  name           varchar(200) [not null]
  created_at     timestamptz  [not null, default: `now()`]

  indexes {
    (household_id, id) [unique, name: 'users_household_id_id_key']
    `upper(email)`     [unique, name: 'users_email_key', note: 'V2.5: upper() y no lower(), que es lo que genera Spring Data para IgnoreCase']
    household_id       [name: 'users_household_idx']
  }

  Note: 'Sin RLS en V2: el login necesita buscar por email sin contexto de hogar.'
}

Table categories {
  id           uuid         [pk, default: `gen_random_uuid()`]
  household_id uuid         [not null]
  name         varchar(200) [not null]
  created_at   timestamptz  [not null, default: `now()`]
  updated_at   timestamptz  [not null, default: `now()`, note: 'V4']
  active       boolean      [not null, default: true, note: 'V4: marca de baja logica (RN-16)']
  deleted_at   timestamptz  [note: 'V4: momento de la baja; null mientras esta activa. check: active = (deleted_at is null)']

  indexes {
    (household_id, id)           [unique, name: 'categories_household_id_id_key']
    (household_id, `lower(name)`) [unique, name: 'categories_household_name_key', note: 'V4: parcial, where active. Una categoria dada de baja libera su nombre']
  }

  Note: '''
    RLS (V2): enable + force, policy categories_household_isolation.
    Trigger (V4) categories_delete_rules_on_deactivate: al pasar active de true a
    false, elimina fisicamente las category_rules de esa categoria.
  '''
}

Table category_rules {
  id           uuid         [pk, default: `gen_random_uuid()`]
  household_id uuid         [not null]
  pattern      varchar(200) [not null]
  category_id  uuid         [not null]
  created_at   timestamptz  [not null, default: `now()`]

  indexes {
    (household_id, `lower(pattern)`) [unique, name: 'category_rules_household_pattern_key']
    (household_id, category_id)      [name: 'category_rules_household_category_idx']
  }

  Note: 'RLS (V2): enable + force, policy category_rules_household_isolation.'
}

Table expenses {
  id             uuid           [pk, default: `gen_random_uuid()`]
  household_id   uuid           [not null]
  owner_id       uuid           [not null]
  category_id    uuid           [note: 'nullable: FK compuesta MATCH SIMPLE no se evalúa con NULL → bandeja de no categorizados']
  merchant       varchar(200)   [not null]
  amount         numeric(12,2)  [not null, note: 'check: amount > 0']
  expense_date   date           [not null]
  payment_method text           [not null, note: "check in ('Efectivo', 'Tarjeta', 'Transferencia')"]
  created_at     timestamptz    [not null, default: `now()`]
  updated_at     timestamptz    [not null, default: `now()`]
  active         boolean        [not null, default: true, note: 'V4: marca de baja logica (RN-16)']
  deleted_at     timestamptz    [note: 'V4: momento de la baja; null mientras esta activo. check: active = (deleted_at is null)']

  indexes {
    (household_id, expense_date) [name: 'expenses_household_date_idx', note: 'expense_date desc']
    (household_id, category_id)  [name: 'expenses_household_category_idx']
    (household_id, owner_id)     [name: 'expenses_household_owner_idx']
  }

  Note: 'RLS (V2): enable + force, policy expenses_household_isolation.'
}

// ── FK directas a households (on delete restrict) ────────────────────────────
Ref users_household:          users.household_id          > households.id [delete: restrict]
Ref categories_household:     categories.household_id     > households.id [delete: restrict]
Ref category_rules_household: category_rules.household_id > households.id [delete: restrict]
Ref expenses_household:       expenses.household_id       > households.id [delete: restrict]

// ── FK compuestas por hogar (impiden cruzar hogares aunque falle el RLS) ─────
Ref category_rules_category_fk:
  category_rules.(household_id, category_id) > categories.(household_id, id) [delete: restrict]

Ref expenses_owner_fk:
  expenses.(household_id, owner_id) > users.(household_id, id) [delete: restrict]

Ref expenses_category_fk:
  expenses.(household_id, category_id) > categories.(household_id, id) [delete: restrict]
```
