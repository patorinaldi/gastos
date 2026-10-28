package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.config.WebUrlProperties;
import io.github.patorinaldi.gastos.api.repository.AuthTokenRepository;
import io.github.patorinaldi.gastos.api.repository.HouseholdRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.security.HouseholdContext;
import io.github.patorinaldi.gastos.api.service.email.EmailSender;
import java.net.URI;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lo que pasa en un despliegue con {@code EMAIL_SENDER=console}: nada que mande un enlace de un solo
 * uso por correo se acepta, porque el enlace terminaría en el log (RNF-09). El restablecimiento lo
 * cubre {@code PasswordResetServiceTest}.
 */
class EmailDeliveryUnavailableTest {

    private final EmailSender emailSender = mock(EmailSender.class);
    private final EmailLinkLimiter linkLimiter = mock(EmailLinkLimiter.class);
    private final RegistrationAttemptLimiter registrationLimiter = mock(RegistrationAttemptLimiter.class);
    private final Executor executor = mock(Executor.class);
    private final UserRepository users = mock(UserRepository.class);

    private final EmailVerificationService verification = new EmailVerificationService(
            users,
            mock(AuthTokenRepository.class),
            new AuthTokenValues(),
            emailSender,
            linkLimiter,
            new WebUrlProperties(URI.create("https://gastos.ejemplo")),
            executor,
            mock(PlatformTransactionManager.class));

    private final RegistrationService registration = new RegistrationService(
            users,
            mock(HouseholdRepository.class),
            mock(PasswordEncoder.class),
            mock(HouseholdContext.class),
            registrationLimiter,
            verification,
            mock(JdbcTemplate.class),
            mock(PlatformTransactionManager.class));

    // Una cuenta que no puede recibir su enlace no se podría verificar nunca, y dejaría el correo
    // tomado por una cuenta inservible.
    @Test
    void elAltaSeRechazaAntesDeContarlaYDeCrearNada() {
        when(emailSender.canSendSecrets()).thenReturn(false);

        assertThatThrownBy(() -> registration.register("Ana", "ana@ejemplo.test", "contraseña-larga", "203.0.113.7"))
                .isInstanceOf(EmailDeliveryUnavailableException.class);

        verifyNoInteractions(registrationLimiter, users, executor);
    }

    @Test
    void elReenvioSeRechazaSinContarYSinBuscarLaCuenta() {
        when(emailSender.canSendSecrets()).thenReturn(false);

        assertThatThrownBy(() -> verification.requestResend("ana@ejemplo.test", "203.0.113.7"))
                .isInstanceOf(EmailDeliveryUnavailableException.class);

        verifyNoInteractions(linkLimiter, executor, users);
    }
}
