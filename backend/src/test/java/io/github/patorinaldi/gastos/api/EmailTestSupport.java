package io.github.patorinaldi.gastos.api;

import io.github.patorinaldi.gastos.api.domain.AuthTokenPurpose;
import io.github.patorinaldi.gastos.api.service.auth.AuthTokenValues;
import io.github.patorinaldi.gastos.api.service.email.EmailMessage;
import io.github.patorinaldi.gastos.api.service.email.EmailSender;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Base para las pruebas de los enlaces que llegan por correo: verificación y restablecimiento.
 *
 * <p>El envío real sigue siendo el de consola, espiado para leer lo que se mandó. Los correos salen
 * en segundo plano, después de responder: {@link #emailTo} los espera. Todas las clases que
 * extienden esta comparten el mismo contexto de Spring, porque espían el mismo bean.
 */
public abstract class EmailTestSupport extends AuthTestSupport {

    protected static final long WAIT_MS = 10_000;

    @MockitoSpyBean
    protected EmailSender emailSender;

    @Autowired
    protected AuthTokenValues tokenValues;

    /** Un token emitido directo en la base, como lo haría el pedido del enlace. */
    protected String issue(UUID user, AuthTokenPurpose purpose) {
        String token = tokenValues.generate();
        jdbcTemplate.update("insert into auth_tokens (user_id, purpose, token_hash) values (?, ?, ?)",
                user, purpose.databaseValue(), tokenValues.hash(token));
        return token;
    }

    /** Vence el token en la base, como si hubieran pasado las 24 horas. */
    protected void expire(String token) {
        jdbcTemplate.update("update auth_tokens set expires_at = now() - interval '1 minute' where token_hash = ?",
                (Object) tokenValues.hash(token));
    }

    /** El último correo enviado a {@code to}, esperando a que salga. */
    protected EmailMessage emailTo(String to) {
        return emailsTo(to, 1).getLast();
    }

    /** Los correos enviados a {@code to}, esperando a que salgan {@code count}. */
    protected List<EmailMessage> emailsTo(String to, int count) {
        verify(emailSender, timeout(WAIT_MS).times(count)).send(argThat(m -> m != null && to.equals(m.to())));
        return mockingDetails(emailSender).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("send"))
                .map(invocation -> invocation.<EmailMessage>getArgument(0))
                .filter(message -> to.equals(message.to()))
                .toList();
    }

    /** El token del enlace {@code path}, por ejemplo {@code /verify-email}, que lleva el correo. */
    protected static String tokenIn(EmailMessage message, String path) {
        Matcher matcher = Pattern.compile(Pattern.quote(path) + "#token=([A-Za-z0-9_-]{43})").matcher(message.body());
        assertThat(matcher.find()).as("el correo lleva el enlace " + path).isTrue();
        return matcher.group(1);
    }

    /**
     * Canjea los dos tokens a la vez, cada uno desde su hilo, y devuelve cuáles se aceptaron. Un
     * canje rechazado tiene que lanzar {@code rejected}; cualquier otra excepción, como un deadlock,
     * hace fallar la prueba.
     */
    protected List<Boolean> redeemSimultaneously(String first, String second, Consumer<String> redeem,
                                                 Class<? extends RuntimeException> rejected) throws Exception {
        CyclicBarrier largada = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> canjes = new ArrayList<>();
            for (String token : List.of(first, second)) {
                canjes.add(pool.submit(() -> {
                    largada.await(5, TimeUnit.SECONDS);
                    try {
                        redeem.accept(token);
                        return true;
                    } catch (RuntimeException e) {
                        if (rejected.isInstance(e)) {
                            return false;
                        }
                        throw e;
                    }
                }));
            }
            List<Boolean> resultados = new ArrayList<>();
            for (Future<Boolean> canje : canjes) {
                resultados.add(canje.get(WAIT_MS, TimeUnit.MILLISECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }
}
