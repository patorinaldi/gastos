package io.github.patorinaldi.gastos.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConnectivityTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void conectaContraUnPostgresReal() {
        String version = jdbcTemplate.queryForObject("select version()", String.class);
        assertThat(version).contains("PostgreSQL 17");
    }
}