package io.github.patorinaldi.gastos.api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Emisión y validación del token de sesión, y hash de contraseñas.
 *
 * <p>El token se firma con HS256 y una clave compartida: lo emite y lo valida la misma aplicación,
 * así que no hace falta un par de claves ni un servidor de autorización aparte. El decoder valida
 * la firma y el vencimiento; quién es el usuario y a qué hogar accede lo resuelve después
 * {@code SessionAuthenticationConverter} contra la base.
 */
@Configuration
class JwtConfiguration {

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** bcrypt, con sal propia en cada hash (RNF-05). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Inyectable para que las pruebas puedan fijar la hora de emisión de un token. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}