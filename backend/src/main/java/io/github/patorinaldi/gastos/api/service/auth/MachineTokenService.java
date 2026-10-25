package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.config.MachineTokenLimitProperties;
import io.github.patorinaldi.gastos.api.domain.MachineToken;
import io.github.patorinaldi.gastos.api.domain.User;
import io.github.patorinaldi.gastos.api.repository.MachineTokenRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tokens de cliente máquina (RF-07, HU-17): los emite y los revoca la persona, desde su sesión. Con
 * uno, una automatización carga gastos en su hogar a su nombre por la API de integración (M6), y
 * solo ahí: el resto de la API exige sesión.
 *
 * <p>Cada persona ve y revoca solo los suyos, aunque comparta hogar: un token es una credencial de
 * quien lo emitió, y los gastos que carga quedan a su nombre.
 */
@Service
public class MachineTokenService {

    private static final Logger log = LoggerFactory.getLogger(MachineTokenService.class);

    /** Cuánto siguen apareciendo en el listado los tokens revocados. */
    static final Duration REVOKED_LISTED_FOR = Duration.ofDays(30);

    private final MachineTokenRepository tokens;
    private final UserRepository users;
    private final MachineTokenValues values;
    private final MachineTokenLimitProperties limits;
    private final MachineTokenIssueLimiter issueLimiter;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public MachineTokenService(MachineTokenRepository tokens,
                               UserRepository users,
                               MachineTokenValues values,
                               MachineTokenLimitProperties limits,
                               MachineTokenIssueLimiter issueLimiter,
                               PasswordEncoder passwordEncoder,
                               Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.tokens = tokens;
        this.users = users;
        this.values = values;
        this.limits = limits;
        this.issueLimiter = issueLimiter;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Emite un token para el hogar actual de la persona y devuelve su valor en claro, que no se puede
     * volver a obtener.
     *
     * <p>Pide la contraseña actual: el token no vence, y sin ella una sesión robada alcanzaría para
     * convertirse en un acceso permanente. Antes cuenta el pedido (RNF-33), que acota también las
     * pruebas de contraseña. bcrypt corre fuera de la transacción, por lo mismo que en el login: no
     * retiene una conexión ni el lock del usuario mientras tanto.
     *
     * <p>La fila del usuario se bloquea primero, igual que en el canje de invitaciones. Así una
     * emisión y un cambio de hogar simultáneos no se cruzan: o el token se emite antes y el canje lo
     * revoca, o se emite después, ya para el hogar nuevo. El hogar sale de esa fila bloqueada y no de
     * la sesión, que se leyó antes. El bloqueo también serializa dos emisiones de la misma persona,
     * para que no pasen las dos el tope con un lugar libre.
     *
     * @throws io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException si superó el
     *         límite de pedidos de emisión (RNF-33)
     * @throws PasswordConfirmationFailedException si la contraseña no es la de la cuenta
     * @throws TooManyActiveMachineTokensException si ya tiene el máximo vigente (RNF-33)
     */
    public IssuedMachineToken issue(AuthenticatedUser principal, String name, String password) {
        issueLimiter.count(principal.userId());
        User current = users.findById(principal.userId()).orElseThrow(InvalidSessionException::new);
        if (!passwordEncoder.matches(password, current.getPasswordHash())) {
            log.info("Emisión de token de cliente máquina rechazada para el usuario {}: contraseña incorrecta",
                    principal.userId());
            throw new PasswordConfirmationFailedException();
        }
        return transactions.execute(status -> issueLocked(principal, name));
    }

    private IssuedMachineToken issueLocked(AuthenticatedUser principal, String name) {
        User user = users.lockById(principal.userId()).orElseThrow(InvalidSessionException::new);
        UUID household = user.getHouseholdId();
        long active = tokens.countByUserIdAndHouseholdIdAndRevokedAtIsNull(user.getId(), household);
        if (active >= limits.maxActivePerUser()) {
            log.info("Token de cliente máquina rechazado para el usuario {}: ya tiene {} vigentes", user.getId(), active);
            throw new TooManyActiveMachineTokensException(limits.maxActivePerUser());
        }

        String token = values.generate();
        // saveAndFlush: la fecha de creación se completa al insertar, y va en la respuesta.
        MachineToken saved = tokens.saveAndFlush(new MachineToken(household, user.getId(), name.strip(), values.hash(token)));
        log.info("Token de cliente máquina {} emitido por el usuario {} para el hogar {}",
                saved.getId(), user.getId(), household);
        return new IssuedMachineToken(saved.getId(), saved.getName(), token, saved.getCreatedAt());
    }

    /**
     * Los tokens de la persona en su hogar actual, del más nuevo al más viejo: los vigentes y los
     * revocados en los últimos {@link #REVOKED_LISTED_FOR}. Los revocados no se borran, y sin ese corte
     * el listado crecería sin límite.
     */
    @Transactional(readOnly = true)
    public List<MachineTokenView> list(AuthenticatedUser principal) {
        return tokens.findListed(principal.userId(), principal.householdId(), clock.instant().minus(REVOKED_LISTED_FOR))
                .stream()
                .map(t -> new MachineTokenView(t.getId(), t.getName(), t.getCreatedAt(), t.getLastUsedAt(), t.getRevokedAt()))
                .toList();
    }

    /**
     * Revoca uno de los tokens de la persona. Revocar uno ya revocado no hace nada y no es un error:
     * el resultado que se pidió ya se cumple. Las capturas que lleguen con él se rechazan desde ahora,
     * y la sesión web sigue activa (HU-17).
     *
     * <p>Solo alcanza a los tokens del hogar actual, los mismos que muestra el listado. Los de un hogar
     * anterior ya los revocó el cambio de hogar (RN-18), y responden igual que uno inexistente.
     *
     * @throws MachineTokenNotFoundException si el token no existe, es de otra persona o de otro hogar
     */
    @Transactional
    public void revoke(AuthenticatedUser principal, UUID tokenId) {
        if (tokens.revoke(tokenId, principal.userId(), principal.householdId(), clock.instant()) > 0) {
            log.info("Token de cliente máquina {} revocado por el usuario {}", tokenId, principal.userId());
            return;
        }
        if (!tokens.existsByIdAndUserIdAndHouseholdId(tokenId, principal.userId(), principal.householdId())) {
            throw new MachineTokenNotFoundException();
        }
    }
}
