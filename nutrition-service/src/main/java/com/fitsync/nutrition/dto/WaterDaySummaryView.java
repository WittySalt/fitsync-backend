package com.fitsync.nutrition.dto;

import java.time.LocalDate;
import java.util.List;

public record WaterDaySummaryView(
        LocalDate date,
        String timezone,
        Integer totalMl,
        List<WaterLogView> entries,
        Integer fromDrinksMl,
        List<DrinkWaterEntryView> drinkEntries) {

    public WaterDaySummaryView {
        entries = entries == null ? List.of() : List.copyOf(entries);
        drinkEntries = drinkEntries == null ? List.of() : List.copyOf(drinkEntries);
    }

    @Override
    public List<DrinkWaterEntryView> drinkEntries() {
        return List.copyOf(drinkEntries);
    }

    @Override
    public List<WaterLogView> entries() {
        return List.copyOf(entries);
    }
}
