package io.github.patorinaldi.gastos.api.config;

import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La zona en la que se decide qué día es hoy: la fecha que completa el alta de un gasto (RF-14) y el
 * mes en curso del listado y del análisis (RD-07).
 *
 * <p>Tiene que ser una sola. Si el alta tomara la fecha en UTC y el análisis el mes en la hora de
 * Argentina, un gasto cargado el 31 a las 22 h quedaría con fecha del 1 y no aparecería en el mes en
 * que se hizo.
 */
@Configuration
class TimeZoneConfiguration {

    @Bean
    ZoneId zoneId() {
        return ZoneId.of("America/Argentina/Buenos_Aires");
    }
}
