package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.config.WebUrlProperties;
import io.github.patorinaldi.gastos.api.domain.AuthToken;
import io.github.patorinaldi.gastos.api.domain.AuthTokenPurpose;
import io.github.patorinaldi.gastos.api.domain.User;
import io.github.patorinaldi.gastos.api.repository.AuthTokenRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.service.email.EmailMessage;
import io.github.patorinaldi.gastos.api.service.email.EmailSender;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Restablecimiento de contraseña (RF-06, CU-08): pedir el enlace por correo y canjearlo por una
 * contraseña nueva.
 *
 * <p>Ninguna de las tablas que toca tiene RLS ({@code users}, {@code auth_tokens}), así que no hace
 * falta un hogar en contexto: los dos pasos corren sin sesión.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final String PURPOSE = AuthTokenPurpose.PASSWORD_RESET.databaseValue();

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final AuthTokenValues tokenValues;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final PasswordResetLimiter limiter;
    private final WebUrlProperties web;
    private final Executor executor;
    private final TransactionTemplate transactions;

    public PasswordResetService(UserRepository users,
                                AuthTokenRepository tokens,
                                AuthTokenValues tokenValues,
                                PasswordEncoder passwordEncoder,
                                EmailSender emailSender,
                                PasswordResetLimiter limiter,
                                WebUrlProperties web,
                                @Qualifier("applicationTaskExecutor") Executor executor,
                                PlatformTransactionManager transactionManager) {
        this.users = users;
        this.tokens = tokens;
        this.tokenValues = tokenValues;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.limiter = limiter;
        this.web = web;
        this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Pide el enlace de restablecimiento para {@code email}. Responde igual exista o no la cuenta
     * (CU-08, 1a).
     *
     * <p>Buscar la cuenta, emitir el token y enviar el correo corren en segundo plano, después de
     * responder. Si corrieran dentro de la petición, quien llama podría averiguar qué correos tienen
     * cuenta por el tiempo de respuesta, que solo incluye el envío cuando la cuenta existe, o por un
     * 500 que solo aparece si el proveedor de correo falla al enviar.
     *
     * @param clientIp dirección del cliente, sin puerto, para el límite de pedidos
     * @throws io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException si se superó el límite de pedidos (RNF-32)
     */
    public void requestReset(String email, String clientIp) {
        limiter.count(email, clientIp);
        executor.execute(() -> sendResetLink(email));
    }

    void sendResetLink(String email) {
        try {
            Optional<User> found = users.findByEmailIgnoreCase(email);
            if (found.isEmpty()) {
                log.info("Restablecimiento pedido para un correo sin cuenta: correo={}", LogHash.ofEmail(email));
                return;
            }
            User user = found.get();
            String token = tokenValues.generate();
            tokens.save(new AuthToken(user.getId(), AuthTokenPurpose.PASSWORD_RESET, tokenValues.hash(token)));
            emailSender.send(resetMessage(user, token));
            log.info("Enlace de restablecimiento enviado: usuario={}", user.getId());
        } catch (RuntimeException e) {
            // Nadie espera este resultado: la respuesta ya salió. Queda en el log.
            log.error("No se pudo enviar el enlace de restablecimiento: correo={}", LogHash.ofEmail(email), e);
        }
    }

    // El token va en el fragmento (#) y no en la query: el navegador no manda el fragmento al
    // servidor ni en el header Referer, así que no queda en los logs del hosting ni de terceros.
    private EmailMessage resetMessage(User user, String token) {
        String link = web.link("/reset-password#token=" + token);
        String body = """
                Hola, %s:

                Recibimos un pedido para restablecer la contraseña de tu cuenta en Gastos. Para elegir \
                una nueva, abrí este enlace:

                %s

                El enlace vence en 24 horas y sirve una sola vez. Si no lo pediste, ignorá este correo: \
                tu contraseña sigue siendo la misma.
                """.formatted(user.getName(), link);
        return new EmailMessage(user.getEmail(), "Restablecer tu contraseña de Gastos", body);
    }

    /**
     * Cambia la contraseña con el token del enlace (CU-08, pasos 5 y 6). Cierra además todas las
     * sesiones abiertas del usuario (RNF-29) y deja sin efecto los demás enlaces que haya pedido.
     *
     * <p>El token se busca antes de correr bcrypt: un token inválido no cuesta más que una consulta.
     * bcrypt corre fuera de la transacción, por lo mismo que en el login: no retiene una conexión
     * del pool mientras calcula.
     *
     * @throws ResetTokenUnavailableException si el token no existe, venció o ya se usó
     */
    public void resetPassword(String token, String newPassword) {
        if (!tokenValues.isWellFormed(token)) {
            throw new ResetTokenUnavailableException();
        }
        AuthToken found = tokens.findUsable(tokenValues.hash(token), PURPOSE)
                .orElseThrow(ResetTokenUnavailableException::new);
        String passwordHash = passwordEncoder.encode(newPassword);

        transactions.executeWithoutResult(status -> {
            // Entre la búsqueda y acá, otro canje del mismo token pudo ganar.
            if (tokens.consume(found.getId()) == 0) {
                throw new ResetTokenUnavailableException();
            }
            User user = users.lockById(found.getUserId()).orElseThrow(ResetTokenUnavailableException::new);
            user.setPasswordHash(passwordHash);
            user.invalidateSessions();
            tokens.consumeAll(user.getId(), PURPOSE);
        });
        log.info("Contraseña restablecida: usuario={}", found.getUserId());
    }
}
