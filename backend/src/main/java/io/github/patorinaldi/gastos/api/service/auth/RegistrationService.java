package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.domain.Household;
import io.github.patorinaldi.gastos.api.domain.User;
import io.github.patorinaldi.gastos.api.repository.HouseholdRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.security.HouseholdContext;
import io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Alta de una cuenta con su hogar y su catálogo inicial (RF-01, RF-02, CU-01).
 *
 * <p>Las tres cosas se crean juntas o no se crea ninguna (RN-11): una cuenta sin hogar no podría
 * operar, y un hogar sin catálogo dejaría al motor de reglas sin nada que aplicar.
 *
 * <p>La cuenta nace sin verificar y no puede iniciar sesión hasta confirmar el correo (RN-02). El
 * token de verificación y su envío son del punto de entrada de verificación, no de este.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    // Código SQLSTATE de una violación de clave única, y el índice que hace cumplir RN-01.
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String EMAIL_INDEX = "users_email_key";

    private final UserRepository users;
    private final HouseholdRepository households;
    private final PasswordEncoder passwordEncoder;
    private final HouseholdContext householdContext;
    private final RegistrationAttemptLimiter attemptLimiter;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    public RegistrationService(UserRepository users,
                               HouseholdRepository households,
                               PasswordEncoder passwordEncoder,
                               HouseholdContext householdContext,
                               RegistrationAttemptLimiter attemptLimiter,
                               JdbcTemplate jdbcTemplate,
                               PlatformTransactionManager transactionManager) {
        this.users = users;
        this.households = households;
        this.passwordEncoder = passwordEncoder;
        this.householdContext = householdContext;
        this.attemptLimiter = attemptLimiter;
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Registra la cuenta y devuelve sus identificadores.
     *
     * <p>El orden es el de menor a mayor costo: el límite de intentos, después la comprobación de
     * que el correo esté libre, que es una consulta, y recién al final bcrypt, que tarda del orden
     * de 100 ms. Así un alta repetida o rechazada no paga el hash.
     *
     * <p>El método no es {@code @Transactional} y la transacción se abre adentro, por dos motivos.
     * Uno es el mismo que en {@code AuthService.login}: una transacción retiene una conexión del
     * pool mientras dura, y bcrypt corre antes de abrirla. El otro es que la violación de la clave
     * única tiene que atraparse una vez que la transacción ya revirtió.
     *
     * @param clientIp dirección desde la que llega el intento, sin puerto, para el límite de altas
     * @throws TooManyAttemptsException       si esa dirección superó el límite (RNF-31)
     * @throws EmailAlreadyRegisteredException si ya hay una cuenta con ese correo (RN-01)
     */
    public Registration register(String name, String email, String password, String clientIp) {
        attemptLimiter.start(clientIp);

        // Comprobación temprana para responder 409 sin hashear. No alcanza por sí sola: dos altas
        // simultáneas con el mismo correo la pasan las dos, y ahí decide users_email_key.
        if (users.existsByEmailIgnoreCase(email)) {
            throw alreadyRegistered(email);
        }

        Registration registration = create(name.strip(), email, passwordEncoder.encode(password));
        log.info("Alta del usuario {} con su hogar {}", registration.userId(), registration.householdId());
        return registration;
    }

    /**
     * Crea el hogar, el usuario y el catálogo en una sola transacción. Sin límite ni
     * comprobaciones previas: es el camino que decide el índice único, y se puede ejercer solo.
     *
     * <p>Solo la violación de {@code users_email_key} se informa como correo repetido. Cualquier
     * otra violación de integridad es un error real, con otra causa, y se relanza para que no quede
     * disfrazada de "ya existe una cuenta".
     */
    Registration create(String name, String email, String passwordHash) {
        try {
            return transactions.execute(status -> createAccount(name, email, passwordHash));
        } catch (DataIntegrityViolationException ex) {
            if (violatesUniqueEmail(ex)) {
                throw alreadyRegistered(email);
            }
            throw ex;
        }
    }

    private Registration createAccount(String name, String email, String passwordHash) {
        Household household = households.saveAndFlush(new Household(householdNameFor(name)));

        // El hogar no existía cuando empezó la transacción, así que HouseholdTransactionListener no
        // pudo fijarlo. La siembra escribe en categories y category_rules, que están bajo RLS: sin
        // el hogar en contexto, las políticas rechazan cada fila.
        householdContext.bind(household.getId());

        User user = users.saveAndFlush(new User(household.getId(), email, passwordHash, name));
        jdbcTemplate.execute("select seed_household_defaults()");

        return new Registration(user.getId(), household.getId(), user.isEmailVerified());
    }

    /** El hogar arranca con el nombre de quien lo crea, y se renombra desde M2 (RF-11). */
    private static String householdNameFor(String name) {
        return "Casa de " + name;
    }

    // Se reconoce por el código SQLSTATE y el nombre del índice, que es lo que no cambia aunque
    // cambie la excepción con que cada capa envuelve al error de la base.
    private static boolean violatesUniqueEmail(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && UNIQUE_VIOLATION.equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && sql.getMessage().contains(EMAIL_INDEX)) {
                return true;
            }
        }
        return false;
    }

    // El correo va como hash en el log (RNF-09): alcanza para ver si un mismo correo insiste.
    private static EmailAlreadyRegisteredException alreadyRegistered(String email) {
        log.info("Alta rechazada: ya existe una cuenta con el correo {}", LogHash.ofEmail(email));
        return new EmailAlreadyRegisteredException();
    }
}
