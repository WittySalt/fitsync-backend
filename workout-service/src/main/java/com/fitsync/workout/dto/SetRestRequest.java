package com.fitsync.workout.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The rest the person actually took after a set, in seconds, at most six hours. */
public record SetRestRequest(@NotNull @Min(0) @Max(21600) Integer restTakenSeconds) {
}
