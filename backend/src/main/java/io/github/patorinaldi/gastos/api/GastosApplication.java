package io.github.patorinaldi.gastos.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GastosApplication {

    public static void main(String[] args) {
        SpringApplication.run(GastosApplication.class, args);
    }

}
