package com.fitsync.contracts.events.workout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PersonalRecordAchievedPayload(
        UUID exerciseId,
        String exerciseNameSnapshot,
        UUID sessionId,
        List<RecordAchievement> records,
        Instant achievedAt) {

    public PersonalRecordAchievedPayload {
        records = records == null ? List.of() : List.copyOf(records);
    }

    @Override
    public List<RecordAchievement> records() {
        return List.copyOf(records);
    }

    public record RecordAchievement(
            String recordType,
            BigDecimal value) {
    }
}
