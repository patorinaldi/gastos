package io.github.patorinaldi.gastos.api.service.household;

import io.github.patorinaldi.gastos.api.domain.Household;
import io.github.patorinaldi.gastos.api.repository.HouseholdRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.auth.InvalidSessionException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M2, hogares (RF-08 a RF-12).
 *
 * <p>Cada operación recibe al usuario autenticado y trabaja sobre su hogar actual, que la sesión
 * leyó de la base (RN-18). Ningún método acepta el identificador de un hogar: no hay forma de
 * pedirle a este servicio uno ajeno.
 *
 * <p>Ni {@code households} ni {@code users} tienen RLS, así que las consultas se acotan por hogar a
 * mano, siempre con el del principal.
 */
@Service
public class HouseholdService {

    private final HouseholdRepository households;
    private final UserRepository users;

    public HouseholdService(HouseholdRepository households, UserRepository users) {
        this.households = households;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public HouseholdView get(AuthenticatedUser user) {
        return view(currentHousehold(user));
    }

    /** RF-11. El nombre llega validado como no vacío; se guarda sin los espacios de los extremos. */
    @Transactional
    public HouseholdView rename(AuthenticatedUser user, String name) {
        Household household = currentHousehold(user);
        household.setName(name.strip());
        return view(household);
    }

    /** RF-10. Ordenados por nombre, para que el listado sea estable. */
    @Transactional(readOnly = true)
    public List<Member> members(AuthenticatedUser user) {
        return users.findByHouseholdIdOrderByNameAsc(user.householdId()).stream()
                .map(member -> new Member(member.getId(), member.getName(), member.getEmail()))
                .toList();
    }

    // La sesión ya comprobó que el hogar existe y está activo. Si dejó de existir entre ese momento
    // y este, corresponde volver a iniciar sesión, como en AuthService.currentUser.
    private Household currentHousehold(AuthenticatedUser user) {
        return households.findById(user.householdId()).orElseThrow(InvalidSessionException::new);
    }

    private static HouseholdView view(Household household) {
        return new HouseholdView(household.getId(), household.getName(), household.getCreatedAt());
    }
}
