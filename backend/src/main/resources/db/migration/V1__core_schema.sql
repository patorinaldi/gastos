-- V1 · Esquema base
--
-- Aislamiento por hogar (defensa en capas, previo a las políticas RLS de V2):
--   * toda tabla de negocio lleva household_id con FK directa a households;
--   * las referencias entre tablas de negocio son FK compuestas (household_id, x)
--     contra un unique (household_id, id) del destino, de modo que una fila solo
--     puede referenciar filas del mismo hogar aunque el contexto RLS falle.
--
-- Borrado: NO ACTION explícito (restrict) en todos los FK. En V1 nada se borra en
-- cascada; la baja real de un hogar/usuario/categoría se resolverá con soft-delete
-- si el alcance lo requiere.

create table households (
    id         uuid        primary key default gen_random_uuid(),
    name       text        not null,
    created_at timestamptz not null default now(),

    constraint households_name_not_blank check (length(trim(name)) > 0)
);

create table users (
    id             uuid         primary key default gen_random_uuid(),
    household_id   uuid         not null references households (id) on delete restrict,
    email          varchar(320) not null,
    password_hash  text         not null,
    email_verified boolean      not null default false,
    name           varchar(200) not null,
    created_at     timestamptz  not null default now(),

    -- objetivo de las FK compuestas por hogar (id ya es único por ser PK)
    constraint users_household_id_id_key unique (household_id, id),
    constraint users_email_not_blank check (length(trim(email)) > 0),
    constraint users_name_not_blank check (length(trim(name)) > 0),
    constraint users_password_hash_not_blank check (length(trim(password_hash)) > 0)
);

-- email es identidad de login: único global y case-insensitive.
create unique index users_email_key on users (lower(email));
create index users_household_idx on users (household_id);

create table categories (
    id           uuid         primary key default gen_random_uuid(),
    household_id uuid         not null references households (id) on delete restrict,
    name         varchar(200) not null,
    created_at   timestamptz  not null default now(),

    -- objetivo de las FK compuestas por hogar
    constraint categories_household_id_id_key unique (household_id, id),
    constraint categories_name_not_blank check (length(trim(name)) > 0)
);

-- nombre de categoría único por hogar, case-insensitive (coherente con email/pattern).
create unique index categories_household_name_key on categories (household_id, lower(name));

create table category_rules (
    id           uuid         primary key default gen_random_uuid(),
    household_id uuid         not null references households (id) on delete restrict,
    pattern      varchar(200) not null,
    category_id  uuid         not null,
    created_at   timestamptz  not null default now(),

    constraint category_rules_category_fk
        foreign key (household_id, category_id) references categories (household_id, id)
        on delete restrict,
    constraint category_rules_pattern_not_blank check (length(trim(pattern)) > 0)
);

create unique index category_rules_household_pattern_key
    on category_rules (household_id, lower(pattern));
create index category_rules_household_category_idx
    on category_rules (household_id, category_id);

create table expenses (
    id             uuid           primary key default gen_random_uuid(),
    household_id   uuid           not null references households (id) on delete restrict,
    owner_id       uuid           not null,
    category_id    uuid,
    merchant       varchar(200)   not null,
    amount         numeric(12, 2) not null,
    expense_date   date           not null,
    payment_method text           not null,
    created_at     timestamptz    not null default now(),
    updated_at     timestamptz    not null default now(),

    constraint expenses_owner_fk
        foreign key (household_id, owner_id) references users (household_id, id)
        on delete restrict,
    -- category_id nullable: la FK compuesta (MATCH SIMPLE) no se evalúa si hay NULL,
    -- lo que habilita la bandeja de no categorizados.
    constraint expenses_category_fk
        foreign key (household_id, category_id) references categories (household_id, id)
        on delete restrict,
    constraint expenses_amount_positive check (amount > 0),
    constraint expenses_merchant_not_blank check (length(trim(merchant)) > 0),
    constraint expenses_payment_method_valid
        check (payment_method in ('Efectivo', 'Tarjeta', 'Transferencia'))
);

create index expenses_household_date_idx on expenses (household_id, expense_date desc);
create index expenses_household_category_idx on expenses (household_id, category_id);
create index expenses_household_owner_idx on expenses (household_id, owner_id);