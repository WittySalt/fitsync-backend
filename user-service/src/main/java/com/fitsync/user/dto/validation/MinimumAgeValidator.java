package com.fitsync.user.dto.validation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Checks {@link MinimumAge}. "Today" is the date in the earliest time zone (UTC+14), so a person whose birthday
 * has already arrived anywhere in the world is never turned away because the server's day has not yet turned.
 * The check is therefore at most one day more lenient than the person's own local date, never stricter.
 */
public class MinimumAgeValidator implements ConstraintValidator<MinimumAge, LocalDate> {

    static final ZoneOffset EARLIEST_ZONE = ZoneOffset.ofHours(14);

    private final Clock clock;
    private int years;
    private String futureMessage;

    public MinimumAgeValidator() {
        this(Clock.system(EARLIEST_ZONE));
    }

    MinimumAgeValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void initialize(MinimumAge annotation) {
        this.years = annotation.years();
        this.futureMessage = annotation.futureMessage();
    }

    @Override
    public boolean isValid(LocalDate birthDate, ConstraintValidatorContext context) {
        if (birthDate == null) {
            return true;
        }
        LocalDate today = LocalDate.now(clock);
        if (birthDate.isAfter(today)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(futureMessage).addConstraintViolation();
            return false;
        }
        // On the birthday itself the person has turned that age, so the boundary date is accepted.
        return !birthDate.isAfter(today.minusYears(years));
    }
}
