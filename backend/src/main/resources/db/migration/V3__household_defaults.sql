-- V3 · Catálogo inicial de categorías y reglas de cada hogar
--
-- Los hogares se crean en el alta, no en una migración: por eso acá no se insertan
-- filas sino una función que siembra el catálogo de un hogar. El alta la invoca en la
-- misma transacción que crea el hogar, con ese hogar ya fijado en el contexto.
--
-- seed_household_defaults() siembra el hogar de app.current_household y no lo recibe
-- por parámetro: no puede apuntar a otro hogar que el de la transacción. Es SECURITY
-- INVOKER (el default): corre con los permisos de quien la llama, así que las
-- políticas RLS de V2 aplican igual que a cualquier insert de la aplicación.
--
-- Sembrar dos veces el mismo hogar falla por los índices únicos de nombre y patrón:
-- es un error del llamador, no algo que la función deba absorber en silencio.
--
-- Patrones en minúscula y sin tildes. Cómo se comparan contra el comercio lo define el
-- motor de reglas de M4; se evitan patrones cortos o palabras comunes que coincidirían
-- con comercios no relacionados (por eso no están "dia", "disco", "personal", "claro").

create function seed_household_defaults() returns void
    language plpgsql
as $$
declare
    current_household uuid := app_current_household();
begin
    if current_household is null then
        raise exception 'seed_household_defaults: no hay hogar en app.current_household';
    end if;

    with seeded_categories as (
        insert into categories (household_id, name)
        select current_household, c.name
        from (values
            ('Supermercado'),
            ('Restaurantes y delivery'),
            ('Transporte'),
            ('Servicios'),
            ('Salud'),
            ('Hogar'),
            ('Suscripciones'),
            ('Mascotas'),
            -- sin reglas: quedan en el catálogo para asignarlas a mano desde la bandeja
            ('Ropa'),
            ('Educación')
        ) as c (name)
        returning id, name
    )
    insert into category_rules (household_id, pattern, category_id)
    select current_household, r.pattern, sc.id
    from (values
        ('supermercado',    'Supermercado'),
        ('coto',            'Supermercado'),
        ('carrefour',       'Supermercado'),
        ('jumbo',           'Supermercado'),
        ('changomas',       'Supermercado'),
        ('la anonima',      'Supermercado'),
        ('makro',           'Supermercado'),

        ('rappi',           'Restaurantes y delivery'),
        ('pedidosya',       'Restaurantes y delivery'),
        ('mcdonald',        'Restaurantes y delivery'),
        ('burger king',     'Restaurantes y delivery'),
        ('mostaza',         'Restaurantes y delivery'),
        ('starbucks',       'Restaurantes y delivery'),

        ('uber',            'Transporte'),
        ('cabify',          'Transporte'),
        ('sube',            'Transporte'),
        ('ypf',             'Transporte'),
        ('shell',           'Transporte'),
        ('axion',           'Transporte'),
        ('peaje',           'Transporte'),
        ('estacionamiento', 'Transporte'),

        ('edenor',          'Servicios'),
        ('edesur',          'Servicios'),
        ('epec',            'Servicios'),
        ('metrogas',        'Servicios'),
        ('naturgy',         'Servicios'),
        ('ecogas',          'Servicios'),
        ('aysa',            'Servicios'),
        ('telecentro',      'Servicios'),
        ('fibertel',        'Servicios'),
        ('movistar',        'Servicios'),

        ('farmacia',        'Salud'),
        ('farmacity',       'Salud'),
        ('osde',            'Salud'),
        ('swiss medical',   'Salud'),

        ('sodimac',         'Hogar'),
        ('ferreteria',      'Hogar'),

        ('netflix',         'Suscripciones'),
        ('spotify',         'Suscripciones'),
        ('disney',          'Suscripciones'),
        ('youtube',         'Suscripciones'),

        ('veterinaria',     'Mascotas'),
        ('puppis',          'Mascotas')
    ) as r (pattern, category_name)
    -- left join a propósito: una regla con la categoría mal escrita deja category_id en
    -- null y el not null de la columna hace fallar la función, en lugar de que un inner
    -- join descarte la regla sin avisar.
    left join seeded_categories sc on sc.name = r.category_name;
end;
$$;
