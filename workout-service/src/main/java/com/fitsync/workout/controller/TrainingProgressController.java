package com.fitsync.workout.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fitsync.web.timezone.RequestTimezone;
import com.fitsync.workout.dto.progress.ActivityProgressView;
import com.fitsync.workout.dto.progress.ExerciseProgressView;
import com.fitsync.workout.dto.progress.TrainingProgressView;
import com.fitsync.workout.progress.ActivityKey;
import com.fitsync.workout.progress.ProgressPeriod;
import com.fitsync.workout.progress.StrengthMath;
import com.fitsync.workout.progress.TrainingProgressService;
import com.fitsync.workout.security.CurrentUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The period reads behind Progress: the Training page, one activity and one exercise. The user comes
 * from the token; the zone from X-Timezone, else UTC. {@code from} and {@code to} are inclusive dates,
 * at most 370 days apart, and every figure with a comparison also covers the period just before.
 */
@RestController
@Validated
@RequestMapping("/api/v1/workouts/progress")
public class TrainingProgressController {

    /** Sent by the frontend from the profile; read only for relative strength. */
    public static final String BODY_WEIGHT_HEADER = "X-Body-Weight-Kg";

    private static final Pattern BODY_WEIGHT = Pattern.compile("^\\d{1,3}(\\.\\d{1,2})?$");

    private final TrainingProgressService trainingProgressService;

    public TrainingProgressController(TrainingProgressService trainingProgressService) {
        this.trainingProgressService = trainingProgressService;
    }

    @Operation(summary = "Training progress for a period")
    @GetMapping("/training")
    public TrainingProgressView getTraining(
            Authentication authentication,
            @RequestTimezone Optional<ZoneId> zone,
            @RequestHeader(name = BODY_WEIGHT_HEADER, required = false) String bodyWeight,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return trainingProgressService.getTraining(currentPrincipal(authentication), zoneOf(zone),
                new ProgressPeriod(from, to), parseBodyWeight(bodyWeight));
    }

    @Operation(summary = "Progress of one activity for a period")
    @GetMapping("/activities/{key}")
    public ActivityProgressView getActivity(
            Authentication authentication,
            @RequestTimezone Optional<ZoneId> zone,
            @PathVariable @Size(max = 60) String key,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return trainingProgressService.getActivity(currentPrincipal(authentication), zoneOf(zone),
                ActivityKey.parse(key), new ProgressPeriod(from, to));
    }

    @Operation(summary = "Progress of one exercise for a period")
    @GetMapping("/exercises/{exerciseId}")
    public ExerciseProgressView getExercise(
            Authentication authentication,
            @RequestTimezone Optional<ZoneId> zone,
            @PathVariable UUID exerciseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return trainingProgressService.getExercise(currentPrincipal(authentication), zoneOf(zone), exerciseId,
                new ProgressPeriod(from, to));
    }

    /** A body weight from 30 to 300 kg with at most two decimals; anything else means no ratios. */
    static BigDecimal parseBodyWeight(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (!BODY_WEIGHT.matcher(value).matches()) {
            return null;
        }
        return StrengthMath.acceptedBodyWeight(new BigDecimal(value));
    }

    private static ZoneId zoneOf(Optional<ZoneId> zone) {
        return zone.orElse(ZoneOffset.UTC);
    }

    private CurrentUserPrincipal currentPrincipal(Authentication authentication) {
        return (CurrentUserPrincipal) authentication.getPrincipal();
    }
}
