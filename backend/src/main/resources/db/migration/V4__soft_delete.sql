-- V4 · Baja lógica de gastos y categorías
--
-- Gastos y categorías no se borran físicamente: se marcan con active = false y se conservan
-- (RN-16). Las reglas de categorización no llevan marca: se eliminan físicamente.
--
-- Cada tabla lleva tres columnas relacionadas con el ciclo de vida de la fila:
--   * active: la marca de baja. Es la que filtra la aplicación y la que usa el índice parcial.
--   * deleted_at: el momento de la baja. Null mientras la fila está activa.
--   * updated_at: la última modificación de la fila, sea o no la baja. expenses ya la tiene
--     desde V1; categories la recibe acá con la misma forma.
-- active y deleted_at dicen lo mismo de dos maneras, así que un check impide que se
-- contradigan: una fila activa no tiene fecha de baja y una dada de baja siempre la tiene.
-- updated_at no alcanza como fecha de baja porque se pisa con cualquier edición posterior.
--
-- Filtrar las filas dadas de baja es trabajo de la aplicación. La base solo cambia en lo que
-- tiene que sostener ella: la coherencia entre active y deleted_at, y la unicidad del nombre de
-- categoría, que pasa a regir únicamente entre categorías activas (RN-09).
--
-- Las políticas RLS de V2 no cambian: las columnas nuevas son columnas más de tablas que ya
-- están aisladas. Tampoco los permisos de gastos_api, que se otorgaron sobre la tabla y
-- alcanzan a las columnas agregadas después.

alter table expenses
    add column active     boolean     not null default true,
    add column deleted_at timestamptz,
    add constraint expenses_active_deleted_at_consistent
        check (active = (deleted_at is null));

alter table categories
    add column active     boolean     not null default true,
    add column deleted_at timestamptz,
    add column updated_at timestamptz not null default now(),
    add constraint categories_active_deleted_at_consistent
        check (active = (deleted_at is null));

-- Índice parcial: una categoría dada de baja deja de ocupar su nombre y el hogar puede crear
-- otra activa con el mismo. Con el índice total de V1, dar de baja "Transporte" impediría
-- volver a crearla. Se conserva el nombre del índice para que las referencias a él en la
-- documentación sigan valiendo.
drop index categories_household_name_key;
create unique index categories_household_name_key
    on categories (household_id, lower(name))
    where active;
