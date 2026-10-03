package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.dto.progress.ActivityProgressView;
import com.fitsync.workout.dto.progress.ExerciseProgressView;
import com.fitsync.workout.dto.progress.SummaryLine;
import com.fitsync.workout.dto.progress.Trend;
import com.fitsync.workout.progress.ExerciseProgressCalculator.ExerciseInfo;
import org.junit.jupiter.api.Test;

import static com.fitsync.workout.progress.Snaps.deadlift;
import static com.fitsync.workout.progress.Snaps.set;
import static com.fitsync.workout.progress.Snaps.sport;
import static com.fitsync.workout.progress.Snaps.strength;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The activity page and the exercise page, run on hand-built sessions without Spring. */
class ActivityAndExerciseProgressCalculatorTests {

    private static final ProgressPeriod PERIOD = new ProgressPeriod(LocalDate.parse("2026-09-07"),
            LocalDate.parse("2026-10-04"));
    private static final LocalDate SATURDAY = LocalDate.parse("2026-10-03");

    private static List<SessionSnap> badminton() {
        return List.of(
                sport("2026-08-16", "15:00", SportActivity.BADMINTON, 60, 6),
                sport("2026-08-23", "15:00", SportActivity.BADMINTON, 45, 5),
                sport("2026-09-08", "19:00", SportActivity.TENNIS, 90, 7),
                sport("2026-09-13", "15:00", SportActivity.BADMINTON, 60, 5),
                sport("2026-09-20", "15:10", SportActivity.BADMINTON, 60, 6),
                sport("2026-09-27", "15:20", SportActivity.BADMINTON, 65, 7));
    }

    @Test
    void buildsTheActivityPageFromItsOwnSessionsOnly() {
        ActivityProgressView view = ActivityProgressCalculator.compute(ActivityKey.parse("SPORT:BADMINTON"), PERIOD,
                SATURDAY, badminton(), Map.of());

        assertEquals("SPORT:BADMINTON", view.key());
        assertEquals("Badminton", view.name());
        assertEquals(SessionKind.SPORT, view.kind());
        assertEquals(185, view.tiles().minutes());
        assertEquals(3, view.tiles().sessions());
        assertEquals(3, view.tiles().weeksInARow());
        assertEquals(65, view.tiles().longestSession().minutes());
        assertEquals(LocalDate.parse("2026-09-27"), view.tiles().longestSession().date());
        assertEquals(DayOfWeek.SUNDAY, view.tiles().usualTime().weekday());
        assertEquals(15, view.tiles().usualTime().hour());
        assertEquals(new BigDecimal("6.0"), view.tiles().effort().average());
        assertEquals(5, view.tiles().effort().first());
        assertEquals(7, view.tiles().effort().last());

        assertEquals(8, view.weekly().size());
        assertEquals(LocalDate.parse("2026-08-10"), view.weekly().get(0).weekStart());
        assertEquals(60, view.weekly().get(0).minutes());
        assertTrue(view.weekly().get(7).current());
        assertFalse(view.weekly().get(6).current());

        assertEquals(3, view.sessions().size());
        assertEquals(65, view.sessions().get(0).minutes());
        assertEquals(1, view.whenGrid().size());
        assertEquals(DayOfWeek.SUNDAY, view.whenGrid().get(0).weekday());
        assertEquals(3, view.whenGrid().get(0).count());
        assertEquals("Once a week, 3 weeks in a row", view.summary().get(0).text());
        assertEquals("+1 h 20 min on the 4 weeks before", view.context());
    }

    @Test
    void anActivityWithoutSessionsHasEmptyFiguresAndNoUsualTime() {
        ActivityProgressView view = ActivityProgressCalculator.compute(ActivityKey.parse("MOBILITY"), PERIOD,
                SATURDAY, badminton(), Map.of());

        assertEquals("Mobility", view.name());
        assertEquals(0, view.tiles().sessions());
        assertNull(view.tiles().longestSession());
        assertNull(view.tiles().usualTime());
        assertNull(view.tiles().effort());
        assertEquals("Same time as the 4 weeks before", view.context());
    }

    @Test
    void rejectsAnUnknownActivityKey() {
        assertThrows(com.fitsync.web.error.BadRequestException.class, () -> ActivityKey.parse("SPORT:CURLING"));
        assertThrows(com.fitsync.web.error.BadRequestException.class, () -> ActivityKey.parse("SPORT"));
        assertThrows(com.fitsync.web.error.BadRequestException.class, () -> ActivityKey.parse("YOGA"));
        assertEquals("SPORT:TABLE_TENNIS", ActivityKey.parse("sport:table_tennis").key());
    }

    @Test
    void buildsTheExercisePageFromTheWholeHistory() {
        List<SessionSnap> history = List.of(
                strength("2026-08-20", 50, 7, deadlift(set("90", 5))),
                strength("2026-09-08", 50, 7, deadlift(set("80", 5))),
                strength("2026-09-15", 50, 7, deadlift(set("90", 6))),
                strength("2026-09-22", 50, 7, deadlift(set("95", 5), set("100", 2))));
        ExerciseInfo info = new ExerciseInfo(Snaps.DEADLIFT, "Deadlift", MuscleGroup.BACK,
                List.of(MuscleGroup.LEGS, MuscleGroup.GLUTES), ExerciseEquipment.BARBELL);

        ExerciseProgressView view = ExerciseProgressCalculator.compute(info, PERIOD, history);

        assertEquals(LocalDate.parse("2026-08-20"), view.firstSeen());
        assertEquals(4, view.sessions());
        assertEquals(new BigDecimal("110.8"), view.tiles().e1rm().value());
        assertEquals(new BigDecimal("17.5"), view.tiles().e1rm().changeKg());
        assertEquals(LocalDate.parse("2026-09-08"), view.tiles().e1rm().since());
        assertEquals(new BigDecimal("100.0"), view.tiles().heaviestSet().weightKg());
        assertEquals(2, view.tiles().heaviestSet().reps());
        assertEquals(new BigDecimal("675.0"), view.tiles().bestVolume().kg());
        assertEquals(2, view.tiles().records());
        assertEquals(2, view.tiles().recordsInPeriod());
        assertEquals(3, view.series().e1rm().size());
        assertEquals(List.of(false, true, true), view.series().e1rm().stream().map(p -> p.isRecord()).toList());
        assertEquals(new BigDecimal("5"), view.series().reps().get(0).value());
        assertEquals(LocalDate.parse("2026-09-22"), view.history().get(0).date());
        assertTrue(view.history().get(0).record());
        assertEquals(2, view.history().get(0).sets().size());
        assertEquals(List.of(
                new SummaryLine("e1RM 93.3 → 110.8 kg", "e1RM 93.3 → 110.8 kg estimated best single", Trend.UP),
                new SummaryLine("Heaviest set 100 kg × 2", "Heaviest set 100 kg × 2 against 90 kg before",
                        Trend.UP),
                new SummaryLine("3 sessions", "3 sessions against 1 in the 4 weeks before", Trend.UP)), view.summary());
    }

    @Test
    void anExerciseNeverDoneHasNoTiles() {
        ExerciseInfo info = new ExerciseInfo(Snaps.DEADLIFT, "Deadlift", MuscleGroup.BACK, List.of(),
                ExerciseEquipment.BARBELL);
        ExerciseProgressView view = ExerciseProgressCalculator.compute(info, PERIOD, List.of());
        assertNull(view.firstSeen());
        assertNull(view.tiles().e1rm());
        assertNull(view.tiles().heaviestSet());
        assertEquals(0, view.tiles().records());
        assertEquals("0 sessions against 0 in the 4 weeks before", view.summary().get(0).text());
    }
}
