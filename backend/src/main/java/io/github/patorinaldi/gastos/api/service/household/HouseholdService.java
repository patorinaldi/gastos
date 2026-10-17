package io.github.patorinaldi.gastos.api.service.household;

import io.github.patorinaldi.gastos.api.config.InvitationLimitProperties;
import io.github.patorinaldi.gastos.api.domain.Household;
import io.github.patorinaldi.gastos.api.domain.HouseholdInvitation;
import io.github.patorinaldi.gastos.api.domain.User;
import io.github.patorinaldi.gastos.api.repository.HouseholdInvitationRepository;
import io.github.patorinaldi.gastos.api.repository.HouseholdRepository;
import io.github.patorinaldi.gastos.api.repository.MachineTokenRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.auth.InvalidSessionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final MachineTokenRepository machineTokens;
    private final InvitationCodes codes;
    private final InvitationLimitProperties limits;
    private final Clock clock;

    public HouseholdService(HouseholdRepository households,
                            UserRepository users,
                            HouseholdInvitationRepository invitations,
                            MachineTokenRepository machineTokens,
                            InvitationCodes codes,
                            InvitationLimitProperties limits,
                            Clock clock) {
        this.households = households;
        this.users = users;
        this.invitations = invitations;
        this.machineTokens = machineTokens;
        this.codes = codes;
        this.limits = limits;
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
     *
     * <p>Un hogar no puede tener más de {@link InvitationLimitProperties#maxActivePerHousehold()}
     * invitaciones vigentes (RNF-30). El hogar se bloquea antes de contarlas: sin el bloqueo, dos
     * pedidos simultáneos contarían las mismas y pasarían el tope los dos. Es {@code for no key
     * update}, así que no frena la carga de gastos; ver {@link HouseholdRepository#lockAll}.
     *
     * @throws TooManyActiveInvitationsException si el hogar ya tiene el máximo
     */
    @Transactional
    public IssuedInvitation createInvitation(AuthenticatedUser user) {
        Instant now = clock.instant();
        households.lockAll(List.of(user.householdId()));
        long active = invitations.countByHouseholdIdAndRedeemedAtIsNullAndExpiresAtAfter(user.householdId(), now);
        if (active >= limits.maxActivePerHousehold()) {
            log.info("Invitación rechazada para el usuario {}: el hogar {} ya tiene {} vigentes",
                    user.userId(), user.householdId(), active);
            throw new TooManyActiveInvitationsException(limits.maxActivePerHousehold());
        }

        String code = codes.generate();
        Instant expiresAt = now.truncatedTo(ChronoUnit.SECONDS).plus(INVITATION_VALIDITY);
        HouseholdInvitation invitation = invitations.save(
                new HouseholdInvitation(user.householdId(), user.userId(), codes.hash(code), expiresAt));
        log.info("Invitación {} generada por el usuario {} para el hogar {}",
                invitation.getId(), user.userId(), user.householdId());
        return new IssuedInvitation(code, expiresAt);
    }

    /**
     * RF-09. Valida el código y describe qué pasaría al canjearlo, sin aplicar nada. Es lo que la
     * interfaz muestra para pedir la confirmación que exige RN-18.
     *
     * <p>Responde exactamente lo mismo que {@link #redeem} ante un código inválido, porque usan la
     * misma validación. Sin bloqueos: el canje repite todo bajo bloqueo, así que lo que se vea acá
     * puede cambiar antes de confirmar, pero nunca quedar aplicado a medias.
     */
    @Transactional(readOnly = true)
    public InvitationPreview preview(AuthenticatedUser user, String code) {
        HouseholdInvitation invitation = invitations.findByCodeHash(codes.hash(code))
                .orElseThrow(() -> unavailable(user.userId(), "inexistente"));
        Household target = households.findById(invitation.getHouseholdId()).orElse(null);
        validate(invitation, target, user.householdId(), user.userId(), clock.instant());

        boolean willBeArchived = users.countByHouseholdId(user.householdId()) == 1;
        long tokens = machineTokens.countByUserIdAndHouseholdIdAndRevokedAtIsNull(user.userId(), user.householdId());
        return new InvitationPreview(target.getName(), willBeArchived, Math.toIntExact(tokens));
    }

    /**
     * RF-09, RF-12, RN-18. Canjea el código: el usuario deja su hogar y pasa al de la invitación, en
     * una sola transacción (CU-03, paso 6). Sus gastos quedan en el hogar anterior; sus tokens de
     * cliente máquina de ese hogar se revocan; si el hogar quedó sin integrantes, se archiva.
     *
     * <p>No depende de que antes se haya llamado a {@link #preview}: repite toda la validación. La
     * confirmación explícita la exige el contrato de la petición antes de llegar acá.
     *
     * <p>Bloqueos, siempre en este orden para que dos canjes no puedan esperarse mutuamente:
     * <ol>
     *   <li>el usuario: dos canjes de la misma persona se serializan, y el hogar que deja se lee
     *       bajo bloqueo y no del principal, que pudo quedar viejo;</li>
     *   <li>la invitación: dos personas que canjean el mismo código se serializan, y la segunda la
     *       encuentra ya canjeada;</li>
     *   <li>los dos hogares, en orden de id: los dos últimos integrantes que se van a la vez no
     *       pueden dejar de archivar el hogar, y nadie puede sumarse a uno que se está archivando.</li>
     * </ol>
     */
    @Transactional
    public Redemption redeem(AuthenticatedUser principal, String code) {
        Instant now = clock.instant();
        User user = users.lockById(principal.userId()).orElseThrow(InvalidSessionException::new);
        UUID previousId = user.getHouseholdId();
        HouseholdInvitation invitation = invitations.lockByCodeHash(codes.hash(code))
                .orElseThrow(() -> unavailable(user.getId(), "inexistente"));
        // Con un código del hogar propio los dos son el mismo: lo rechaza validate, con 409.
        List<UUID> involved = Stream.of(previousId, invitation.getHouseholdId()).distinct().toList();
        Map<UUID, Household> locked = households.lockAll(involved).stream()
                .collect(Collectors.toMap(Household::getId, Function.identity()));
        Household target = locked.get(invitation.getHouseholdId());
        validate(invitation, target, previousId, user.getId(), now);

        user.moveTo(target.getId());
        invitation.redeem(user.getId(), now);
        int revoked = machineTokens.revokeActive(user.getId(), previousId, now);
        // El conteo ve el cambio de hogar de arriba: Hibernate lo escribe antes de la consulta.
        boolean archived = users.countByHouseholdId(previousId) == 0;
        if (archived) {
            locked.get(previousId).archive(now);
        }

        log.info("Usuario {} pasó del hogar {} al {} con la invitación {}: {} tokens revocados, hogar anterior {}",
                user.getId(), previousId, target.getId(), invitation.getId(), revoked,
                archived ? "archivado" : "activo");
        return new Redemption(target.getId(), target.getName(), archived);
    }

    /**
     * La validación de {@link #preview} y {@link #redeem}. El 410 se decide antes que el 409: un
     * código vencido o canjeado del hogar propio no confirma que ese código existió.
     */
    private static void validate(HouseholdInvitation invitation, Household target,
                                 UUID currentHouseholdId, UUID userId, Instant now) {
        if (invitation.isRedeemed()) {
            throw unavailable(userId, "ya canjeada");
        }
        if (invitation.isExpiredAt(now)) {
            throw unavailable(userId, "vencida");
        }
        if (target == null || !target.isActive()) {
            throw unavailable(userId, "hogar archivado");
        }
        if (target.getId().equals(currentHouseholdId)) {
            log.info("Invitación rechazada para el usuario {}: es de su propio hogar", userId);
            throw new AlreadyInHouseholdException();
        }
    }

    // El motivo queda solo en el log; la respuesta es la misma para todos. Nunca el código.
    private static InvitationUnavailableException unavailable(UUID userId, String reason) {
        log.info("Invitación rechazada para el usuario {}: {}", userId, reason);
        return new InvitationUnavailableException();
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
