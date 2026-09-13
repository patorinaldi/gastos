-- V2 · Rol de aplicación sin privilegios elevados + aislamiento por hogar (RLS)
--
-- La app se conecta con gastos_api, no con el rol que corre las migraciones (owner
-- de las tablas). gastos_api no tiene BYPASSRLS ni ningún privilegio de superusuario:
-- aunque el código de la aplicación tenga un bug y olvide propagar el contexto de
-- hogar, la conexión en sí misma es incapaz de ver o modificar datos de otro hogar.
--
-- categories, category_rules y expenses quedan aisladas en este V2. users y
-- households se cierran con el módulo de identidad (el
-- login necesita poder buscar un usuario por email sin contexto de hogar todavía).

-- La contraseña de gastos_api es tan sensible como la clave de firma de los JWT:
-- las políticas RLS de abajo confían ciegamente en app.current_household, sin
-- verificar nada más, así que quien tenga esta contraseña puede conectarse con
-- cualquier cliente Postgres, fijar ese valor a mano y leer o escribir los datos
-- de cualquier hogar sin pasar por la aplicación. Además, "create role ... password"
-- no se redacta en el log de sentencias de Postgres como sí ocurre con las queries
-- parametrizadas: si algún entorno activa log_statement (all/ddl/mod), esta
-- contraseña queda en texto plano en el log del servidor.
-- Los valores de dev (gastos/gastos, gastos_api/gastos_api) son descartables y no
-- importa que queden en el repo o en logs locales. Antes del primer despliegue
-- contra una Postgres gestionada: rotar la contraseña de
-- gastos_api (y la de gastos) fuera de esta migración, nunca dejar en producción
-- el valor con el que Flyway la creó la primera vez; y si el proveedor ofrece
-- logging de queries/DDL, no activarlo salvo que se pueda excluir CREATE/ALTER ROLE.
create role gastos_api login password '${api_password}';
alter role gastos_api nosuperuser nocreatedb nocreaterole nobypassrls noreplication;

grant usage on schema public to gastos_api;
grant select, insert, update, delete
    on households, users, categories, category_rules, expenses
    to gastos_api;

-- Hogar activo de la transacción en curso (lo fija el hook transaccional de la app).
-- current_setting(..., true) devuelve null si la variable no está seteada; nullif
-- evita que un valor vacío rompa el cast a uuid.
create function app_current_household() returns uuid
    language sql
    stable
as $$
    select nullif(current_setting('app.current_household', true), '')::uuid
$$;

alter table categories     enable row level security;
alter table categories     force  row level security;
alter table category_rules enable row level security;
alter table category_rules force  row level security;
alter table expenses       enable row level security;
alter table expenses       force  row level security;

create policy categories_household_isolation on categories
    using (household_id = app_current_household())
    with check (household_id = app_current_household());

create policy category_rules_household_isolation on category_rules
    using (household_id = app_current_household())
    with check (household_id = app_current_household());

create policy expenses_household_isolation on expenses
    using (household_id = app_current_household())
    with check (household_id = app_current_household());
