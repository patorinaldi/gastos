package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.AuthTestSupport;
import io.github.patorinaldi.gastos.api.config.PasswordResetLimitProperties;
import io.github.patorinaldi.gastos.api.domain.AuthTokenPurpose;
import io.github.patorinaldi.gastos.api.service.auth.AuthTokenValues;
import io.github.patorinaldi.gastos.api.service.auth.PasswordResetService;
import io.github.patorinaldi.gastos.api.service.auth.ResetTokenUnavailableException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Restablecimiento de contraseña: {@code POST /api/auth/password/forgot} y
 * {@code POST /api/auth/password/reset} (RF-06, CU-08, RN-03, RNF-08, RNF-29, RNF-32).
 *
 * <p>El correo sale en segundo plano, después de responder: las pruebas lo esperan con
 * {@code verify(..., timeout(...))}. Las que prueban el canje emiten el token directo en la base,
 * sin pasar por el correo.
 */
class PasswordResetTest extends AuthTestSupport {

    private static final String PASSWORD = "contraseña-anterior";
    private static final String NEW_PASSWORD = "contraseña-nueva-1";
    private static final long WAIT_MS = 10_000;
    private static final Pattern LINK_TOKEN = Pattern.compile("/reset-password#token=([A-Za-z0-9_-]{43})");

    @MockitoSpyBean
    private EmailSender emailSender;

    @Autowired
    private AuthTokenValues tokenValues;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetLimitProperties limits;

    private UUID casa;
    private UUID ana;
    private String email;

    @BeforeEach
    void sembrarUsuario() {
        casa = createHousehold("Casa de Ana");
        email = uniqueEmail("ana");
        ana = createVerifiedUser(casa, email, "Ana", PASSWORD);
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
    }

    // ── Pedido del enlace ───────────────────────────────────────────────────

    @Test
    void elEnlaceDelCorreoPermiteElegirUnaContrasenaNueva() throws Exception {
        forgot(email).andExpect(status().isAccepted()).andExpect(content().string(""));

        resetPassword(tokenIn(emailTo(email)), NEW_PASSWORD).andExpect(status().isNoContent());

        login(email, NEW_PASSWORD).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    // La base guarda solo el hash: el token en claro existe únicamente en el correo (RN-03).
    @Test
    void elCorreoLlevaElEnlaceAlClienteWebYLaBaseGuardaSoloElHash() throws Exception {
        forgot(email).andExpect(status().isAccepted());

        EmailMessage sent = emailTo(email);
        String token = tokenIn(sent);

        assertThat(sent.body()).contains("http://localhost:5173/reset-password#token=" + token);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auth_tokens where user_id = ? and purpose = 'password_reset' and token_hash = ?",
                Integer.class, ana, tokenValues.hash(token))).isEqualTo(1);
    }

    @Test
    void elCorreoNoDistingueMayusculasYElEnlaceVaALaDireccionDeLaCuenta() throws Exception {
        forgot(email.toUpperCase()).andExpect(status().isAccepted());

        assertThat(emailTo(email).to()).isEqualTo(email);
    }

    // CU-08, 1a: la respuesta no revela si el correo tiene cuenta.
    @Test
    void unCorreoSinCuentaRecibeLaMismaRespuestaYNoSeLeEnviaNada() throws Exception {
        String desconocido = uniqueEmail("nadie");

        forgot(desconocido).andExpect(status().isAccepted()).andExpect(content().string(""));

        verify(emailSender, after(1_000).never()).send(argThat(m -> m != null && desconocido.equals(m.to())));
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_tokens where user_id = ?", Integer.class, ana))
                .isZero();
    }

    // RNF-32. Se cuenta el correo escrito, exista o no la cuenta: el 429 tampoco revela nada.
    @Test
    void superadoElLimitePorCorreoResponde429() throws Exception {
        String insistente = uniqueEmail("insistente");
        for (int i = 0; i < limits.maxPerEmail(); i++) {
            forgot(insistente).andExpect(status().isAccepted());
        }

        forgot(insistente)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    // ── Canje ───────────────────────────────────────────────────────────────

    @Test
    void unEnlaceUsadoNoSirveUnaSegundaVez() throws Exception {
        String token = issue(ana, AuthTokenPurpose.PASSWORD_RESET);
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());

        resetPassword(token, "otra-contraseña-1").andExpect(status().isGone());

        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unEnlaceVencidoNoSirve() throws Exception {
        String token = issue(ana, AuthTokenPurpose.PASSWORD_RESET);
        jdbcTemplate.update("update auth_tokens set expires_at = now() - interval '1 minute' where token_hash = ?",
                (Object) tokenValues.hash(token));

        resetPassword(token, NEW_PASSWORD).andExpect(status().isGone());

        login(email, PASSWORD).andExpect(status().isOk());
    }

    // La tabla es compartida (RN-03): un token de verificación no puede cambiar una contraseña.
    @Test
    void unTokenDeVerificacionNoSirveParaRestablecer() throws Exception {
        String token = issue(ana, AuthTokenPurpose.EMAIL_VERIFICATION);

        resetPassword(token, NEW_PASSWORD).andExpect(status().isGone());

        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unTokenInexistenteOMalFormadoNoSirve() throws Exception {
        resetPassword(tokenValues.generate(), NEW_PASSWORD).andExpect(status().isGone());
        resetPassword("no-es-un-token", NEW_PASSWORD).andExpect(status().isGone());
    }

    // RNF-29: quien restablece la contraseña suele sospechar que otra persona la tiene.
    @Test
    void restablecerCierraLasSesionesAbiertas() throws Exception {
        String sesionAnterior = bearer(ana);
        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, sesionAnterior)).andExpect(status().isOk());

        resetPassword(issue(ana, AuthTokenPurpose.PASSWORD_RESET), NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, sesionAnterior))
                .andExpect(status().isUnauthorized());
    }

    // Si alguien pidió varios enlaces, usar uno deja sin efecto los demás.
    @Test
    void restablecerAnulaLosDemasEnlacesPendientes() throws Exception {
        String primero = issue(ana, AuthTokenPurpose.PASSWORD_RESET);
        String segundo = issue(ana, AuthTokenPurpose.PASSWORD_RESET);

        resetPassword(segundo, NEW_PASSWORD).andExpect(status().isNoContent());

        resetPassword(primero, "otra-contraseña-1").andExpect(status().isGone());
    }

    // La validación corre antes de tocar el token: una contraseña rechazada no gasta el enlace.
    @Test
    void unaContrasenaCortaNoGastaElEnlace() throws Exception {
        String token = issue(ana, AuthTokenPurpose.PASSWORD_RESET);

        resetPassword(token, "corta").andExpect(status().isBadRequest());

        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    // Un solo uso también con dos canjes a la vez: el update condicional deja pasar a uno.
    @Test
    void deDosCanjesSimultaneosDelMismoEnlaceSoloUnoCambiaLaContrasena() throws Exception {
        String token = issue(ana, AuthTokenPurpose.PASSWORD_RESET);
        CyclicBarrier largada = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> canjes = new ArrayList<>();
            for (String password : List.of("contraseña-de-uno-1", "contraseña-de-otro-2")) {
                canjes.add(pool.submit(() -> {
                    largada.await(5, TimeUnit.SECONDS);
                    try {
                        passwordResetService.resetPassword(token, password);
                        return true;
                    } catch (ResetTokenUnavailableException e) {
                        return false;
                    }
                }));
            }
            List<Boolean> resultados = new ArrayList<>();
            for (Future<Boolean> canje : canjes) {
                resultados.add(canje.get(WAIT_MS, TimeUnit.MILLISECONDS));
            }

            assertThat(resultados).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("select session_version from users where id = ?", Integer.class, ana))
                .isEqualTo(1);
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    private ResultActions forgot(String address) throws Exception {
        return mockMvc.perform(post("/api/auth/password/forgot")
                .with(fromNewAddress())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}""".formatted(address)));
    }

    private ResultActions resetPassword(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "password": "%s"}""".formatted(token, password)));
    }

    /** Un token emitido directo en la base, como lo haría el pedido del enlace. */
    private String issue(UUID user, AuthTokenPurpose purpose) {
        String token = tokenValues.generate();
        jdbcTemplate.update("insert into auth_tokens (user_id, purpose, token_hash) values (?, ?, ?)",
                user, purpose.databaseValue(), tokenValues.hash(token));
        return token;
    }

    /** El correo enviado a {@code to}, esperando a que salga. */
    private EmailMessage emailTo(String to) {
        verify(emailSender, timeout(WAIT_MS)).send(argThat(m -> m != null && to.equals(m.to())));
        return mockingDetails(emailSender).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("send"))
                .map(invocation -> invocation.<EmailMessage>getArgument(0))
                .filter(message -> to.equals(message.to()))
                .reduce((first, last) -> last)
                .orElseThrow();
    }

    private static String tokenIn(EmailMessage message) {
        Matcher matcher = LINK_TOKEN.matcher(message.body());
        assertThat(matcher.find()).as("el correo lleva el enlace").isTrue();
        return matcher.group(1);
    }
}
