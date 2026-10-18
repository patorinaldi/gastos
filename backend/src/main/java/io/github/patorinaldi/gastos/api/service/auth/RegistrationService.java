package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.domain.Household;
import io.github.patorinaldi.gastos.api.domain.User;
import io.github.patorinaldi.gastos.api.repository.HouseholdRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
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

    private final UserRepository users;
    private final HouseholdRepository households;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    public RegistrationService(UserRepository users,
                               HouseholdRepository households,
                               PasswordEncoder passwordEncoder,
                               JdbcTemplate jdbcTemplate,
                               PlatformTransactionManager transactionManager) {
        this.users = users;
        this.households = households;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Registra la cuenta y devuelve sus identificadores.
     *
     * <p>El método no es {@code @Transactional} y la transacción se abre adentro, por dos motivos.
     * Uno es el mismo que en {@code AuthService.login}: bcrypt tarda del orden de 100 ms y una
     * transacción retiene una conexión del pool mientras dura, así que el hash se calcula antes de
     * abrirla. El otro es que la violación de la clave única tiene que atraparse una vez que la
     * transacción ya revirtió; atraparla adentro dejaría una transacción marcada para rollback.
     *
     * @throws EmailAlreadyRegisteredException si ya hay una cuenta con ese correo (RN-01)
     */
    public Registration register(String name, String email, String password) {
        String cleanName = name.strip();
        String cleanEmail = email.strip();
        String passwordHash = passwordEncoder.encode(password);

        // Comprobación temprana para responder 409 sin intentar el alta. No alcanza por sí sola:
        // dos altas simultáneas con el mismo correo la pasan las dos, y ahí decide users_email_key.
        if (users.existsByEmailIgnoreCase(cleanEmail)) {
            throw alreadyRegistered(cleanEmail);
        }

        Registration registration;
        try {
            registration = transactions.execute(status -> createAccount(cleanName, cleanEmail, passwordHash));
        } catch (DataIntegrityViolationException ex) {
            throw alreadyRegistered(cleanEmail);
        }

        log.info("Alta del usuario {} con su hogar {}", registration.userId(), registration.householdId());
        return registration;
    }

    private Registration createAccount(String name, String email, String passwordHash) {
        Household household = households.saveAndFlush(new Household(householdNameFor(name)));

        // El hogar no existía cuando empezó la transacción, así que HouseholdTransactionListener no
        // pudo fijarlo. Se fija acá, con el mismo alcance transaccional que usa el listener, porque
        // la siembra escribe en categories y category_rules, que están bajo RLS: sin esto, las
        // políticas rechazan cada fila.
        jdbcTemplate.queryForObject(
                "select set_config('app.current_household', ?, true)",
                String.class, household.getId().toString());

        User user = users.saveAndFlush(new User(household.getId(), email, passwordHash, name));
        jdbcTemplate.execute("select seed_household_defaults()");

        return new Registration(user.getId(), household.getId(), user.isEmailVerified());
    }

    /** El hogar arranca con el nombre de quien lo crea, y se renombra desde M2 (RF-11). */
    private static String householdNameFor(String name) {
        return "Casa de " + name;
    }

    // El correo queda en el log del servidor, que es donde ya está el de cada alta exitosa.
    private static EmailAlreadyRegisteredException alreadyRegistered(String email) {
        log.info("Alta rechazada: ya existe una cuenta con el correo {}", email);
        return new EmailAlreadyRegisteredException();
    }
}
