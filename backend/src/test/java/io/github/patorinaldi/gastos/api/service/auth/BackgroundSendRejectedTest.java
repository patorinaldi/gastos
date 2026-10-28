package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.config.WebUrlProperties;
import io.github.patorinaldi.gastos.api.repository.AuthTokenRepository;
import io.github.patorinaldi.gastos.api.repository.MachineTokenRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.service.email.EmailSender;
import java.net.URI;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El ejecutor de los envíos en segundo plano rechaza la tarea, saturado o apagándose. Lo que ya se
 * respondió, o se creó, no puede convertirse en un 500: el alta ya creó la cuenta, y los pedidos de
 * enlaces responden igual pase lo que pase en segundo plano.
 */
class BackgroundSendRejectedTest {

    private final EmailSender emailSender = mock(EmailSender.class);
    private final Executor executor = mock(Executor.class);
    private final WebUrlProperties web = new WebUrlProperties(URI.create("https://gastos.ejemplo"));

    private final EmailVerificationService verification = new EmailVerificationService(
            mock(UserRepository.class),
            mock(AuthTokenRepository.class),
            new AuthTokenValues(),
            emailSender,
            mock(EmailLinkLimiter.class),
            web,
            executor,
            mock(PlatformTransactionManager.class));

    private final PasswordResetService passwordReset = new PasswordResetService(
            mock(UserRepository.class),
            mock(AuthTokenRepository.class),
            mock(MachineTokenRepository.class),
            new AuthTokenValues(),
            mock(PasswordEncoder.class),
            emailSender,
            mock(EmailLinkLimiter.class),
            web,
            executor,
            mock(PlatformTransactionManager.class));

    @BeforeEach
    void ejecutorQueRechaza() {
        when(emailSender.canSendSecrets()).thenReturn(true);
        doThrow(new TaskRejectedException("cola llena")).when(executor).execute(any());
    }

    @Test
    void elEnvioDelAltaNoLanza() {
        assertThatNoException().isThrownBy(() -> verification.sendLater("ana@ejemplo.test", "Ana", "token"));
    }

    @Test
    void elReenvioDeLaVerificacionNoLanza() {
        assertThatNoException().isThrownBy(() -> verification.requestResend("ana@ejemplo.test", "203.0.113.7"));
    }

    @Test
    void elPedidoDeRestablecimientoNoLanza() {
        assertThatNoException().isThrownBy(() -> passwordReset.requestReset("ana@ejemplo.test", "203.0.113.7"));
    }
}
