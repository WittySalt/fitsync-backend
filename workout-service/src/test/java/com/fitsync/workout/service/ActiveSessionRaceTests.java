package com.fitsync.workout.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.media.MediaStorageService;
import com.fitsync.web.error.ConflictException;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/**
 * Two simultaneous starts both pass the service's check; on PostgreSQL the one-in-progress index
 * (V18) then refuses the second insert. H2 has no such index, so the refusal is stubbed on the
 * repository, and the start that loses must get the same 409 the check gives.
 */
@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images"
})
@ActiveProfiles("test")
class ActiveSessionRaceTests {

    private static final String INDEX = "uq_workout_sessions_one_in_progress_per_user";

    @Autowired
    private WorkoutService workoutService;

    @SpyBean
    private WorkoutSessionRepository workoutSessionRepository;

    @MockBean
    private MediaStorageService mediaStorageService;

    @MockBean
    private UpstreamHttpImageSource upstreamHttpImageSource;

    private final CurrentUserPrincipal dana = new CurrentUserPrincipal(
            UUID.fromString("7a5ce000-0000-4000-8000-000000000018"), "dana@example.com", "Dana", "USER");

    @AfterEach
    void resetSpy() {
        Mockito.reset(workoutSessionRepository);
    }

    @Test
    void aStartThatLosesTheRaceGetsTheActiveSessionConflict() {
        doThrow(new DataIntegrityViolationException("could not execute statement", new ConstraintViolationException(
                "could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"" + INDEX + "\"", "23505"),
                INDEX)))
                .when(workoutSessionRepository).saveAndFlush(any());

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> workoutService.createSession(dana, inProgressRequest()));

        assertEquals(HttpStatus.CONFLICT, conflict.getStatus());
        assertEquals("ACTIVE_SESSION_EXISTS", conflict.getCode());
        assertEquals("You have a workout in progress.", conflict.getMessage());
    }

    @Test
    void anotherIntegrityFailureIsNotTakenForTheRace() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("value too long");
        doThrow(other).when(workoutSessionRepository).saveAndFlush(any());

        assertSame(other, assertThrows(DataIntegrityViolationException.class,
                () -> workoutService.createSession(dana, inProgressRequest())));
    }

    @Test
    void theIndexIsRecognisedByConstraintNameOrDriverMessage() {
        assertTrue(WorkoutServiceImpl.isOneInProgressViolation(new DataIntegrityViolationException("x",
                new ConstraintViolationException("x", new SQLException("x"), INDEX))));
        assertTrue(WorkoutServiceImpl.isOneInProgressViolation(new DataIntegrityViolationException("x",
                new SQLException("duplicate key value violates unique constraint \"" + INDEX + "\""))));
        assertFalse(WorkoutServiceImpl.isOneInProgressViolation(new DataIntegrityViolationException("x",
                new ConstraintViolationException("x", new SQLException("x"), "uk_workout_sessions_external"))));
    }

    private static UpsertWorkoutSessionRequest inProgressRequest() {
        return new UpsertWorkoutSessionRequest(
                null, "Raced", WorkoutType.STRENGTH, WorkoutSessionStatus.IN_PROGRESS, WorkoutSource.MANUAL,
                Instant.now().minusSeconds(60), null, null, null, null, null, List.of());
    }
}
