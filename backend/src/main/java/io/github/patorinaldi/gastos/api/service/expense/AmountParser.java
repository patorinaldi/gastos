package io.github.patorinaldi.gastos.api.service.expense;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Normalización defensiva de un importe escrito como texto (RF-32, RN-06).
 *
 * <p>Lo usa la captura desde clientes automatizados (M6), que recibe el importe tal como se leyó de
 * una notificación del banco o de un mensaje: en formato local ({@code "1.234,56"}), anglosajón
 * ({@code "1,234.56"}) o sin separadores, y a veces con el signo {@code $} adelante. El alta desde la
 * interfaz no pasa por acá: su contrato ya pide el importe como cadena decimal con punto (RD-01).
 *
 * <p>Defensiva quiere decir que ante la duda rechaza. Un importe mal interpretado no da error: queda
 * registrado, y sale en los totales del mes. Por eso:
 * <ul>
 *   <li>con los dos separadores, el último es el decimal y el otro tiene que agrupar de a tres
 *       ({@code "1.234,56"}, {@code "1,234.56"});</li>
 *   <li>con un solo separador que aparece una vez, seguido de uno o dos dígitos es decimal
 *       ({@code "12,5"}, {@code "12.50"}), y seguido de tres es de miles ({@code "1.234"}). Leído
 *       como decimal, {@code "1.234"} tendría tres decimales, que RN-06 no admite: las dos lecturas
 *       válidas coinciden;</li>
 *   <li>un separador repetido es siempre de miles ({@code "1.234.567"});</li>
 *   <li>un grupo de miles que empieza en cero ({@code "0.500"}), más de dos decimales, espacios en el
 *       medio, signos u otras monedas se rechazan, en lugar de adivinar.</li>
 * </ul>
 *
 * <p>El resultado tiene siempre dos decimales y entra en {@code numeric(12, 2)}. Nunca se redondea:
 * un tercer decimal es un error, no un centavo de más o de menos.
 */
public final class AmountParser {

    /** Dígitos enteros que admite la columna {@code numeric(12, 2)}. */
    static final int MAX_INTEGER_DIGITS = 10;

    private static final int SCALE = 2;

    // \p{Z} incluye el espacio duro y los espacios finos, que String.strip() no recorta.
    private static final Pattern EDGE_SPACES = Pattern.compile("^[\\p{Z}\\s]+|[\\p{Z}\\s]+$");
    private static final Pattern CURRENCY = Pattern.compile("^(?:AR\\$|ARS|\\$)|ARS$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS_AND_SEPARATORS = Pattern.compile("[0-9.,]+");
    private static final Pattern PLAIN_DIGITS = Pattern.compile("[0-9]+");

    private AmountParser() {
    }

    /**
     * @return el importe, mayor que cero y con dos decimales
     * @throws InvalidAmountException si el texto no es un importe o no se puede interpretar sin ambigüedad
     */
    public static BigDecimal parse(String raw) {
        if (raw == null) {
            throw new InvalidAmountException("falta");
        }
        String text = trim(CURRENCY.matcher(trim(raw)).replaceAll(""));
        if (text.isEmpty()) {
            throw new InvalidAmountException("no tiene ningún número");
        }
        if (text.startsWith("-")) {
            throw new InvalidAmountException("tiene que ser mayor que cero");
        }
        if (!DIGITS_AND_SEPARATORS.matcher(text).matches()) {
            throw new InvalidAmountException("solo puede tener dígitos y los separadores de miles y decimales");
        }

        BigDecimal amount = new BigDecimal(normalize(text));
        if (amount.signum() <= 0) {
            throw new InvalidAmountException("tiene que ser mayor que cero");
        }
        if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidAmountException("no puede tener más de " + MAX_INTEGER_DIGITS + " dígitos enteros");
        }
        return amount.setScale(SCALE);
    }

    /** Lleva el texto, ya limpio, a la forma de {@link BigDecimal}: sin separador de miles y con punto decimal. */
    private static String normalize(String text) {
        int dots = count(text, '.');
        int commas = count(text, ',');
        if (dots == 0 && commas == 0) {
            return text;
        }

        char decimal;
        char grouping;
        if (dots > 0 && commas > 0) {
            decimal = text.lastIndexOf('.') > text.lastIndexOf(',') ? '.' : ',';
            grouping = decimal == '.' ? ',' : '.';
            if (count(text, decimal) > 1) {
                throw new InvalidAmountException("tiene separadores de miles y decimales mezclados");
            }
        } else {
            char separator = dots > 0 ? '.' : ',';
            int digitsAfter = text.length() - text.lastIndexOf(separator) - 1;
            boolean isDecimal = Math.max(dots, commas) == 1 && digitsAfter != 3;
            decimal = isDecimal ? separator : 0;
            grouping = isDecimal ? 0 : separator;
        }

        int decimalAt = decimal == 0 ? -1 : text.indexOf(decimal);
        String integerPart = decimalAt < 0 ? text : text.substring(0, decimalAt);
        String fraction = decimalAt < 0 ? "" : text.substring(decimalAt + 1);
        if (decimalAt >= 0 && !PLAIN_DIGITS.matcher(fraction).matches()) {
            throw new InvalidAmountException("no tiene dígitos después del separador decimal");
        }
        if (fraction.length() > SCALE) {
            throw new InvalidAmountException("no puede tener más de dos decimales");
        }
        return integerDigits(integerPart, grouping) + (fraction.isEmpty() ? "" : "." + fraction);
    }

    /** La parte entera sin separadores de miles, que tienen que agrupar de a tres. */
    private static String integerDigits(String integerPart, char grouping) {
        if (PLAIN_DIGITS.matcher(integerPart).matches()) {
            return integerPart;
        }
        if (grouping == 0) {
            throw new InvalidAmountException("no tiene dígitos antes del separador decimal");
        }
        String group = Pattern.quote(String.valueOf(grouping));
        if (!integerPart.matches("[1-9][0-9]{0,2}(?:" + group + "[0-9]{3})+")) {
            throw new InvalidAmountException("tiene los separadores de miles mal ubicados");
        }
        return integerPart.replace(String.valueOf(grouping), "");
    }

    private static String trim(String text) {
        return EDGE_SPACES.matcher(text).replaceAll("");
    }

    private static int count(String text, char c) {
        return (int) text.chars().filter(ch -> ch == c).count();
    }
}
