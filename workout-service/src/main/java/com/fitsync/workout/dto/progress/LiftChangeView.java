package com.fitsync.workout.dto.progress;

import java.math.BigDecimal;
import java.util.UUID;

/** One main lift's estimated best single at the start and the end of the window, for the card. */
public record LiftChangeView(UUID exerciseId, String name, BigDecimal e1rmStart, BigDecimal e1rmNow, int changePct) {
}
