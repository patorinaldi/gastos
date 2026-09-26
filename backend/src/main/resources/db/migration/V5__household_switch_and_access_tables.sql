-- V5 · Cambio de hogar, archivado y tablas de acceso
--
-- Cambio de hogar (RN-18). Hasta la V4, expenses_owner_fk apuntaba a users (household_id, id): el
-- responsable de un gasto tenía que estar *ahora* en el hogar del gasto, así que un usuario con
-- gastos no podía cambiarse de hogar. La FK pasa a ser simple, contra users (id). Quien se cambia
-- de hogar solo actualiza users.household_id: sus gastos quedan en el hogar anterior y los demás
-- integrantes los siguen viendo con su nombre. Que el responsable pertenezca al hogar al cargar el
-- gasto lo garantiza la aplicación, que lo toma siempre del token de sesión y nunca del cliente.
-- Los gastos siguen aislados por RLS igual que antes.
--
-- Tablas de acceso (RN-03, RN-05). Tokens de verificación y restablecimiento, invitaciones y
-- tokens de cliente máquina. Se guarda solo el hash SHA-256 (32 bytes): el valor en claro existe
-- únicamente en el correo, en el código compartido o en el dispositivo.
--
-- Estas tablas no llevan RLS, igual que users: son datos de acceso y no de negocio, y se buscan
-- justamente sin contexto de hogar (al canjear un código, al autenticar una automatización). Los
-- listados por hogar filtran en la consulta. RLS protege los datos de negocio: gastos, categorías
-- y reglas.

-- ── Archivado de hogares (RN-18) ────────────────────────────────────────────────────────────
-- Misma forma que la baja lógica de la V4. Un hogar se archiva cuando su último integrante se
-- cambia a otro: los datos se conservan, pero nadie accede a ellos.

alter table households
    add column active      boolean     not null default true,
    add column archived_at timestamptz,
    add constraint households_active_archived_at_consistent
        check (active = (archived_at is null));

-- ── Responsable del gasto ───────────────────────────────────────────────────────────────────

alter table expenses drop constraint expenses_owner_fk;
alter table expenses add constraint expenses_owner_fk
    foreign key (owner_id) references users (id) on delete restrict;

-- users (household_id, id) solo existía como destino de la FK anterior.
alter table users drop constraint users_household_id_id_key;

-- ── Tokens de verificación de correo y restablecimiento de contraseña (RN-03) ───────────────
-- Una sola tabla para los dos usos, porque comparten ciclo de vida.

create table auth_tokens (
    id         uuid        primary key default gen_random_uuid(),
    user_id    uuid        not null references users (id) on delete restrict,
    purpose    text        not null,
    token_hash bytea       not null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default now() + interval '24 hours',
    used_at    timestamptz,

    constraint auth_tokens_purpose_valid
        check (purpose in ('email_verification', 'password_reset')),
    constraint auth_tokens_hash_length check (octet_length(token_hash) = 32)
);

create unique index auth_tokens_token_hash_key on auth_tokens (token_hash);
create index auth_tokens_user_idx on auth_tokens (user_id);

-- ── Invitaciones (RN-05) ────────────────────────────────────────────────────────────────────

create table household_invitations (
    id           uuid        primary key default gen_random_uuid(),
    household_id uuid        not null references households (id) on delete restrict,
    created_by   uuid        not null references users (id) on delete restrict,
    code_hash    bytea       not null,
    created_at   timestamptz not null default now(),
    expires_at   timestamptz not null default now() + interval '7 days',
    redeemed_by  uuid        references users (id) on delete restrict,
    redeemed_at  timestamptz,

    constraint household_invitations_hash_length check (octet_length(code_hash) = 32),
    -- Canjeada significa las dos cosas a la vez: quién y cuándo.
    constraint household_invitations_redemption_consistent
        check ((redeemed_by is null) = (redeemed_at is null))
);

create unique index household_invitations_code_hash_key on household_invitations (code_hash);
create index household_invitations_household_idx on household_invitations (household_id);

-- ── Tokens de cliente máquina (RF-07, RF-31) ───────────────────────────────────────────────

create table machine_tokens (
    id           uuid         primary key default gen_random_uuid(),
    household_id uuid         not null references households (id) on delete restrict,
    user_id      uuid         not null references users (id) on delete restrict,
    name         varchar(100) not null,
    token_hash   bytea        not null,
    created_at   timestamptz  not null default now(),
    last_used_at timestamptz,
    revoked_at   timestamptz,

    constraint machine_tokens_name_not_blank check (length(trim(name)) > 0),
    constraint machine_tokens_hash_length check (octet_length(token_hash) = 32)
);

create unique index machine_tokens_token_hash_key on machine_tokens (token_hash);
create index machine_tokens_household_idx on machine_tokens (household_id);

-- ── Permisos del rol de aplicación ──────────────────────────────────────────────────────────

grant select, insert, update, delete
    on auth_tokens, household_invitations, machine_tokens
    to gastos_api;
