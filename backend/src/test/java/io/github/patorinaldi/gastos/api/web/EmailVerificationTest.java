package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.EmailTestSupport;
import io.github.patorinaldi.gastos.api.config.EmailLinkLimitProperties;
import io.github.patorinaldi.gastos.api.domain.AuthTokenPurpose;
import io.github.patorinaldi.gastos.api.service.auth.EmailVerificationService;
import io.github.patorinaldi.gastos.api.service.auth.VerificationTokenUnavailableException;
import io.github.patorinaldi.gastos.api.service.email.EmailMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verificación del correo: el enlace del alta, {@code POST /api/auth/verify} y
 * {@code POST /api/auth/verify/resend} (RF-03, HU-02, RN-02, RN-03, RNF-08, RNF-32).
 */
class EmailVerificationTest extends EmailTestSupport {

    private static final String PASSWORD = "contraseña-de-prueba";
    private static final String LINK = "/verify-email";

    @Autowired
    private EmailVerificationService verificationService;

    @Autowired
    private EmailLinkLimitProperties limits;

    private final List<UUID> households = new ArrayList<>();

    // Para las pruebas que no pasan por el alta: una cuenta sin verificar, como la deja el alta.
    private UUID casa;
    private UUID beto;
    private String correoDeBeto;

    @BeforeEach
    void sembrarCuentaSinVerificar() {
        casa = createHousehold("Casa de Beto");
        households.add(casa);
        correoDeBeto = uniqueEmail("beto");
        beto = createUser(casa, correoDeBeto, "Beto");
        jdbcTemplate.update("update users set password_hash = ? where id = ?", passwordEncoder.encode(PASSWORD), beto);
    }

    @AfterEach
    void limpiar() {
        households.forEach(this::deleteHousehold);
    }

    // ── Alta y confirmación ─────────────────────────────────────────────────

    // HU-02, confirmación válida: el enlace del alta habilita el inicio de sesión (RN-02).
    @Test
    void elAltaMandaElEnlaceYConfirmarloHabilitaElInicioDeSesion() throws Exception {
        String email = uniqueEmail("ana");
        register("Ana", email);
        login(email, PASSWORD).andExpect(status().isForbidden());

        verifyEmail(tokenIn(emailTo(email), LINK)).andExpect(status().isNoContent());

        login(email, PASSWORD).andExpect(status().isOk());
    }

    // RN-03: el token en claro existe solo en el correo; la base guarda su hash.
    @Test
    void elCorreoDelAltaLlevaElEnlaceAlClienteWebYLaBaseGuardaSoloElHash() throws Exception {
        String email = uniqueEmail("ana");
        UUID ana = register("Ana", email);

        EmailMessage sent = emailTo(email);
        String token = tokenIn(sent, LINK);

        assertThat(sent.body()).contains("http://localhost:5173/verify-email#token=" + token).contains("Hola, Ana:");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auth_tokens where user_id = ? and purpose = 'email_verification' and token_hash = ?",
                Integer.class, ana, tokenValues.hash(token))).isEqualTo(1);
    }

    // HU-02, token ya usado.
    @Test
    void unEnlaceUsadoNoSirveUnaSegundaVez() throws Exception {
        String token = issue(beto, AuthTokenPurpose.EMAIL_VERIFICATION);
        verifyEmail(token).andExpect(status().isNoContent());

        verifyEmail(token).andExpect(status().isGone());
    }

    // HU-02, token vencido.
    @Test
    void unEnlaceVencidoNoSirve() throws Exception {
        String token = issue(beto, AuthTokenPurpose.EMAIL_VERIFICATION);
        expire(token);

        verifyEmail(token).andExpect(status().isGone());

        login(correoDeBeto, PASSWORD).andExpect(status().isForbidden());
    }

    // La tabla es compartida (RN-03): un token de restablecimiento no verifica una cuenta.
    @Test
    void unTokenDeRestablecimientoNoVerifica() throws Exception {
        String token = issue(beto, AuthTokenPurpose.PASSWORD_RESET);

        verifyEmail(token).andExpect(status().isGone());

        login(correoDeBeto, PASSWORD).andExpect(status().isForbidden());
    }

    @Test
    void unTokenInexistenteOMalFormadoNoSirve() throws Exception {
        verifyEmail(tokenValues.generate()).andExpect(status().isGone());
        verifyEmail("no-es-un-token").andExpect(status().isGone());
    }

    // Dos enlaces de la misma persona abiertos a la vez: uno verifica, el otro queda anulado, y
    // ninguno termina en un deadlock.
    @Test
    void deDosEnlacesConfirmadosALaVezUnoVerificaYElOtroQuedaAnulado() throws Exception {
        String primero = issue(beto, AuthTokenPurpose.EMAIL_VERIFICATION);
        String segundo = issue(beto, AuthTokenPurpose.EMAIL_VERIFICATION);

        assertThat(redeemSimultaneously(primero, segundo, verificationService::verify,
                VerificationTokenUnavailableException.class))
                .containsExactlyInAnyOrder(true, false);
        login(correoDeBeto, PASSWORD).andExpect(status().isOk());
    }

    // ── Reenvío ─────────────────────────────────────────────────────────────

    // HU-02, token vencido: se ofrece reenviarlo. Confirmar con el nuevo anula el anterior.
    @Test
    void elReenvioMandaUnEnlaceNuevoQueVerificaYAnulaElAnterior() throws Exception {
        String anterior = issue(beto, AuthTokenPurpose.EMAIL_VERIFICATION);

        resend(correoDeBeto).andExpect(status().isAccepted()).andExpect(content().string(""));
        verifyEmail(tokenIn(emailTo(correoDeBeto), LINK)).andExpect(status().isNoContent());

        verifyEmail(anterior).andExpect(status().isGone());
        login(correoDeBeto, PASSWORD).andExpect(status().isOk());
    }

    // Un reenvío que no sale no deja vivo un enlace que nadie recibió, ni anula el que la persona ya
    // tiene en su casilla.
    @Test
    void unReenvioQueNoSaleAnulaElEnlaceNuevoYDejaVigenteElAnterior() throws Exception {
        String anterior = issue(beto, AuthTokenPurpose.EMAIL_VERIFICATION);
        doThrow(new MailSendException("proveedor caído"))
                .when(emailSender).send(argThat(m -> m != null && correoDeBeto.equals(m.to())));

        resend(correoDeBeto).andExpect(status().isAccepted());

        verify(emailSender, timeout(WAIT_MS)).send(argThat(m -> m != null && correoDeBeto.equals(m.to())));
        awaitPendingVerificationTokens(beto, 1);
        verifyEmail(anterior).andExpect(status().isNoContent());
    }

    // Misma respuesta exista o no la cuenta, esté o no verificada, y sin correo de más.
    @Test
    void elReenvioRespondeIgualParaUnaCuentaVerificadaOInexistenteYNoMandaNada() throws Exception {
        String verificada = uniqueEmail("carla");
        createVerifiedUser(casa, verificada, "Carla", PASSWORD);
        String desconocido = uniqueEmail("nadie");

        resend(verificada).andExpect(status().isAccepted()).andExpect(content().string(""));
        resend(desconocido).andExpect(status().isAccepted()).andExpect(content().string(""));

        verify(emailSender, after(1_000).never())
                .send(argThat(m -> m != null && (verificada.equals(m.to()) || desconocido.equals(m.to()))));
    }

    // RNF-32: el reenvío y el restablecimiento llegan a la misma casilla, así que cuentan juntos.
    @Test
    void elReenvioYElRestablecimientoCompartenElLimitePorCorreo() throws Exception {
        String insistente = uniqueEmail("insistente");
        for (int i = 0; i < limits.maxPerEmail(); i++) {
            (i % 2 == 0 ? resend(insistente) : forgot(insistente)).andExpect(status().isAccepted());
        }

        resend(insistente)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    /** Alta por la API. Devuelve el usuario y anota su hogar para limpiarlo. */
    private UUID register(String name, String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .with(fromNewAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "email": "%s", "password": "%s"}""".formatted(name, email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        households.add(UUID.fromString(JsonPath.read(body, "$.householdId")));
        return UUID.fromString(JsonPath.read(body, "$.userId"));
    }

    /** Espera a que el reenvío en segundo plano termine de anular tokens y deje {@code expected} vigentes. */
    private void awaitPendingVerificationTokens(UUID user, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        int pending;
        do {
            pending = jdbcTemplate.queryForObject("""
                    select count(*) from auth_tokens
                    where user_id = ? and purpose = 'email_verification' and used_at is null""", Integer.class, user);
            if (pending == expected) {
                return;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        assertThat(pending).as("tokens de verificación vigentes").isEqualTo(expected);
    }

    private ResultActions verifyEmail(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s"}""".formatted(token)));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/verify/resend")
                .with(fromNewAddress())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}""".formatted(email)));
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password/forgot")
                .with(fromNewAddress())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}""".formatted(email)));
    }
}
