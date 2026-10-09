package io.github.patorinaldi.gastos.api.config;

import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.security.HouseholdContextFilter;
import io.github.patorinaldi.gastos.api.security.ProblemDetailAccessDeniedHandler;
import io.github.patorinaldi.gastos.api.security.ProblemDetailAuthenticationEntryPoint;
import io.github.patorinaldi.gastos.api.security.SessionAuthenticationConverter;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

/**
 * Cadena de seguridad de la API.
 *
 * <p>Sin estado: cada petición trae su token de sesión en {@code Authorization: Bearer}, y el
 * servidor no guarda sesiones. Por eso tampoco hay CSRF, que protege a las credenciales que el
 * navegador agrega solo, como las cookies; un header lo tiene que poner el código del cliente.
 *
 * <p>Orden de lo que pasa en una petición autenticada:
 * <ol>
 *   <li>el filtro del resource server valida la firma y el vencimiento del token;</li>
 *   <li>{@link SessionAuthenticationConverter} lee de la base el hogar actual del usuario y su
 *       versión de sesión;</li>
 *   <li>{@link HouseholdContextFilter} deja el hogar en {@code CurrentHousehold};</li>
 *   <li>{@code HouseholdTransactionListener} lo fija en Postgres al comenzar cada transacción.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
class SecurityConfiguration {

    /**
     * Lo que se atiende sin sesión. Está definido una sola vez porque cumple dos funciones: se
     * permite sin autenticación, y en estas rutas se ignora el header {@code Authorization}.
     */
    private static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            pathPattern("/actuator/health"),
            pathPattern("/actuator/health/**"),
            // Sin esto, el error de una petición pública se reenvía a /error, que exigiría
            // sesión, y quien llama recibiría un 401 en lugar del error real.
            pathPattern("/error"),
            pathPattern(HttpMethod.POST, "/api/auth/register"),
            pathPattern(HttpMethod.POST, "/api/auth/verify"),
            pathPattern(HttpMethod.POST, "/api/auth/login"),
            pathPattern(HttpMethod.POST, "/api/auth/password/forgot"),
            pathPattern(HttpMethod.POST, "/api/auth/password/reset"));

    /**
     * Los 401 y 403 que responde la propia cadena, antes de llegar a un controlador, salen como
     * {@code problem+json}, igual que el resto de los errores de la API (RF-40). Los handlers se
     * configuran dos veces porque el resource server usa los suyos para los tokens inválidos, y la
     * cadena, para las peticiones sin token o sin permiso.
     */
    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            UserRepository users,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        ProblemDetailAuthenticationEntryPoint unauthorized = new ProblemDetailAuthenticationEntryPoint(exceptionResolver);
        ProblemDetailAccessDeniedHandler forbidden = new ProblemDetailAccessDeniedHandler(exceptionResolver);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // M6 se autentica con un token de cliente máquina, no con la sesión.
                        // Hasta que exista ese canal, el punto de entrada y todo lo que cuelgue de
                        // él quedan cerrados. "/**" incluye también la ruta exacta.
                        .requestMatchers("/api/capture/**").denyAll()
                        .anyRequest().hasRole(SessionAuthenticationConverter.SESSION_ROLE))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(ignoringPublicEndpoints())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new SessionAuthenticationConverter(users))))
                .addFilterAfter(new HouseholdContextFilter(), BearerTokenAuthenticationFilter.class)
                .build();
    }

    /**
     * El filtro del resource server intenta autenticar toda petición que traiga un token, antes
     * de que se evalúe el {@code permitAll}: con un token vencido, hasta el login respondería 401.
     * Un cliente que agrega el token a cada llamada quedaría sin forma de volver a iniciar sesión.
     * En las rutas públicas el token se ignora, como si no hubiera venido.
     */
    private static BearerTokenResolver ignoringPublicEndpoints() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        return request -> PUBLIC_ENDPOINTS.matches(request) ? null : resolver.resolve(request);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}