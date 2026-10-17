-- V7 · Una invitación se canjea una sola vez, también en el motor (RN-05)
--
-- Hasta la V6, el canje de una sola vez lo sostenía solo la aplicación: HouseholdService.redeem
-- bloquea la fila y rechaza una invitación ya canjeada. La base exigía que quién y cuándo se
-- fijaran juntos (household_invitations_redemption_consistent), pero dejaba sobrescribir un canje
-- ya registrado: cualquier escritura que no pasara por ese método, o un error en él, podía
-- cambiar quién canjeó el código, o devolverlo a vigente para que se canjeara otra vez.
--
-- Este trigger lo impide: una vez fijados, redeemed_by y redeemed_at no cambian. Compara valores y
-- no columnas nombradas en el update, así que reescribir los mismos valores, como hace Hibernate
-- al actualizar la fila entera, sigue funcionando. Usa el código de error de un check, como
-- require_user_in_household (V5), así que la aplicación lo recibe como cualquier otra violación de
-- integridad.

create function household_invitations_redeemed_once() returns trigger
    language plpgsql
as $$
begin
    if old.redeemed_at is not null
        and (new.redeemed_by is distinct from old.redeemed_by
            or new.redeemed_at is distinct from old.redeemed_at) then
        raise exception 'household_invitations_redeemed_once: la invitación % ya fue canjeada', old.id
            using errcode = 'check_violation';
    end if;
    return new;
end;
$$;

create trigger household_invitations_redeemed_once
    before update of redeemed_by, redeemed_at on household_invitations
    for each row execute function household_invitations_redeemed_once();
