package io.github.patorinaldi.gastos.api.isolation;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import io.github.patorinaldi.gastos.api.config.InvitationLimitProperties;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aislamiento entre hogares por punto de acceso HTTP, para los endpoints de identidad
 * ({@code /api/auth}) y de hogares ({@code /api/household}).
 *
 * <p>Completa a {@link HouseholdIsolationTest}, que cubre las tablas con RLS a través de los
 * repositorios. Acá el riesgo es otro: ni {@code users}, ni {@code households}, ni
 * {@code household_invitations} tienen RLS, así que el aislamiento de estos endpoints depende solo
 * de que cada consulta se acote a mano por el hogar del principal. Estas pruebas son lo que
 * detecta que alguna deje de hacerlo.
 *
 * <p>Ana y Alicia integran la Casa A; Beto, la Casa B. Cada caso mira dos cosas: que la respuesta
 * no traiga nada de B, y que la Casa B quede como estaba. Los casos de control positivo —Beto
 * viendo lo suyo— demuestran que la ausencia de datos de B significa "no se filtró" y no "no
 * había nada".
 *
 * <p>La vista previa y el canje de invitaciones cruzan hogares a propósito (RF-09): lo que se
 * verifica es que crucen solo lo previsto. El registro y la verificación de correo no operan sobre
 * un hogar existente: el motivo de cada uno figura en {@link EndpointIsolationInventoryTest}.
 */
class IdentityAndHouseholdIsolationTest extends AuthTestSupport {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CONTRASENA = "contrasena-de-prueba";

    @Autowired
    private InvitationLimitProperties invitationLimits;

    private UUID casaA;
    private UUID casaB;
    private UUID ana;
    private UUID alicia;
    private UUID beto;
    private String correoDeAna;
    private String correoDeBeto;

    @BeforeEach
    void sembrarDosHogares() {
        casaA = createHousehold("Casa A");
        correoDeAna = uniqueEmail("ana");
        ana = createVerifiedUser(casaA, correoDeAna, "Ana", CONTRASENA);
        alicia = createUser(casaA, uniqueEmail("alicia"), "Alicia");

        casaB = createHousehold("Casa B");
        correoDeBeto = uniqueEmail("beto");
        beto = createVerifiedUser(casaB, correoDeBeto, "Beto", CONTRASENA);
    }

    // Si Ana canjeó un código de B, sus filas en A se borran con A y las de B, con B.
    @AfterEach
    void limpiar() {
        deleteHousehold(casaA);
        deleteHousehold(casaB);
    }

    @Nested
    @DisplayName("sin sesión")
    class SinSesion {

        static Stream<Arguments> endpointsConSesion() {
            return Stream.of(
                    Arguments.of("GET /api/auth/me", get("/api/auth/me")),
                    Arguments.of("GET /api/household", get("/api/household")),
                    Arguments.of("PATCH /api/household", conCuerpo(patch("/api/household"), """
                            {"name": "Cualquiera"}""")),
                    Arguments.of("GET /api/household/members", get("/api/household/members")),
                    Arguments.of("POST /api/household/invitations", post("/api/household/invitations")),
                    Arguments.of("POST /api/household/invitations/preview",
                            conCuerpo(post("/api/household/invitations/preview"), """
                                    {"code": "ABCD-EFGH-JKLM"}""")),
                    Arguments.of("POST /api/household/invitations/redeem",
                            conCuerpo(post("/api/household/invitations/redeem"), """
                                    {"code": "ABCD-EFGH-JKLM", "confirm": true}""")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("endpointsConSesion")
        void ningunEndpointDeIdentidadNiDeHogaresRespondeSinSesion(String endpoint,
                                                                  MockHttpServletRequestBuilder request)
                throws Exception {
            mockMvc.perform(request.with(fromNewAddress()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        void laSesionQueEmiteOperaSobreElHogarDeQuienIniciaSesion() throws Exception {
            String deAna = "Bearer " + tokenDeLogin(correoDeAna);

            noFiltraNadaDeB(mockMvc.perform(get("/api/household").header(AUTHORIZATION, deAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(casaA.toString())));
            noFiltraNadaDeB(mockMvc.perform(get("/api/household/members").header(AUTHORIZATION, deAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", containsInAnyOrder(ana.toString(), alicia.toString()))));
        }

        @Test
        void controlPositivoLaSesionDeBetoOperaSobreLaCasaB() throws Exception {
            String deBeto = "Bearer " + tokenDeLogin(correoDeBeto);

            mockMvc.perform(get("/api/household").header(AUTHORIZATION, deBeto))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(casaB.toString()));
        }

        private String tokenDeLogin(String correo) throws Exception {
            String body = login(correo, CONTRASENA)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            return JsonPath.read(body, "$.token");
        }
    }

    @Nested
    @DisplayName("GET /api/auth/me")
    class UsuarioActual {

        @Test
        void devuelveSoloAlUsuarioYSuHogar() throws Exception {
            noFiltraNadaDeB(mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(ana)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(ana.toString()))
                    .andExpect(jsonPath("$.householdId").value(casaA.toString())));
        }

        @Test
        void controlPositivoBetoSeVeASiMismoEnLaCasaB() throws Exception {
            mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(beto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(beto.toString()))
                    .andExpect(jsonPath("$.email").value(correoDeBeto))
                    .andExpect(jsonPath("$.householdId").value(casaB.toString()))
                    .andExpect(jsonPath("$.householdName").value("Casa B"));
        }
    }

    @Nested
    @DisplayName("GET y PATCH /api/household")
    class Hogar {

        @Test
        void devuelveElHogarPropio() throws Exception {
            noFiltraNadaDeB(mockMvc.perform(get("/api/household").header(AUTHORIZATION, bearer(ana)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(casaA.toString())));
        }

        // La ruta no acepta un hogar: un parámetro con el id de B no cambia a cuál se accede.
        @Test
        void nombrarOtroHogarEnLaPeticionNoLoAlcanza() throws Exception {
            noFiltraNadaDeB(mockMvc.perform(get("/api/household")
                            .param("id", casaB.toString())
                            .param("householdId", casaB.toString())
                            .header(AUTHORIZATION, bearer(ana)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(casaA.toString())));
        }

        // Un id de B en el cuerpo no redirige el renombre: el contrato solo lleva el nombre.
        @Test
        void renombrarConElIdDeOtroHogarEnElCuerpoNoLoModifica() throws Exception {
            mockMvc.perform(conCuerpo(patch("/api/household"), """
                            {"id": "%s", "householdId": "%s", "name": "Tomado"}""".formatted(casaB, casaB))
                            .header(AUTHORIZATION, bearer(ana)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(casaA.toString()));

            assertThat(nombreDe(casaB)).isEqualTo("Casa B");
            assertThat(nombreDe(casaA)).isEqualTo("Tomado");
        }

        @Test
        void controlPositivoBetoVeYRenombraLaCasaB() throws Exception {
            mockMvc.perform(conCuerpo(patch("/api/household"), """
                            {"name": "Casa B renovada"}""")
                            .header(AUTHORIZATION, bearer(beto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(casaB.toString()));

            assertThat(nombreDe(casaB)).isEqualTo("Casa B renovada");
            assertThat(nombreDe(casaA)).isEqualTo("Casa A");
        }
    }

    @Nested
    @DisplayName("GET /api/household/members")
    class Integrantes {

        @Test
        void listaSoloALosIntegrantesDelHogarPropio() throws Exception {
            noFiltraNadaDeB(mockMvc.perform(get("/api/household/members").header(AUTHORIZATION, bearer(alicia)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", containsInAnyOrder(ana.toString(), alicia.toString()))));
        }

        @Test
        void controlPositivoBetoSeVeSoloASiMismo() throws Exception {
            mockMvc.perform(get("/api/household/members").header(AUTHORIZATION, bearer(beto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", containsInAnyOrder(beto.toString())));
        }
    }

    @Nested
    @DisplayName("POST /api/household/invitations")
    class GenerarInvitacion {

        @Test
        void laInvitacionQuedaEnElHogarPropioYNoTocaAlOtro() throws Exception {
            noFiltraNadaDeB(generar(ana).andExpect(status().isCreated()));

            assertThat(jdbcTemplate.queryForList(
                    "select household_id from household_invitations where created_by = ?", UUID.class, ana))
                    .containsExactly(casaA);
            assertThat(invitacionesDe(casaB)).isZero();
        }

        // El tope de vigentes (RNF-30) se cuenta por hogar: las de B no le cierran la puerta a A.
        @Test
        void lasInvitacionesVigentesDeOtroHogarNoCuentanParaElTope() throws Exception {
            for (int i = 0; i < invitationLimits.maxActivePerHousehold(); i++) {
                insertarInvitacion(casaB, beto);
            }

            generar(ana).andExpect(status().isCreated());
            generar(beto).andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/household/invitations/preview")
    class VistaPrevia {

        // Lo único que la vista previa revela del otro hogar es su nombre, para confirmar a cuál se
        // suma (RF-09). Ni ids, ni integrantes, ni correos.
        @Test
        void deOtroHogarRevelaSoloSuNombre() throws Exception {
            String body = vistaPrevia(ana, codigoDe(beto))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            Map<String, Object> respuesta = JsonPath.read(body, "$");
            assertThat(respuesta).containsOnlyKeys(
                    "householdName", "currentHouseholdWillBeArchived", "machineTokensToRevoke");
            assertThat(respuesta).containsEntry("householdName", "Casa B");
            assertThat(body).doesNotContain(casaB.toString(), beto.toString(), correoDeBeto, "Beto");
        }

        // Las consecuencias se calculan sobre el hogar y los tokens de quien canjea, no sobre los
        // del hogar de destino: B tiene un solo integrante y A, dos.
        @Test
        void lasConsecuenciasSeCalculanSobreElHogarPropio() throws Exception {
            insertarToken(ana, casaA);
            insertarToken(alicia, casaA);
            insertarToken(alicia, casaA);
            insertarToken(beto, casaB);
            insertarToken(beto, casaB);
            insertarToken(beto, casaB);

            vistaPrevia(ana, codigoDe(beto))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentHouseholdWillBeArchived").value(false))
                    .andExpect(jsonPath("$.machineTokensToRevoke").value(1));
        }
    }

    @Nested
    @DisplayName("POST /api/household/invitations/redeem")
    class Canje {

        // El canje mueve solo a quien canjea: el resto de su hogar anterior se queda donde estaba,
        // con sus tokens, y del hogar de destino solo se consume la invitación usada.
        @Test
        void soloMueveYRevocaAQuienCanjea() throws Exception {
            UUID tokenDeAna = insertarToken(ana, casaA);
            UUID tokenDeAlicia = insertarToken(alicia, casaA);
            UUID tokenDeBeto = insertarToken(beto, casaB);
            String codigo = codigoDe(beto);
            insertarInvitacion(casaB, beto);

            canjear(ana, codigo)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.householdId").value(casaB.toString()))
                    .andExpect(jsonPath("$.previousHouseholdArchived").value(false));

            assertThat(revocado(tokenDeAna)).isTrue();
            assertThat(revocado(tokenDeAlicia)).isFalse();
            assertThat(revocado(tokenDeBeto)).isFalse();
            assertThat(hogarDe(alicia)).isEqualTo(casaA);
            assertThat(hogarDe(beto)).isEqualTo(casaB);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from household_invitations where household_id = ? and redeemed_at is null",
                    Integer.class, casaB)).isEqualTo(1);
        }

        @Test
        void despuesDelCanjeCadaHogarListaSoloASusIntegrantes() throws Exception {
            canjear(ana, codigoDe(beto)).andExpect(status().isOk());

            mockMvc.perform(get("/api/household/members").header(AUTHORIZATION, bearer(alicia)))
                    .andExpect(jsonPath("$[*].id", containsInAnyOrder(alicia.toString())));
            mockMvc.perform(get("/api/household/members").header(AUTHORIZATION, bearer(beto)))
                    .andExpect(jsonPath("$[*].id", containsInAnyOrder(ana.toString(), beto.toString())));
        }
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────

    /** Ningún identificador ni dato personal de la Casa B en el cuerpo de la respuesta. */
    private void noFiltraNadaDeB(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(casaB.toString(), beto.toString(), correoDeBeto, "Casa B", "Beto");
    }

    private static MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder request, String json) {
        return request.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private ResultActions generar(UUID usuario) throws Exception {
        return mockMvc.perform(post("/api/household/invitations").header(AUTHORIZATION, bearer(usuario)));
    }

    private String codigoDe(UUID usuario) throws Exception {
        String body = generar(usuario)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    private ResultActions vistaPrevia(UUID usuario, String codigo) throws Exception {
        return mockMvc.perform(conCuerpo(post("/api/household/invitations/preview"), """
                        {"code": "%s"}""".formatted(codigo))
                .with(fromNewAddress())
                .header(AUTHORIZATION, bearer(usuario)));
    }

    private ResultActions canjear(UUID usuario, String codigo) throws Exception {
        return mockMvc.perform(conCuerpo(post("/api/household/invitations/redeem"), """
                        {"code": "%s", "confirm": true}""".formatted(codigo))
                .with(fromNewAddress())
                .header(AUTHORIZATION, bearer(usuario)));
    }

    /** Invitación vigente con un hash al azar: cuenta para el tope, pero nadie conoce su código. */
    private void insertarInvitacion(UUID hogar, UUID creadaPor) {
        byte[] hash = new byte[32];
        RANDOM.nextBytes(hash);
        jdbcTemplate.update(
                "insert into household_invitations (household_id, created_by, code_hash) values (?, ?, ?)",
                hogar, creadaPor, hash);
    }

    private UUID insertarToken(UUID usuario, UUID hogar) {
        UUID id = UUID.randomUUID();
        byte[] hash = new byte[32];
        RANDOM.nextBytes(hash);
        jdbcTemplate.update(
                "insert into machine_tokens (id, household_id, user_id, name, token_hash) values (?, ?, ?, ?, ?)",
                id, hogar, usuario, "Atajo del teléfono", hash);
        return id;
    }

    private boolean revocado(UUID token) {
        return jdbcTemplate.queryForObject(
                "select revoked_at is not null from machine_tokens where id = ?", Boolean.class, token);
    }

    private int invitacionesDe(UUID hogar) {
        return jdbcTemplate.queryForObject(
                "select count(*) from household_invitations where household_id = ?", Integer.class, hogar);
    }

    private UUID hogarDe(UUID usuario) {
        return jdbcTemplate.queryForObject("select household_id from users where id = ?", UUID.class, usuario);
    }

    private String nombreDe(UUID hogar) {
        return jdbcTemplate.queryForObject("select name from households where id = ?", String.class, hogar);
    }
}
