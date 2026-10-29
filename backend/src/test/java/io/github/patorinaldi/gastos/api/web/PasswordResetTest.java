package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.EmailTestSupport;
import io.github.patorinaldi.gastos.api.config.EmailLinkLimitProperties;
import io.github.patorinaldi.gastos.api.domain.AuthTokenPurpose;
import io.github.patorinaldi.gastos.api.service.auth.PasswordResetService;
import io.github.patorinaldi.gastos.api.service.auth.ResetTokenUnavailableException;
import io.github.patorinaldi.gastos.api.service.email.EmailMessage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
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
 * <p>Las pruebas del canje emiten el token directo en la base, sin pasar por el correo.
 */
class PasswordResetTest extends EmailTestSupport {

    private static final String PASSWORD = "contraseña-anterior";
    private static final String NEW_PASSWORD = "contraseña-nueva-1";
    private static final String LINK = "/reset-password";

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private EmailLinkLimitProperties limits;

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

        resetPassword(tokenIn(emailTo(email), LINK), NEW_PASSWORD).andExpect(status().isNoContent());

        login(email, NEW_PASSWORD).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    // La base guarda solo el hash: el token en claro existe únicamente en el correo (RN-03).
    @Test
    void elCorreoLlevaElEnlaceAlClienteWebYLaBaseGuardaSoloElHash() throws Exception {
        forgot(email).andExpect(status().isAccepted());

        EmailMessage sent = emailTo(email);
        String token = tokenIn(sent, LINK);

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
        expire(token);

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

    // RN-02: el enlace llegó a la casilla, así que la cuenta queda verificada y puede entrar.
    @Test
    void restablecerVerificaElCorreoDeUnaCuentaSinVerificar() throws Exception {
        String correoSinVerificar = uniqueEmail("beto");
        UUID beto = createUser(casa, correoSinVerificar, "Beto");
        String verificacionPendiente = issue(beto, AuthTokenPurpose.EMAIL_VERIFICATION);

        resetPassword(issue(beto, AuthTokenPurpose.PASSWORD_RESET), NEW_PASSWORD).andExpect(status().isNoContent());

        login(correoSinVerificar, NEW_PASSWORD).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "select used_at is not null from auth_tokens where token_hash = ?",
                Boolean.class, (Object) tokenValues.hash(verificacionPendiente)))
                .as("el enlace de verificación pendiente queda sin efecto").isTrue();
    }

    // Una automatización emitida con la contraseña comprometida no vence: se revoca con las sesiones.
    @Test
    void restablecerRevocaLosTokensDeClienteMaquina() throws Exception {
        jdbcTemplate.update("insert into machine_tokens (household_id, user_id, name, token_hash) values (?, ?, ?, ?)",
                casa, ana, "Atajo del teléfono", tokenValues.hash(tokenValues.generate()));

        resetPassword(issue(ana, AuthTokenPurpose.PASSWORD_RESET), NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from machine_tokens where user_id = ? and revoked_at is null", Integer.class, ana))
                .isZero();
    }

    // Lo que cierra el restablecimiento es solo de quien lo pide: las sesiones y los tokens de quien
    // comparte su hogar, y los de otro hogar, siguen como estaban.
    @Test
    void restablecerNoTocaLasSesionesNiLosTokensDeLosDemas() throws Exception {
        UUID beto = createUser(casa, uniqueEmail("beto"), "Beto");
        UUID otraCasa = createHousehold("Casa de Carla");
        UUID carla = createUser(otraCasa, uniqueEmail("carla"), "Carla");
        try {
            String sesionDeBeto = bearer(beto);
            String sesionDeCarla = bearer(carla);
            for (UUID[] duenio : new UUID[][] {{casa, ana}, {casa, beto}, {otraCasa, carla}}) {
                jdbcTemplate.update(
                        "insert into machine_tokens (household_id, user_id, name, token_hash) values (?, ?, ?, ?)",
                        duenio[0], duenio[1], "Atajo del teléfono", tokenValues.hash(tokenValues.generate()));
            }

            resetPassword(issue(ana, AuthTokenPurpose.PASSWORD_RESET), NEW_PASSWORD).andExpect(status().isNoContent());

            mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, sesionDeBeto)).andExpect(status().isOk());
            mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, sesionDeCarla)).andExpect(status().isOk());
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from machine_tokens where user_id in (?, ?) and revoked_at is null",
                    Integer.class, beto, carla))
                    .isEqualTo(2);
        } finally {
            deleteHousehold(otraCasa);
        }
    }

    // Un solo uso también con dos canjes a la vez: el update condicional deja pasar a uno.
    @Test
    void deDosCanjesSimultaneosDelMismoEnlaceSoloUnoCambiaLaContrasena() throws Exception {
        String token = issue(ana, AuthTokenPurpose.PASSWORD_RESET);

        assertThat(simultaneousResets(token, token)).containsExactlyInAnyOrder(true, false);
        assertThat(sessionVersion(ana)).isEqualTo(1);
    }

    // Dos enlaces distintos de la misma persona, a la vez: cada canje anula el enlace del otro.
    // Bloqueando los tokens antes que al usuario, terminaban en un deadlock y uno respondía 500.
    @Test
    void deDosEnlacesDistintosCanjeadosALaVezUnoGanaYElOtroQuedaAnulado() throws Exception {
        String primero = issue(ana, AuthTokenPurpose.PASSWORD_RESET);
        String segundo = issue(ana, AuthTokenPurpose.PASSWORD_RESET);

        assertThat(simultaneousResets(primero, segundo)).containsExactlyInAnyOrder(true, false);
        assertThat(sessionVersion(ana)).isEqualTo(1);
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    private List<Boolean> simultaneousResets(String first, String second) throws Exception {
        return redeemSimultaneously(first, second,
                token -> passwordResetService.resetPassword(token, "contraseña-simultanea-" + token.charAt(0)),
                ResetTokenUnavailableException.class);
    }

    private int sessionVersion(UUID user) {
        return jdbcTemplate.queryForObject("select session_version from users where id = ?", Integer.class, user);
    }

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
}
