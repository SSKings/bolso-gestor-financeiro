package com.sskings.bolso.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Texto livre sem caracteres de controle (quebra de linha, tab, NUL, códigos de terminal): nome e descrição aparecem
 * em listas, logs e, depois, em exportação CSV — um "\n" ou "\r" ali vira linha falsa no log ou no arquivo.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Pattern(regexp = "^[^\\p{Cntrl}\\u2028\\u2029]*$", message = "must not contain control characters")
public @interface PlainText {
    String message() default "must not contain control characters";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
