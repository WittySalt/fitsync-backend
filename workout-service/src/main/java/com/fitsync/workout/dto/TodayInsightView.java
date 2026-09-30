package com.fitsync.workout.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.DayKind;
import com.fitsync.workout.domain.DaySource;

/** Today's own entry in the weekly training insight, carrying the extra detail (what to train,
 * how it last went, what from earlier this week is still outstanding) the plain {@link
 * DayInsightView} does not need for the other six days. */
public record TodayInsightView(
        LocalDate date,
        DayKind kind,
        String label,
        DaySource source,
        UUID planDayId,
        String title,
        Integer estimatedMinutes,
        Integer moves,
        LastOfKindView lastOfKind,
        List<CarriedOverView> carriedOver) {

    public TodayInsightView {
        carriedOver = carriedOver == null ? List.of() : List.copyOf(carriedOver);
    }

    @Override
    public List<CarriedOverView> carriedOver() {
        return List.copyOf(carriedOver);
    }
}
