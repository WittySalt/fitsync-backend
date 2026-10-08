package com.fitsync.workout.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpdateWorkoutPlanRequest;
import com.fitsync.workout.dto.WorkoutPlanDayInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.messaging.entity.OutboxEventEntity;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * WorkoutPlanScheduleChanged follows the active plan: activation, an edit of the active plan,
 * archiving it and switching to another plan each publish the week now active, keyed by the user
 * id, while edits to any other plan publish nothing. The startup catch-up publishes once per
 * schedule, however many times the service restarts.
 */
@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images"
})
@ActiveProfiles("test")
class WorkoutPlanScheduleEventTests {

    private static final String EVENT_TYPE = "WorkoutPlanScheduleChanged";

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private WorkoutPlanScheduleEventService workoutPlanScheduleEventService;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final CurrentUserPrincipal user = new CurrentUserPrincipal(
            UUID.fromString("44444444-4444-4444-4444-444444444444"), "dana@example.com", "Dana", "USER");

    private UUID squat;
    private UUID row;

    @BeforeEach
    void clearData() {
        outboxEventRepository.deleteAll();
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
        squat = exercise("schedule-squat", "Schedule Squat");
        row = exercise("schedule-row", "Schedule Row");
    }

    @Test
    void activatingAPlanPublishesItsWholeWeek() throws Exception {
        WorkoutPlanView plan = workoutService.createPlan(user, createWeek("Three Day Split"));
        assertTrue(scheduleEvents().isEmpty());

        workoutService.activatePlan(user, plan.planId());

        List<OutboxEventEntity> events = scheduleEvents();
        assertEquals(1, events.size());
        OutboxEventEntity event = events.get(0);
        assertEquals("workout-events", event.getTopic());
        assertEquals(user.userId().toString(), event.getAggregateId());
        assertEquals(user.userId(), event.getUserId());
        JsonNode payload = payloadOf(event);
        assertEquals(plan.planId().toString(), payload.get("planId").asText());
        assertEquals("Three Day Split", payload.get("planName").asText());
        JsonNode days = payload.get("days");
        assertEquals(2, days.size());
        assertEquals(1, days.get(0).get("dayOfWeek").asInt());
        assertEquals("STRENGTH", days.get(0).get("kind").asText());
        assertEquals("Full Body", days.get(0).get("title").asText());
        assertEquals(45, days.get(0).get("estimatedMinutes").asInt());
        assertEquals(2, days.get(0).get("exerciseCount").asInt());
        assertEquals(4, days.get(1).get("dayOfWeek").asInt());
        assertEquals("CARDIO", days.get(1).get("kind").asText());
        assertEquals(0, days.get(1).get("exerciseCount").asInt());
    }

    @Test
    void editingTheActivePlanPublishesTheNewWeek() throws Exception {
        WorkoutPlanView plan = workoutService.createPlan(user, createWeek("Editable"));
        workoutService.activatePlan(user, plan.planId());
        outboxEventRepository.deleteAll();

        workoutService.updatePlan(user, plan.planId(), new UpdateWorkoutPlanRequest(
                "Editable", null, GoalFocus.STRENGTH, WorkoutType.STRENGTH, WorkoutDifficulty.BEGINNER, 45, List.of(),
                List.of(strengthDay(3, "Moved Day", List.of(move(squat, 1))))));

        List<OutboxEventEntity> events = scheduleEvents();
        assertEquals(1, events.size());
        JsonNode days = payloadOf(events.get(0)).get("days");
        assertEquals(1, days.size());
        assertEquals(3, days.get(0).get("dayOfWeek").asInt());
        assertEquals("Moved Day", days.get(0).get("title").asText());
        assertEquals(1, days.get(0).get("exerciseCount").asInt());
    }

    @Test
    void creatingOrEditingAPlanThatIsNotActivePublishesNothing() {
        WorkoutPlanView active = workoutService.createPlan(user, createWeek("Active"));
        workoutService.activatePlan(user, active.planId());
        WorkoutPlanView draft = workoutService.createPlan(user, createWeek("Draft"));
        outboxEventRepository.deleteAll();

        workoutService.updatePlan(user, draft.planId(), new UpdateWorkoutPlanRequest(
                "Draft renamed", null, GoalFocus.STRENGTH, WorkoutType.STRENGTH, WorkoutDifficulty.BEGINNER, 45, List.of(),
                List.of(strengthDay(2, "Other", List.of(move(row, 1))))));
        workoutService.createPlan(user, createWeek("Another draft"));
        workoutService.archivePlan(user, draft.planId());

        assertTrue(scheduleEvents().isEmpty());
    }

    @Test
    void archivingTheActivePlanPublishesAnEmptyWeek() throws Exception {
        WorkoutPlanView plan = workoutService.createPlan(user, createWeek("To archive"));
        workoutService.activatePlan(user, plan.planId());
        outboxEventRepository.deleteAll();

        workoutService.archivePlan(user, plan.planId());

        List<OutboxEventEntity> events = scheduleEvents();
        assertEquals(1, events.size());
        JsonNode payload = payloadOf(events.get(0));
        assertTrue(payload.get("planId").isNull());
        assertEquals(0, payload.get("days").size());
    }

    @Test
    void switchingToAnotherPlanPublishesItOnceAndReactivatingTheSamePlanPublishesNothing() throws Exception {
        WorkoutPlanView first = workoutService.createPlan(user, createWeek("First"));
        WorkoutPlanView second = workoutService.createPlan(user, new CreateWorkoutPlanRequest(
                "Second", null, GoalFocus.STRENGTH, WorkoutType.STRENGTH, WorkoutDifficulty.BEGINNER, 45, List.of(),
                List.of(strengthDay(5, "Friday", List.of(move(row, 1))))));
        workoutService.activatePlan(user, first.planId());
        outboxEventRepository.deleteAll();

        workoutService.activatePlan(user, second.planId());
        workoutService.activatePlan(user, second.planId());

        List<OutboxEventEntity> events = scheduleEvents();
        assertEquals(1, events.size());
        JsonNode payload = payloadOf(events.get(0));
        assertEquals(second.planId().toString(), payload.get("planId").asText());
        assertEquals(5, payload.get("days").get(0).get("dayOfWeek").asInt());
    }

    @Test
    void theStartupCatchUpPublishesEachActiveScheduleOnlyOnce() {
        WorkoutPlanView plan = workoutService.createPlan(user, createWeek("Caught up"));
        workoutService.activatePlan(user, plan.planId());
        workoutService.createPlan(new CurrentUserPrincipal(
                UUID.fromString("55555555-5555-5555-5555-555555555555"), "eve@example.com", "Eve", "USER"),
                createWeek("Never active"));
        outboxEventRepository.deleteAll();

        assertEquals(1, workoutPlanScheduleEventService.catchUpActiveSchedules());
        assertEquals(0, workoutPlanScheduleEventService.catchUpActiveSchedules());

        List<OutboxEventEntity> events = scheduleEvents();
        assertEquals(1, events.size());
        assertEquals(user.userId(), events.get(0).getUserId());
    }

    private List<OutboxEventEntity> scheduleEvents() {
        return outboxEventRepository.findAll().stream()
                .filter(event -> EVENT_TYPE.equals(event.getEventType()))
                .toList();
    }

    private JsonNode payloadOf(OutboxEventEntity event) throws Exception {
        return objectMapper.readTree(event.getPayload()).get("payload");
    }

    private CreateWorkoutPlanRequest createWeek(String name) {
        return new CreateWorkoutPlanRequest(
                name, null, GoalFocus.STRENGTH, WorkoutType.STRENGTH, WorkoutDifficulty.BEGINNER, 45, List.of(),
                List.of(
                        strengthDay(1, "Full Body", List.of(move(squat, 1), move(row, 2))),
                        new WorkoutPlanDayInput(null, 4, SessionKind.CARDIO, "Easy Run", null, 30, List.of())));
    }

    private static WorkoutPlanDayInput strengthDay(int dayOfWeek, String title, List<WorkoutPlanExerciseInput> moves) {
        return new WorkoutPlanDayInput(null, dayOfWeek, SessionKind.STRENGTH, title, null, 45, moves);
    }

    private static WorkoutPlanExerciseInput move(UUID exerciseId, int sequence) {
        return new WorkoutPlanExerciseInput(exerciseId, sequence, 3, 10, new BigDecimal("20"), null, null, 90, null,
                List.of());
    }

    private UUID exercise(String code, String name) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName(name);
        entity.setExerciseType(ExerciseType.STRENGTH);
        entity.setPrimaryMuscleGroup(MuscleGroup.LEGS);
        entity.setEquipment(ExerciseEquipment.BARBELL);
        entity.setDifficulty(WorkoutDifficulty.BEGINNER);
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(false);
        return exerciseRepository.save(entity).getExerciseId();
    }
}
