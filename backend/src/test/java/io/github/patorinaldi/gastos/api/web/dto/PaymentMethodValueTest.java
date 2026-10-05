package io.github.patorinaldi.gastos.api.web.dto;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las etiquetas de los medios de pago están escritas en cuatro lugares: este enumerado, el del
 * dominio, el check de la migración V1 y el tipo de TypeScript del cliente. Si dejan de coincidir,
 * la traducción entre la API y el dominio falla.
 *
 * <p>Esta prueba ata los dos lados de Java. Se prefiere a derivar uno del otro, que acoplaría la
 * capa web al dominio: acá los tipos siguen siendo independientes y la divergencia se descubre al
 * compilar la batería, no en producción. La base ya está atada por el conversor del dominio, y el
 * tipo de TypeScript queda cubierto por la revisión, que es el eslabón que no se puede verificar
 * desde acá.
 */
class PaymentMethodValueTest {

    @Test
    void mirrorsTheDomainEnumConstantByConstant() {
        assertThat(Arrays.stream(PaymentMethodValue.values()).map(Enum::name))
                .containsExactly(Arrays.stream(PaymentMethod.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    void usesTheSameLabelsThatTheDatabaseStores() {
        for (PaymentMethodValue value : PaymentMethodValue.values()) {
            assertThat(value.label())
                    .isEqualTo(PaymentMethod.valueOf(value.name()).databaseValue());
        }
    }

    @Test
    void isParsedFromTheLabel() {
        assertThat(PaymentMethodValue.from("Tarjeta")).isEqualTo(PaymentMethodValue.TARJETA);
    }

    // Un cliente que mande "tarjeta" quiso decir Tarjeta: no hay ambigüedad posible.
    @Test
    void ignoresTheCaseOfTheLabel() {
        assertThat(PaymentMethodValue.from("tarjeta")).isEqualTo(PaymentMethodValue.TARJETA);
    }

    @Test
    void rejectsAValueOutsideTheClosedSet() {
        assertThatThrownBy(() -> PaymentMethodValue.from("Cripto"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cripto");
    }
}
