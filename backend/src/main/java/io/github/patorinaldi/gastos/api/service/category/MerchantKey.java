package io.github.patorinaldi.gastos.api.service.category;

import java.util.BitSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un comercio listo para compararlo con los patrones: su clave (ver {@link Patterns#matchKey}) y las
 * posiciones de la clave donde empieza una palabra del comercio.
 *
 * <p>Un patrón coincide si su clave aparece en la del comercio <em>empezando al principio de una
 * palabra</em>: al principio del texto o después de un carácter que no es letra ni número. Sin esa
 * condición, como la clave no tiene espacios, {@code uber} coincidía con "Pub Ernesto"
 * ({@code pubernesto}) y con "Tuberías del Sur", y {@code coto} con "Disco Tomás" y con "Escoto". El
 * error no se veía: el gasto no quedaba en la bandeja, donde alguien lo revisa, sino en una categoría
 * equivocada y en sus totales.
 *
 * <p>La coincidencia sí puede seguir más allá de esa palabra, que es lo que hace que
 * {@code supermercado} coincida con "Super Mercado Rosario" y {@code pedidosya} con "PEDIDOS YA". Y
 * puede terminar en medio de una palabra: {@code farmacia} coincide con "Farmacias del Pueblo".
 */
final class MerchantKey {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private final String key;
    private final BitSet wordStarts;

    private MerchantKey(String key, BitSet wordStarts) {
        this.key = key;
        this.wordStarts = wordStarts;
    }

    /** Las palabras son las tiras de letras y números del comercio normalizado; la clave es su unión. */
    static MerchantKey of(String merchant) {
        StringBuilder key = new StringBuilder();
        BitSet wordStarts = new BitSet();
        Matcher words = WORD.matcher(Patterns.normalize(merchant));
        while (words.find()) {
            wordStarts.set(key.length());
            key.append(words.group());
        }
        return new MerchantKey(key.toString(), wordStarts);
    }

    /** Si la clave del patrón aparece en la del comercio empezando en el principio de una palabra. */
    boolean matches(String patternKey) {
        if (patternKey.isEmpty()) {
            return false;
        }
        for (int at = key.indexOf(patternKey); at >= 0; at = key.indexOf(patternKey, at + 1)) {
            if (wordStarts.get(at)) {
                return true;
            }
        }
        return false;
    }
}
