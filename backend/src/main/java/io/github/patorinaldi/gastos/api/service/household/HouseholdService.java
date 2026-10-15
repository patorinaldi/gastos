package io.github.patorinaldi.gastos.api.service.household;

import io.github.patorinaldi.gastos.api.domain.Household;
import io.github.patorinaldi.gastos.api.domain.HouseholdInvitation;
import io.github.patorinaldi.gastos.api.repository.HouseholdInvitationRepository;
import io.github.patorinaldi.gastos.api.repository.HouseholdRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.auth.InvalidSessionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /** Vigencia de un código de invitación (RN-05). La misma que el default de la columna. */
    static final Duration INVITATION_VALIDITY = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(HouseholdService.class);

    private final HouseholdRepository households;
    private final UserRepository users;
    private final HouseholdInvitationRepository invitations;
    private final InvitationCodes codes;
    private final Clock clock;

    public HouseholdService(HouseholdRepository households,
                            UserRepository users,
                            HouseholdInvitationRepository invitations,
                            InvitationCodes codes,
                            Clock clock) {
        this.households = households;
        this.users = users;
        this.invitations = invitations;
        this.codes = codes;
        this.clock = clock;
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

    /**
     * RF-08. Genera un código para el hogar del usuario. Se guarda solo su hash, así que el código en
     * claro se devuelve esta única vez y no se registra en el log.
     *
     * <p>El vencimiento se trunca a segundos: es lo que necesita quien lo lee, y así el valor
     * respondido coincide exactamente con el guardado.
     */
    @Transactional
    public IssuedInvitation createInvitation(AuthenticatedUser user) {
        String code = codes.generate();
        Instant expiresAt = clock.instant().truncatedTo(ChronoUnit.SECONDS).plus(INVITATION_VALIDITY);
        HouseholdInvitation invitation = invitations.save(
                new HouseholdInvitation(user.householdId(), user.userId(), codes.hash(code), expiresAt));
        log.info("Invitación {} generada por el usuario {} para el hogar {}",
                invitation.getId(), user.userId(), user.householdId());
        return new IssuedInvitation(code, expiresAt);
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
