package io.github.patorinaldi.gastos.api.service.category;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Ya hay en el hogar una regla con el mismo patrón (RN-10). Se compara la clave del patrón, así que
 * {@code "burger king"} y {@code "burgerking"} son el mismo: los dos coinciden con exactamente los
 * mismos comercios, y solo los separaría la fecha de alta.
 *
 * <p>Cuando se sabe, el detalle dice a qué categoría apunta la regla existente (CU-05, 4a.1): para que
 * el patrón apunte a otra, hay que eliminar esa regla y volver a crearla.
 */
public class PatternTakenException extends ErrorResponseException {

    public PatternTakenException(String existingCategoryName) {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail(existingCategoryName)),
                null);
    }

    private static String detail(String existingCategoryName) {
        return existingCategoryName == null
                ? "Ya existe una regla con ese patrón."
                : "Ya existe una regla con ese patrón, para la categoría «" + existingCategoryName + "».";
    }
}
