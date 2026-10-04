package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fitsync.workout.domain.MuscleGroup;

/**
 * What a plan trains: the planned hard sets a week per muscle group (primary 1, secondary 0.5 per set,
 * over the strength days) and the notes the plan editor reads out, each with a fixed code and text.
 */
public record PlanCoverageView(List<GroupSets> groups, List<Note> notes) {

    public PlanCoverageView {
        groups = groups == null ? List.of() : List.copyOf(groups);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    @Override
    public List<GroupSets> groups() {
        return List.copyOf(groups);
    }

    @Override
    public List<Note> notes() {
        return List.copyOf(notes);
    }

    /** One group's planned hard sets a week, to one decimal. */
    public record GroupSets(MuscleGroup group, BigDecimal setsPerWeek) {
    }

    /**
     * One note: CALVES_NOT_TRAINED, NO_VERTICAL_PULL, SHOULDERS_HELPER_ONLY, STRENGTH_BACK_TO_BACK or
     * OVER_PROFILE_MINUTES.
     */
    public record Note(String code, String text) {
    }
}
