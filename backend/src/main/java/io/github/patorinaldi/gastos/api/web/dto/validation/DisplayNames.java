package io.github.patorinaldi.gastos.api.web.dto.validation;

/**
 * Validación de los nombres que se muestran en pantalla: el de una persona, el de un token de
 * cliente máquina, el comercio de un gasto. Se usa con {@code @Pattern(regexp = DisplayNames.PATTERN, message = DisplayNames.MESSAGE)}.
 *
 * <p>Rechaza los caracteres de control, también los de Unicode (C1) y no solo los ASCII, que es lo
 * único que cubre {@code \p{Cntrl}} sin {@code (?U)}: Postgres no puede guardar el carácter nulo en
 * un texto, y sin este control llegaría al insert y volvería como error interno. Rechaza también los
 * caracteres de formato, como el override de dirección o el espacio de ancho cero, que harían que un
 * nombre se vea al revés o vacío, y exige al menos una letra, número, símbolo o signo: un nombre
 * hecho solo de espacios duros pasa {@code @NotBlank}, que no los considera espacio.
 *
 * <p>La unión de ancho cero (U+200D) sí se acepta: arma los emoji compuestos.
 */
public final class DisplayNames {

    public static final String PATTERN =
            "(?U)(?=.*[\\p{L}\\p{N}\\p{S}\\p{P}])(?:[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]|\\u200D)*";

    public static final String MESSAGE =
            "necesita al menos un carácter visible y no puede contener caracteres de control ni invisibles";

    private DisplayNames() {
    }
}
