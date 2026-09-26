package com.fitsync.recommendation.coach.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A thread with all of its turns in order.")
public record CoachThreadDetailView(CoachThreadView thread, List<CoachTurnView> turns) {

    public CoachThreadDetailView {
        turns = turns == null ? List.of() : List.copyOf(turns);
    }

    @Override
    public List<CoachTurnView> turns() {
        return List.copyOf(turns);
    }
}
