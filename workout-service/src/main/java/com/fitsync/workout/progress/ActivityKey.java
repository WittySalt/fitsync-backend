package com.fitsync.workout.progress;

import com.fitsync.web.error.BadRequestException;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;

/** A parsed activity key, matched exactly: STRENGTH, CARDIO, MOBILITY or SPORT:&lt;activity code&gt;. */
public record ActivityKey(SessionKind kind, SportActivity sport) {

    private static final int MAX_LENGTH = 60;

    public static ActivityKey parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH) {
            throw invalid();
        }
        String value = raw.trim();
        if (value.startsWith("SPORT:")) {
            String code = value.substring("SPORT:".length());
            for (SportActivity activity : SportActivity.values()) {
                if (activity.name().equals(code)) {
                    return new ActivityKey(SessionKind.SPORT, activity);
                }
            }
            throw invalid();
        }
        for (SessionKind kind : SessionKind.values()) {
            if (kind != SessionKind.SPORT && kind.name().equals(value)) {
                return new ActivityKey(kind, null);
            }
        }
        throw invalid();
    }

    public String key() {
        return kind == SessionKind.SPORT ? "SPORT:" + sport.name() : kind.name();
    }

    private static BadRequestException invalid() {
        return new BadRequestException("ACTIVITY_KEY_INVALID",
                "The activity key must be STRENGTH, CARDIO, MOBILITY or SPORT:<activity>.");
    }
}
