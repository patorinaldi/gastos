package io.github.patorinaldi.gastos.api.service.category;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cómo se escribe un patrón y cómo se compara contra un comercio (M4, RF-20).
 *
 * <p>Hay dos formas del mismo texto:
 * <ul>
 *   <li><strong>La forma guardada</strong> ({@link #normalize}): en minúscula, sin tildes y con los
 *       espacios colapsados, como los patrones de la V3. Es la que ve el usuario en el listado de
 *       reglas, y la que compara el índice único de RN-10.</li>
 *   <li><strong>La clave de comparación</strong> ({@link #matchKey}): la forma guardada solo con letras
 *       y números. Una regla coincide con un comercio cuando la clave de su patrón está contenida en
 *       la del comercio, empezando al principio de una palabra (ver {@link MerchantKey}). Así
 *       "Super Mercado Rosario" coincide con {@code supermercado} (RN-17), "McDonald's" con
 *       {@code mcdonald} y "PEDIDOS YA" con {@code pedidosya}: el comercio se tipea a mano o llega de
 *       una notificación del banco, y los espacios y la puntuación varían.</li>
 * </ul>
 *
 * <p>Las tildes se quitan con la descomposición de compatibilidad (NFKD), que además lleva a su forma
 * simple las letras de ancho completo y las ligaduras que a veces traen los textos copiados. La eñe
 * queda como ene, igual en el patrón y en el comercio.
 */
public final class Patterns {

    /**
     * Letras o números que tiene que tener, como mínimo, la clave de un patrón. Un patrón más corto
     * coincidiría con comercios que no tienen nada que ver: {@code "a"} clasificaría casi todo. Los
     * patrones más cortos de la V3 tienen tres ({@code ypf}).
     */
    public static final int MIN_KEY_LENGTH = 3;

    /**
     * Largo máximo de la forma guardada: la columna es {@code varchar(200)}. Se controla después de
     * normalizar, porque NFKD puede alargar el texto: la ligadura {@code ﬃ} pasa a ser {@code ffi}, y
     * {@code ㎏} o {@code ½} también se expanden. Un patrón que pasa el {@code Size} del contrato podía
     * llegar al insert con 600 caracteres.
     */
    public static final int MAX_LENGTH = 200;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    // Espacios de todo tipo, saltos de línea, tabulaciones y caracteres de control o de formato.
    private static final Pattern SEPARATORS = Pattern.compile("[\\p{Z}\\s\\p{Cc}\\p{Cf}]+");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private Patterns() {
    }

    /** La forma en que se guarda un patrón: minúscula, sin tildes, con un solo espacio entre palabras. */
    public static String normalize(String text) {
        String withoutMarks = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKD)).replaceAll("");
        return SEPARATORS.matcher(withoutMarks.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /** La clave con la que se compara: la forma normalizada solo con letras y números. */
    public static String matchKey(String text) {
        return NOT_LETTER_OR_DIGIT.matcher(normalize(text)).replaceAll("");
    }
}
