package com.fitsync.nutrition.dto;

import java.time.LocalDate;
import java.util.List;

public record WaterDaySummaryView(
        LocalDate date,
        String timezone,
        Integer totalMl,
        List<WaterLogView> entries) {

    public WaterDaySummaryView {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public List<WaterLogView> entries() {
        return List.copyOf(entries);
    }
}
