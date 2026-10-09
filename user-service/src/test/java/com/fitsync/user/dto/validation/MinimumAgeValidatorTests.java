package com.fitsync.user.dto.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MinimumAgeValidatorTests {

    /** 2026-10-09 in UTC+14, which is the date the validator treats as today. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneOffset.ofHours(14));

    private final ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);

    @BeforeEach
    void setUp() {
        when(context.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(mock(ConstraintViolationBuilder.class));
    }

    @Test
    void acceptsAMissingBirthDateBecauseTheFieldIsOptional() {
        assertThat(validator(CLOCK).isValid(null, context)).isTrue();
    }

    @Test
    void acceptsSomeoneWhoTurnsSixteenToday() {
        assertThat(validator(CLOCK).isValid(LocalDate.of(2010, 10, 9), context)).isTrue();
    }

    @Test
    void rejectsSomeoneWhoTurnsSixteenTomorrow() {
        assertThat(validator(CLOCK).isValid(LocalDate.of(2010, 10, 10), context)).isFalse();
    }

    @Test
    void acceptsAnAdult() {
        assertThat(validator(CLOCK).isValid(LocalDate.of(1990, 1, 1), context)).isTrue();
    }

    @Test
    void countsALeapDayBirthdayFromTheLeapDay() {
        // Someone born on 29 Feb 2012 is still 15 on 28 Feb 2028 and turns 16 on 29 Feb 2028.
        Clock feb28 = Clock.fixed(Instant.parse("2028-02-27T12:00:00Z"), ZoneOffset.ofHours(14));
        Clock feb29 = Clock.fixed(Instant.parse("2028-02-28T12:00:00Z"), ZoneOffset.ofHours(14));

        assertThat(validator(feb28).isValid(LocalDate.of(2012, 2, 29), context)).isFalse();
        assertThat(validator(feb29).isValid(LocalDate.of(2012, 2, 29), context)).isTrue();
    }

    @Test
    void rejectsAFutureDateWithItsOwnMessage() {
        assertThat(validator(CLOCK).isValid(LocalDate.of(2026, 10, 10), context)).isFalse();

        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Birth date cannot be in the future");
    }

    private static MinimumAgeValidator validator(Clock clock) {
        MinimumAge annotation = mock(MinimumAge.class);
        when(annotation.years()).thenReturn(16);
        when(annotation.futureMessage()).thenReturn("Birth date cannot be in the future");
        MinimumAgeValidator validator = new MinimumAgeValidator(clock);
        validator.initialize(annotation);
        return validator;
    }
}
