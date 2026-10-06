package com.fitsync.workout.controller;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.dto.progress.ActivityProgressView;
import com.fitsync.workout.dto.progress.ChartPoint;
import com.fitsync.workout.dto.progress.ExerciseProgressView;
import com.fitsync.workout.dto.progress.Highlight;
import com.fitsync.workout.dto.progress.PeriodRange;
import com.fitsync.workout.dto.progress.SeriesPoint;
import com.fitsync.workout.dto.progress.SummaryLine;
import com.fitsync.workout.dto.progress.Trend;
import com.fitsync.workout.dto.progress.TrainingProgressView;
import com.fitsync.workout.progress.ActivityKey;
import com.fitsync.workout.progress.ProgressPeriod;
import com.fitsync.workout.progress.TrainingProgressService;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.workout.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TrainingProgressController.class)
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class TrainingProgressControllerTests {

    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    private static final LocalDate FROM = LocalDate.parse("2026-09-07");
    private static final LocalDate TO = LocalDate.parse("2026-10-04");
    private static final ProgressPeriod PERIOD = new ProgressPeriod(FROM, TO);
    private static final UUID DEADLIFT = UUID.fromString("7f1c1a52-6c1f-4c55-9a43-1f0b8e0c2d11");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrainingProgressService trainingProgressService;

    @MockBean
    private JwtService jwtService;

    @Test
    void everyProgressReadNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/v1/workouts/progress/training").param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/workouts/progress/activities/STRENGTH")
                        .param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/workouts/progress/exercises/" + DEADLIFT)
                        .param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheTrainingPageWithTheZoneAndBodyWeightFromTheHeaders() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(trainingProgressService.getTraining(eq(principal), eq(SGT), eq(PERIOD), eq(new BigDecimal("71.2"))))
                .thenReturn(trainingView());

        mockMvc.perform(get("/api/v1/workouts/progress/training")
                        .with(authentication(authToken(principal)))
                        .header("X-Timezone", "Asia/Singapore")
                        .header("X-Body-Weight-Kg", "71.2")
                        .param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value("2026-09-07"))
                .andExpect(jsonPath("$.previous.to").value("2026-09-06"))
                .andExpect(jsonPath("$.hero.sessions.value").value(15))
                .andExpect(jsonPath("$.hero.load.estimatedShare").value(0.12))
                .andExpect(jsonPath("$.highlights[0].code").value("LIFT_UP"))
                .andExpect(jsonPath("$.highlights[0].series[0].label").value("2026-09-08"))
                .andExpect(jsonPath("$.load.weeks[0].byKind.STRENGTH").value(520))
                .andExpect(jsonPath("$.load.form").value("FRESH"))
                .andExpect(jsonPath("$.lifts[0].e1rm[1].isRecord").value(true))
                .andExpect(jsonPath("$.lifts[0].relative.level").value("NOVICE"))
                .andExpect(jsonPath("$.activities[0].summary[0].trend").value("UP"));
    }

    @Test
    void aBodyWeightOutsideThirtyToThreeHundredOrNotANumberMeansNoRatios() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(trainingProgressService.getTraining(eq(principal), eq(ZoneOffset.UTC), eq(PERIOD), isNull()))
                .thenReturn(trainingView());

        for (String header : List.of("12", "301", "eighty", "80kg", "-80")) {
            mockMvc.perform(get("/api/v1/workouts/progress/training")
                            .with(authentication(authToken(principal)))
                            .header("X-Timezone", "Not/AZone")
                            .header("X-Body-Weight-Kg", header)
                            .param("from", "2026-09-07").param("to", "2026-10-04"))
                    .andExpect(status().isOk());
        }
        verify(trainingProgressService, org.mockito.Mockito.times(5))
                .getTraining(eq(principal), eq(ZoneOffset.UTC), eq(PERIOD), isNull());
    }

    @Test
    void rejectsAPeriodThatEndsBeforeItStartsOrRunsPastThreeHundredSeventyDays() throws Exception {
        CurrentUserPrincipal principal = principal();
        mockMvc.perform(get("/api/v1/workouts/progress/training")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-10-04").param("to", "2026-09-07"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERIOD_INVALID"));
        mockMvc.perform(get("/api/v1/workouts/progress/training")
                        .with(authentication(authToken(principal)))
                        .param("from", "2025-09-28").param("to", "2026-10-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERIOD_TOO_LONG"));
        mockMvc.perform(get("/api/v1/workouts/progress/training")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-09-07"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/workouts/progress/training")
                        .with(authentication(authToken(principal)))
                        .param("from", "7 Sept").param("to", "2026-10-04"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsOneActivityAndRejectsAnUnknownKey() throws Exception {
        CurrentUserPrincipal principal = principal();
        ActivityProgressView view = new ActivityProgressView("SPORT:BADMINTON", "Badminton", SessionKind.SPORT,
                new PeriodRange(FROM, TO), new PeriodRange(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-09-06")),
                new ActivityProgressView.Tiles(245, 4, 4, new ActivityProgressView.Longest(65, LocalDate.parse("2026-09-26")),
                        new ActivityProgressView.UsualTime(DayOfWeek.SUNDAY, 15),
                        new ActivityProgressView.Effort(new BigDecimal("6.0"), 5, 7)),
                List.of(new ActivityProgressView.Week(LocalDate.parse("2026-09-28"), 0, 0, true)), List.of(),
                List.of(new ActivityProgressView.WhenCell(DayOfWeek.SUNDAY, 15, 3)),
                List.of(new SummaryLine("Once a week,", "Once a week, 4 weeks in a row", Trend.STEADY)),
                "+1 h 10 min on the 4 weeks before");
        when(trainingProgressService.getActivity(eq(principal), eq(SGT), eq(ActivityKey.parse("SPORT:BADMINTON")), eq(PERIOD)))
                .thenReturn(view);

        mockMvc.perform(get("/api/v1/workouts/progress/activities/SPORT:BADMINTON")
                        .with(authentication(authToken(principal)))
                        .header("X-Timezone", "Asia/Singapore")
                        .param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiles.usualTime.weekday").value("SUNDAY"))
                .andExpect(jsonPath("$.weekly[0].current").value(true))
                .andExpect(jsonPath("$.summary[0].text").value("Once a week, 4 weeks in a row"))
                .andExpect(jsonPath("$.context").value("+1 h 10 min on the 4 weeks before"));
        mockMvc.perform(get("/api/v1/workouts/progress/activities/YOGA")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVITY_KEY_INVALID"));
        verify(trainingProgressService, org.mockito.Mockito.times(1)).getActivity(any(), any(), any(), any());
    }

    @Test
    void returnsOneExerciseAndNotFoundForOneTheCallerCannotSee() throws Exception {
        CurrentUserPrincipal principal = principal();
        ExerciseProgressView view = new ExerciseProgressView(DEADLIFT, "Deadlift", MuscleGroup.BACK,
                List.of(MuscleGroup.LEGS), ExerciseEquipment.BARBELL, LocalDate.parse("2026-08-07"), 14,
                new PeriodRange(FROM, TO),
                new ExerciseProgressView.Tiles(new ExerciseProgressView.E1rmTile(new BigDecimal("93.3"),
                        new BigDecimal("11.6"), LocalDate.parse("2026-09-08")), null, null, 5, 3),
                new ExerciseProgressView.Series(List.of(new SeriesPoint(LocalDate.parse("2026-10-02"),
                        new BigDecimal("93.3"), true)), List.of(), List.of(), List.of()),
                List.of(), List.of());
        when(trainingProgressService.getExercise(eq(principal), eq(ZoneOffset.UTC), eq(DEADLIFT), eq(PERIOD)))
                .thenReturn(view);
        UUID hidden = UUID.randomUUID();
        when(trainingProgressService.getExercise(eq(principal), any(), eq(hidden), any()))
                .thenThrow(new ResourceNotFoundException("Exercise not found"));

        mockMvc.perform(get("/api/v1/workouts/progress/exercises/" + DEADLIFT)
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiles.e1rm.value").value(93.3))
                .andExpect(jsonPath("$.tiles.records").value(5))
                .andExpect(jsonPath("$.series.e1rm[0].isRecord").value(true));
        mockMvc.perform(get("/api/v1/workouts/progress/exercises/" + hidden)
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/workouts/progress/exercises/not-a-uuid")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isBadRequest());
    }

    private static TrainingProgressView trainingView() {
        TrainingProgressView.Lift lift = new TrainingProgressView.Lift(DEADLIFT, "Deadlift",
                List.of(new SeriesPoint(LocalDate.parse("2026-09-08"), new BigDecimal("81.7"), false),
                        new SeriesPoint(LocalDate.parse("2026-10-02"), new BigDecimal("93.3"), true)),
                new TrainingProgressView.BestSet(new BigDecimal("80"), 5, LocalDate.parse("2026-10-02")), 14,
                new TrainingProgressView.Relative(new BigDecimal("1.31"), TrainingProgressView.Level.NOVICE,
                        TrainingProgressView.Level.INTERMEDIATE, new BigDecimal("107.0")), new BigDecimal("71.2"));
        return new TrainingProgressView(new PeriodRange(FROM, TO),
                new PeriodRange(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-09-06")),
                new TrainingProgressView.Hero(new TrainingProgressView.Count(15, 13),
                        new TrainingProgressView.Count(710, 640),
                        new TrainingProgressView.LoadFigure(4320, 3960, new BigDecimal("0.12")),
                        new TrainingProgressView.RecordsFigure(7, 4, List.of("Deadlift"))),
                List.of(new Highlight("LIFT_UP", "Your deadlift's estimated best single is up 14% in four weeks.",
                        List.of(new ChartPoint("2026-09-08", new BigDecimal("81.7"))), "kg", "context")),
                List.of(new TrainingProgressView.Activity("STRENGTH", "Strength", 6, 290, List.of(),
                        List.of(new SummaryLine("e1RM +14%", "e1RM +14% across one lift", Trend.UP)), "context")),
                new TrainingProgressView.Load(List.of(new TrainingProgressView.LoadWeek(LocalDate.parse("2026-08-10"),
                        new TrainingProgressView.ByKind(520, 240, 180, 0), 940, false)), List.of(), List.of(),
                        TrainingProgressView.Form.FRESH, null),
                new TrainingProgressView.Muscles(new BigDecimal("4.0"), List.of(),
                        new TrainingProgressView.UsualRange(10, 20)),
                List.of(lift), List.of(), List.of());
    }

    private UsernamePasswordAuthenticationToken authToken(CurrentUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_" + principal.role()));
    }

    private CurrentUserPrincipal principal() {
        return new CurrentUserPrincipal(UUID.fromString("11111111-1111-1111-1111-111111111111"), "alice@example.com",
                "Alice", "USER");
    }
}
