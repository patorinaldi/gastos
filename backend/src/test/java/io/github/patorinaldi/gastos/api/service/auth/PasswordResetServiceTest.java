package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.config.WebUrlProperties;
import io.github.patorinaldi.gastos.api.repository.AuthTokenRepository;
import io.github.patorinaldi.gastos.api.repository.MachineTokenRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.service.email.EmailSender;
import java.net.URI;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lo que no necesita la base. El recorrido completo lo cubre {@code PasswordResetTest}.
 */
class PasswordResetServiceTest {

    private final EmailSender emailSender = mock(EmailSender.class);
    private final PasswordResetLimiter limiter = mock(PasswordResetLimiter.class);
    private final Executor executor = mock(Executor.class);
    private final UserRepository users = mock(UserRepository.class);

    private final PasswordResetService service = new PasswordResetService(
            users,
            mock(AuthTokenRepository.class),
            mock(MachineTokenRepository.class),
            new AuthTokenValues(),
            mock(PasswordEncoder.class),
            emailSender,
            limiter,
            new WebUrlProperties(URI.create("https://gastos.ejemplo")),
            executor,
            mock(PlatformTransactionManager.class));

    // Lo que pasa en un despliegue con EMAIL_SENDER=console: el enlace terminaría en el log (RNF-09).
    @Test
    void conUnEnvioQueNoPuedeLlevarTokensElPedidoSeRechazaSinContarYSinBuscarLaCuenta() {
        when(emailSender.canSendSecrets()).thenReturn(false);

        assertThatThrownBy(() -> service.requestReset("ana@ejemplo.test", "203.0.113.7"))
                .isInstanceOf(PasswordResetUnavailableException.class);

        verifyNoInteractions(limiter, executor, users);
    }
}
