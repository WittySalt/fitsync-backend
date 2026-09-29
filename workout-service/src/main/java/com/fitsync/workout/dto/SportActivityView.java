package com.fitsync.workout.dto;

import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.SportEffort;
import com.fitsync.workout.domain.SportMeasure;

/**
 * One entry in the sport activity vocabulary returned by the exercise metadata endpoint.
 */
public record SportActivityView(
        SportActivity code,
        String label,
        SportEffort effort,
        SportMeasure measure) {
}
