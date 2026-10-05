package io.github.patorinaldi.gastos.api.web.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limita el texto por su longitud en bytes UTF-8, y no por cantidad de caracteres como
 * {@code Size}.
 *
 * <p>Existe por bcrypt, que ignora todo lo que pase de 72 bytes. Una contraseña de 72 caracteres
 * con tildes o eñes pasa holgadamente ese límite en bytes, y ahí bcrypt la trunca sin avisar —dos
 * contraseñas distintas pasan a ser la misma— o la biblioteca falla y la petición responde 500 en
 * lugar de un error de validación.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "no puede superar {value} bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
