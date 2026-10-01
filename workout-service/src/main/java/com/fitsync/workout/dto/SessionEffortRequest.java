package com.fitsync.workout.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** How hard a finished workout was, from 1 to 10, as the person chose it. */
public record SessionEffortRequest(@NotNull @Min(1) @Max(10) Integer perceivedExertion) {
}
