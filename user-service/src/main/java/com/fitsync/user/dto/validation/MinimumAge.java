package com.fitsync.user.dto.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * A birth date that makes the person at least {@link #years()} years old today, and that is not in the future.
 * A missing birth date is accepted, since birth date is optional; pair with {@code @NotNull} to require one.
 *
 * <p>FitSync is offered to people aged 16 and over (GDPR art. 8 and PDPA guidance on minors' consent), so this
 * is the server-side check behind the matching check in the sign-up UI.
 */
@Documented
@Constraint(validatedBy = MinimumAgeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MinimumAge {

    int years() default 16;

    String message() default "You must be at least {years} years old to use FitSync";

    /** Used instead of {@link #message()} when the date lies in the future. */
    String futureMessage() default "Birth date cannot be in the future";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
