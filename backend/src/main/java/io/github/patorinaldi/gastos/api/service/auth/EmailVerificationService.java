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
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verificación del correo (RF-03, HU-02, RN-02, RN-03): el enlace que se manda al registrarse, su
 * reenvío y la confirmación.
 *
 * <p>Ninguna de las tablas que toca tiene RLS ({@code users}, {@code auth_tokens}), así que no hace
 * falta un hogar en contexto.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final String PURPOSE = AuthTokenPurpose.EMAIL_VERIFICATION.databaseValue();

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final AuthTokenValues tokenValues;
    private final EmailSender emailSender;
    private final EmailLinkLimiter limiter;
    private final WebUrlProperties web;
    private final Executor executor;
    private final TransactionTemplate transactions;

    public EmailVerificationService(UserRepository users,
                                    AuthTokenRepository tokens,
                                    AuthTokenValues tokenValues,
                                    EmailSender emailSender,
                                    EmailLinkLimiter limiter,
                                    WebUrlProperties web,
                                    @Qualifier("applicationTaskExecutor") Executor executor,
                                    PlatformTransactionManager transactionManager) {
        this.users = users;
        this.tokens = tokens;
        this.tokenValues = tokenValues;
        this.emailSender = emailSender;
        this.limiter = limiter;
        this.web = web;
        this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Si se pueden mandar enlaces de verificación. Sin eso, una cuenta nueva no podría verificarse
     * nunca: el alta lo consulta antes de crear nada.
     */
    public boolean canSendLinks() {
        return emailSender.canSendSecrets();
    }

    /**
     * Emite un token de verificación para el usuario y devuelve el valor en claro, que va solo en el
     * correo. El alta lo llama dentro de su transacción: la cuenta y su token se crean juntos o no se
     * crea ninguno, y si el alta revierte no queda un token suelto.
     */
    public String issueToken(UUID userId) {
        String token = tokenValues.generate();
        tokens.save(new AuthToken(userId, AuthTokenPurpose.EMAIL_VERIFICATION, tokenValues.hash(token)));
        return token;
    }

    /**
     * Manda el enlace en segundo plano. El alta lo llama después de confirmar la transacción: un
     * proveedor de correo lento o caído no demora ni hace fallar el alta. Si el correo no sale, queda
     * en el log, y la persona puede pedir que se lo reenvíen.
     *
     * <p>Tampoco lanza si el ejecutor rechaza la tarea, saturado o apagándose: la cuenta ya está
     * creada, y un 500 haría que la persona reintente y reciba "correo ya registrado".
     */
    public void sendLater(String email, String name, String token) {
        try {
            executor.execute(() -> {
                try {
                    emailSender.send(verificationMessage(email, name, token));
                } catch (RuntimeException e) {
                    log.error("No se pudo enviar el enlace de verificación: correo={}, causa={}",
                            LogHash.ofEmail(email), LogCause.of(e));
                }
            });
        } catch (RuntimeException e) {
            log.error("No se pudo programar el envío del enlace de verificación: correo={}", LogHash.ofEmail(email), e);
        }
    }

    /**
     * Reenvía el enlace de verificación (HU-02, token vencido). Responde igual exista o no la cuenta,
     * y esté o no verificada, por lo mismo que el pedido de restablecimiento: buscarla y enviar el
     * correo corren en segundo plano, después de responder. Cuenta en el mismo límite que el
     * restablecimiento, porque los dos correos llegan a la misma casilla (RNF-32).
     *
     * @param clientIp dirección del cliente, sin puerto, para el límite de pedidos
     * @throws EmailDeliveryUnavailableException si el envío de correos no puede llevar el enlace
     * @throws io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException si se superó
     *         el límite de pedidos (RNF-32)
     */
    public void requestResend(String email, String clientIp) {
        if (!canSendLinks()) {
            throw new EmailDeliveryUnavailableException();
        }
        limiter.count(email, clientIp);
        try {
            executor.execute(() -> resend(email));
        } catch (RuntimeException e) {
            // La respuesta es la misma pase lo que pase en segundo plano: un 500 acá la distinguiría.
            log.error("No se pudo programar el reenvío de verificación: correo={}", LogHash.ofEmail(email), e);
        }
    }

    /**
     * Emite un enlace nuevo y, recién cuando salió, anula los anteriores: un reenvío deja un solo
     * enlace vigente. Si el envío falla, se anula el nuevo, que nadie recibió, y los anteriores siguen
     * sirviendo.
     *
     * <p>Anula solo los creados antes que el nuevo: de dos reenvíos simultáneos queda vigente el
     * último, en lugar de anularse uno al otro. Bloquea primero al usuario, el mismo orden de locks
     * que {@link #verify}, para no trabarse con una confirmación que esté anulando los mismos tokens.
     */
    void resend(String email) {
        try {
            Optional<User> found = users.findByEmailIgnoreCase(email);
            if (found.isEmpty()) {
                log.info("Reenvío de verificación pedido para un correo sin cuenta: correo={}", LogHash.ofEmail(email));
                return;
            }
            User user = found.get();
            if (user.isEmailVerified()) {
                log.info("Reenvío de verificación pedido para una cuenta ya verificada: usuario={}", user.getId());
                return;
            }
            String token = tokenValues.generate();
            AuthToken issued = tokens.save(
                    new AuthToken(user.getId(), AuthTokenPurpose.EMAIL_VERIFICATION, tokenValues.hash(token)));
            try {
                emailSender.send(verificationMessage(user.getEmail(), user.getName(), token));
            } catch (RuntimeException e) {
                transactions.executeWithoutResult(status -> tokens.consume(issued.getId()));
                throw e;
            }
            transactions.executeWithoutResult(status -> {
                users.lockById(user.getId());
                tokens.consumeOlderThan(user.getId(), PURPOSE, issued.getId());
            });
            log.info("Enlace de verificación reenviado: usuario={}", user.getId());
        } catch (RuntimeException e) {
            log.error("No se pudo reenviar el enlace de verificación: correo={}, causa={}",
                    LogHash.ofEmail(email), LogCause.of(e));
        }
    }

    // El token va en el fragmento, como en el restablecimiento: no llega a los logs del hosting.
    private EmailMessage verificationMessage(String email, String name, String token) {
        String link = web.link("/verify-email#token=" + token);
        String body = """
                Hola, %s:

                Para terminar de crear tu cuenta en Gastos, confirmá tu correo abriendo este enlace:

                %s

                El enlace vence en 24 horas y sirve una sola vez. Si venció, podés pedir otro desde la \
                pantalla de inicio de sesión. Si no creaste una cuenta, ignorá este correo.
                """.formatted(name, link);
        return new EmailMessage(email, "Confirmá tu correo en Gastos", body);
    }

    /**
     * Confirma el correo con el token del enlace (HU-02). Deja sin efecto los demás enlaces de
     * verificación pendientes.
     *
     * <p>Mismo orden de locks que el restablecimiento: primero el usuario y después los tokens. Así
     * dos enlaces de la misma persona abiertos a la vez no se esperan entre sí: el segundo espera al
     * usuario y encuentra su token ya anulado.
     *
     * @throws VerificationTokenUnavailableException si el token no existe, venció o ya se usó
     */
    public void verify(String token) {
        if (!tokenValues.isWellFormed(token)) {
            throw new VerificationTokenUnavailableException();
        }
        AuthToken found = tokens.findUsable(tokenValues.hash(token), PURPOSE)
                .orElseThrow(VerificationTokenUnavailableException::new);

        transactions.executeWithoutResult(status -> {
            User user = users.lockById(found.getUserId()).orElseThrow(VerificationTokenUnavailableException::new);
            if (tokens.consume(found.getId()) == 0) {
                throw new VerificationTokenUnavailableException();
            }
            user.setEmailVerified(true);
            tokens.consumeAll(user.getId(), PURPOSE);
        });
        log.info("Correo verificado: usuario={}", found.getUserId());
    }
}
