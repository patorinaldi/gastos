package io.github.patorinaldi.gastos.api.service.expense;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Normalización defensiva de importes (RF-32, RN-06). Un importe mal interpretado no da error: queda
 * registrado y sale en los totales. Por eso cada formato que se acepta tiene una única lectura, y
 * todo lo demás se rechaza.
 */
class AmountParserTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource(delimiter = '|', value = {
            // Formato local
            "1.234,56      | 1234.56",
            "1.234.567,89  | 1234567.89",
            "12,5          | 12.50",
            "0,99          | 0.99",
            // Formato anglosajón
            "1,234.56      | 1234.56",
            "1,234,567.89  | 1234567.89",
            "12.50         | 12.50",
            // Sin separador decimal
            "1234          | 1234.00",
            "1.234         | 1234.00",
            "1,234         | 1234.00",
            "1.234.567     | 1234567.00",
            // Con moneda y espacios, como en una notificación del banco
            "$ 1.234,56    | 1234.56",
            "$1.234,56     | 1234.56",
            "AR$ 99,90     | 99.90",
            "ars 99,90     | 99.90",
            "99,90 ARS     | 99.90",
    })
    void interpretaLosFormatosQueMandaUnCliente(String raw, String expected) {
        assertThat(AmountParser.parse(raw)).isEqualTo(new BigDecimal(expected));
    }

    // El resultado tiene siempre dos decimales: es lo que se guarda y lo que se responde.
    @Test
    void elResultadoTieneDosDecimales() {
        assertThat(AmountParser.parse("12").scale()).isEqualTo(2);
        assertThat(AmountParser.parse("12,5").scale()).isEqualTo(2);
    }

    // El espacio duro y el espacio fino no los recorta String.strip(), y aparecen al copiar de una
    // notificación o de una página web.
    @Test
    void recortaEspaciosDurosYFinos() {
        assertThat(AmountParser.parse(" $ 1.234,56 ")).isEqualTo(new BigDecimal("1234.56"));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
            "", "   ", "$", "abc", "12a", "US$ 10", "10 €",
            // Espacios en el medio: "1 234" puede ser un separador o dos números pegados
            "1 234,56",
            // Signos
            "-100", "+100", "--",
            // Separadores sin dígitos a un lado
            ",50", "12,", "1.234,",
    })
    void rechazaLoQueNoEsUnImporte(String raw) {
        assertThatThrownBy(() -> AmountParser.parse(raw)).isInstanceOf(InvalidAmountException.class);
    }

    // Ninguno de estos tiene una lectura única: se rechazan en lugar de adivinar.
    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
            // Más de dos decimales (RN-06), en cualquiera de los dos formatos
            "1.234,567", "1,234.567", "12,3456",
            // Grupos de miles que no son de a tres
            "1.23.456", "12.34.567,89", "1,2345.00",
            // Un grupo de miles que empieza en cero: ¿0,5 o 500?
            "0.500", "0,500",
            // El separador decimal repetido
            "1.234,56,78", "1,234.56.78",
    })
    void rechazaLosFormatosAmbiguos(String raw) {
        assertThatThrownBy(() -> AmountParser.parse(raw)).isInstanceOf(InvalidAmountException.class);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"0", "0,00", "0.00", "000"})
    void rechazaElCero(String raw) {
        assertThatThrownBy(() -> AmountParser.parse(raw))
                .isInstanceOfSatisfying(InvalidAmountException.class,
                        e -> assertThat(e.getBody().getDetail()).isEqualTo("El importe tiene que ser mayor que cero."));
    }

    // numeric(12, 2): diez dígitos enteros. Uno más no entra en la columna.
    @Test
    void rechazaUnImporteQueNoEntraEnLaColumna() {
        assertThat(AmountParser.parse("9.999.999.999,99")).isEqualTo(new BigDecimal("9999999999.99"));
        assertThatThrownBy(() -> AmountParser.parse("10.000.000.000,00"))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void rechazaUnImporteNulo() {
        assertThatThrownBy(() -> AmountParser.parse(null)).isInstanceOf(InvalidAmountException.class);
    }

    // El detalle explica qué está mal, pero no repite el valor recibido.
    @Test
    void elErrorExplicaElMotivoSinRepetirElValor() {
        assertThatThrownBy(() -> AmountParser.parse("1.234,567"))
                .isInstanceOfSatisfying(InvalidAmountException.class, e -> {
                    assertThat(e.getBody().getDetail()).isEqualTo("El importe no puede tener más de dos decimales.");
                    assertThat(e.getBody().getDetail()).doesNotContain("1.234,567");
                });
    }
}
