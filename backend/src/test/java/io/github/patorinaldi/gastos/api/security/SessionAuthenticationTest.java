package io.github.patorinaldi.gastos.api.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import io.github.patorinaldi.gastos.api.domain.Category;
import io.github.patorinaldi.gastos.api.repository.CategoryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La sesión de punta a punta: del header {@code Authorization} al hogar que ven las políticas
 * RLS. Cubre lo que resuelven {@code SessionAuthenticationConverter} y
 * {@code HouseholdContextFilter}.
 *
 * <p>Como todavía no hay endpoints de negocio, el aislamiento se prueba con
 * {@link CategoryProbe}, un controlador que solo existe en esta prueba y lista las categorías
 * activas a través del repositorio, igual que lo hará M4.
 */
class SessionAuthenticationTest extends AuthTestSupport {

    @Autowired
    private JwtEncoder jwtEncoder;

    private UUID casaA;
    private UUID casaB;
    private UUID ana;
    private UUID beto;

    @BeforeEach
    void sembrarDosHogares() {
        casaA = createHousehold("Casa A");
        casaB = createHousehold("Casa B");
        ana = createUser(casaA, uniqueEmail("ana"), "Ana");
        beto = createUser(casaB, uniqueEmail("beto"), "Beto");
        withHousehold(casaA, () -> jdbcTemplate.update(
                "insert into categories (household_id, name) values (?, ?)", casaA, "Solo de A"));
        withHousehold(casaB, () -> jdbcTemplate.update(
                "insert into categories (household_id, name) values (?, ?)", casaB, "Solo de B"));
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casaA);
        deleteHousehold(casaB);
    }

    @Test
    void conUnTokenValidoDevuelveElUsuarioYSuHogar() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ana.toString()))
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.householdId").value(casaA.toString()))
                .andExpect(jsonPath("$.householdName").value("Casa A"));
    }

    @Test
    void cadaSesionVeSoloLosDatosDeSuHogar() throws Exception {
        mockMvc.perform(get(CategoryProbe.PATH).header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("Solo de A")));
        mockMvc.perform(get(CategoryProbe.PATH).header(AUTHORIZATION, bearer(beto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("Solo de B")));
    }

    // RN-18: un token emitido antes de cambiarse de hogar opera sobre el hogar nuevo, no sobre
    // el anterior. Es lo que justifica leer el hogar de la base en cada petición.
    @Test
    void elHogarSaleDeLaBaseYNoDelToken() throws Exception {
        String tokenAnterior = bearer(ana);

        jdbcTemplate.update("update users set household_id = ? where id = ?", casaB, ana);

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, tokenAnterior))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId").value(casaB.toString()));
        mockMvc.perform(get(CategoryProbe.PATH).header(AUTHORIZATION, tokenAnterior))
                .andExpect(jsonPath("$", contains("Solo de B")));
    }

    // MockMvc corre la petición en el hilo de la prueba, así que si el filtro no limpiara el
    // contexto, quedaría acá.
    @Test
    void elHogarNoQuedaEnElHiloAlTerminarLaPeticion() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isOk());

        assertThat(CurrentHousehold.get()).isEmpty();
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(CategoryProbe.PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenFirmadoConOtraClaveResponde401() throws Exception {
        var otraClave = new SecretKeySpec(
                "otra-clave-que-no-es-la-de-la-aplicacion".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var falsificador = new NimbusJwtEncoder(new ImmutableSecret<>(otraClave));
        Instant ahora = Instant.now();
        String falso = falsificador.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(ana.toString())
                        .issuedAt(ahora).expiresAt(ahora.plusSeconds(3600)).build()))
                .getTokenValue();

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, "Bearer " + falso))
                .andExpect(status().isUnauthorized());
    }

    // La vigencia es de 7 días, así que un token emitido hace 8 venció hace uno. Con menos margen
    // la prueba sería frágil: el decoder tolera 60 segundos de diferencia de reloj.
    @Test
    void unTokenVencidoResponde401() throws Exception {
        String vencido = token(ana, Instant.now().minus(Duration.ofDays(8)));

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, "Bearer " + vencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenDeUnUsuarioQueNoExisteResponde401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    // Defensivo: una sesión no debería alcanzar un hogar archivado, porque se archiva cuando lo
    // deja su último integrante. Se fuerza con SQL para comprobar que, si pasara, no opera.
    @Test
    void unHogarArchivadoNoAdmiteSesion() throws Exception {
        jdbcTemplate.update("update households set active = false, archived_at = now() where id = ?", casaA);

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isUnauthorized());
    }

    // Al restablecer la contraseña se incrementa la versión de sesión: los tokens emitidos antes,
    // en cualquier dispositivo, dejan de valer, y uno emitido después sí sirve.
    @Test
    void invalidarLasSesionesRechazaLosTokensEmitidosAntes() throws Exception {
        String tokenAnterior = bearer(ana);

        jdbcTemplate.update("update users set session_version = session_version + 1 where id = ?", ana);

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, tokenAnterior))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isOk());
    }

    // Firmado con la clave correcta pero sin versión de sesión: no lo emitió SessionTokenIssuer.
    @Test
    void unTokenSinVersionDeSesionResponde401() throws Exception {
        Instant ahora = Instant.now();
        String sinVersion = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(ana.toString())
                        .issuedAt(ahora).expiresAt(ahora.plusSeconds(3600)).build()))
                .getTokenValue();

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, "Bearer " + sinVersion))
                .andExpect(status().isUnauthorized());
    }

    // El rechazo se describe en WWW-Authenticate. Si fuera distinto según el caso, un token viejo
    // alcanzaría para averiguar el estado de una cuenta. Hoy Spring Security ya pone una
    // descripción genérica, y el converter además usa el mismo mensaje en todos los casos: esta
    // prueba cuida que siga así aunque cambie cualquiera de los dos.
    @Test
    void todoRechazoDeSesionDaLaMismaRespuesta() throws Exception {
        String deUnUsuarioQueNoExiste = bearer(UUID.randomUUID());
        String deUnHogarArchivado = bearer(ana);
        String deUnaVersionVieja = bearer(beto);
        jdbcTemplate.update("update households set active = false, archived_at = now() where id = ?", casaA);
        jdbcTemplate.update("update users set session_version = session_version + 1 where id = ?", beto);

        List<String> respuestas = new ArrayList<>();
        for (String token : List.of(deUnUsuarioQueNoExiste, deUnHogarArchivado, deUnaVersionVieja)) {
            respuestas.add(mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", not(containsString("archivado"))))
                    .andExpect(header().string("WWW-Authenticate", not(containsString("existe"))))
                    .andExpect(header().string("WWW-Authenticate", not(containsString("versión"))))
                    .andReturn().getResponse().getHeader("WWW-Authenticate"));
        }

        assertThat(respuestas).containsOnly(respuestas.getFirst());
    }

    // Un cliente que agrega el token a cada llamada lo sigue mandando cuando vence. Las rutas
    // públicas lo ignoran: si no, ni el chequeo de estado ni el login funcionarían.
    @Test
    void unTokenVencidoNoAfectaLasRutasPublicas() throws Exception {
        String vencido = token(ana, Instant.now().minus(Duration.ofDays(8)));

        mockMvc.perform(get("/actuator/health").header(AUTHORIZATION, "Bearer " + vencido))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health").header(AUTHORIZATION, "Bearer no-es-un-token"))
                .andExpect(status().isOk());
    }

    // M6 tendrá su propio canal de autenticación. La sesión web no habilita ni el punto de
    // entrada ni nada que cuelgue de él.
    @Test
    void laSesionNoHabilitaLaCaptura() throws Exception {
        mockMvc.perform(post("/api/capture").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/capture/").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/capture/" + UUID.randomUUID()).header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        CategoryProbe categoryProbe(CategoryRepository categories) {
            return new CategoryProbe(categories);
        }
    }

    @RestController
    static class CategoryProbe {

        static final String PATH = "/api/test/categories";

        private final CategoryRepository categories;

        CategoryProbe(CategoryRepository categories) {
            this.categories = categories;
        }

        // Sin @Transactional a propósito: el repositorio abre su propia transacción, así que el
        // hogar se fija aunque quien lo llama se olvide de abrir una.
        @GetMapping(PATH)
        public List<String> names() {
            return categories.findAllByActiveTrueOrderByNameAsc().stream().map(Category::getName).toList();
        }
    }
}