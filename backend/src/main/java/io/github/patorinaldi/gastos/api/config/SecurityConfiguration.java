package io.github.patorinaldi.gastos.api.config;

import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.security.HouseholdContextFilter;
import io.github.patorinaldi.gastos.api.security.SessionAuthenticationConverter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
 *   <li>{@link SessionAuthenticationConverter} lee de la base el hogar actual del usuario;</li>
 *   <li>{@link HouseholdContextFilter} lo deja en {@code CurrentHousehold};</li>
 *   <li>{@code HouseholdTransactionListener} lo fija en Postgres al comenzar cada transacción.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, UserRepository users) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Sin esto, el error de una petición pública se reenvía a /error, que
                        // exige sesión, y quien llama recibe un 401 en lugar del error real.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/verify",
                                "/api/auth/login",
                                "/api/auth/password/forgot",
                                "/api/auth/password/reset").permitAll()
                        // M6 se autentica con un token de cliente máquina, no con la sesión.
                        // Hasta que exista ese canal, el punto de entrada queda cerrado.
                        .requestMatchers("/api/capture").denyAll()
                        .anyRequest().hasRole(SessionAuthenticationConverter.SESSION_ROLE))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new SessionAuthenticationConverter(users))))
                .addFilterAfter(new HouseholdContextFilter(), BearerTokenAuthenticationFilter.class)
                .build();
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