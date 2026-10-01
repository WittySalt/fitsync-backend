package com.fitsync.recommendation.coach.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.draft.CandidateSelector;
import com.fitsync.recommendation.coach.draft.DraftContext;
import com.fitsync.recommendation.coach.draft.DraftContext.CatalogueExercise;
import com.fitsync.recommendation.coach.draft.DraftContextAssembler;
import com.fitsync.recommendation.coach.draft.DraftViolation;
import com.fitsync.recommendation.coach.draft.SessionDraft;
import com.fitsync.recommendation.coach.draft.SessionDraftGenerator;
import com.fitsync.recommendation.coach.draft.TemplateSessionBuilder;
import com.fitsync.recommendation.coach.draft.WorkoutPlanMapper;
import com.fitsync.recommendation.coach.client.CatalogueClient.DownstreamWriteException;
import com.fitsync.recommendation.coach.dto.AcceptSessionDraftResponse;
import java.time.Clock;
import java.time.Instant;
import com.fitsync.recommendation.coach.dto.SessionDraftView;
import com.fitsync.recommendation.coach.entity.ArtifactKind;
import com.fitsync.recommendation.coach.entity.ArtifactStatus;
import com.fitsync.recommendation.coach.entity.DraftSource;
import com.fitsync.recommendation.coach.entity.GeneratedArtifactEntity;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.coach.repository.GeneratedArtifactRepository;
import com.fitsync.web.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces a session draft and records how it was produced.
 *
 * <p>The order is fixed and the fallbacks are unconditional. Candidates are gathered from the
 * catalogue, the model is asked, a failed draft gets one repair, and anything still failing goes to
 * the rule engine. A user asking for a session gets one, or a clear reason there is none; what they
 * never get is a plausible session that references an exercise that does not exist.
 *
 * <p>Everything is written down: which path produced the stored draft, how many validations it took
 * and what the first one found. Those three columns are the evidence behind the generation-quality
 * numbers, and none of them can be reconstructed after the fact.
 */
@Service
public class SessionDraftService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionDraftService.class);

    /** Wide enough that the model has real choice, narrow enough not to crowd out the prompt. */
    private static final int CANDIDATES_PER_GROUP = 8;

    /** The code workout-service answers with when the person already has a workout in progress. */
    private static final String ACTIVE_SESSION_EXISTS = "ACTIVE_SESSION_EXISTS";
    private static final String WORKOUT_IN_PROGRESS_MESSAGE =
            "You have a workout in progress. Finish or end it first.";

    /**
     * The groups a session is drawn from when the user did not name one.
     *
     * <p>Not the whole catalogue: a session is three to seven exercises, and offering a model
     * several hundred rows makes its choice worse rather than better.
     */
    private static final List<String> DEFAULT_GROUPS =
            List.of("CHEST", "BACK", "LEGS", "SHOULDERS", "ARMS", "CORE");

    private final WorkoutCatalogueClient workoutClient;
    private final CandidateSelector candidateSelector;
    private final DraftContextAssembler contextAssembler;
    private final SessionDraftGenerator generator;
    private final TemplateSessionBuilder templateBuilder;
    private final GeneratedArtifactRepository artifactRepository;
    private final WorkoutPlanMapper planMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SessionDraftService(WorkoutCatalogueClient workoutClient,
                               CandidateSelector candidateSelector,
                               DraftContextAssembler contextAssembler,
                               SessionDraftGenerator generator,
                               TemplateSessionBuilder templateBuilder,
                               GeneratedArtifactRepository artifactRepository,
                               WorkoutPlanMapper planMapper,
                               ObjectMapper objectMapper,
                               Clock clock) {
        this.workoutClient = workoutClient;
        this.candidateSelector = candidateSelector;
        this.contextAssembler = contextAssembler;
        this.generator = generator;
        this.templateBuilder = templateBuilder;
        this.artifactRepository = artifactRepository;
        this.planMapper = planMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param request  what the user asked for in their own words, or null
     * @param threadId the conversation this came from, or null when drafted from a studio surface
     */
    @Transactional
    public SessionDraftView draft(CoachCallContext call, String request, UUID threadId) {
        List<CatalogueExercise> candidates = candidates(call, request);
        if (candidates.isEmpty()) {
            throw new DraftUnavailableException(
                    "The exercise catalogue could not be read, so there is nothing to build a session from.");
        }

        // The context keeps the whole pool, so an exercise the model remembers from an earlier turn
        // still resolves and earns a precise violation rather than "no such exercise".
        DraftContext context = contextAssembler.assemble(call, candidates);

        // What the model is offered is narrower: only exercises that break no rule on their own.
        // Telling a model in prose to avoid a muscle group while handing it a list that is mostly
        // that muscle group does not work, which a live run demonstrated repeatedly.
        List<CatalogueExercise> eligible = candidateSelector.eligible(candidates, context);
        if (eligible.isEmpty()) {
            throw new DraftUnavailableException(whyNothingCouldBeBuilt(candidates, context));
        }

        SessionDraftGenerator.Attempt attempt = generator.generate(request, eligible, context);

        if (attempt.succeeded()) {
            DraftSource source = attempt.repairUsed() ? DraftSource.MODEL_REPAIRED : DraftSource.MODEL;
            return persist(call, threadId, attempt.draft(), source,
                    attempt.repairUsed() ? 2 : 1, attempt.firstPassViolations());
        }

        // Two failed attempts, or no model at all. Either way the user still gets a session.
        return templateBuilder.build(eligible, context)
                .map(draft -> persist(call, threadId, draft, DraftSource.RULE_FALLBACK,
                        attempt.modelAvailable() ? 2 : 0, attempt.firstPassViolations()))
                .orElseThrow(() -> new DraftUnavailableException(whyNothingCouldBeBuilt(candidates, context)));
    }

    /**
     * Turns a draft into a real plan and, normally, a running session.
     *
     * <p>Two writes to workout-service, in order, and the second is allowed to fail on its own. If
     * the plan is created but cannot be started, the user has still gained a plan they can open and
     * start by hand, so the artifact records the plan and the response says the session did not
     * start. Undoing the plan to keep the two in step would take something real away from the user
     * to make a status field tidier.
     *
     * <p>The one failure that does undo the plan is a workout already in progress, because the
     * person can only run one at a time. That is checked before the plan is created, and again if
     * workout-service refuses the start; either way the draft stays open and the person is told to
     * finish or end the workout first.
     *
     * <p>Accepting an already accepted draft returns what it became rather than creating a second
     * plan. The status check and the update happen in one transaction, and the entity's version
     * column turns two simultaneous accepts into one success and one retryable failure.
     */
    @Transactional
    public AcceptSessionDraftResponse accept(CoachCallContext call, UUID artifactId) {
        GeneratedArtifactEntity artifact = artifactRepository
                .findByArtifactIdAndUserId(artifactId, call.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Draft not found"));

        if (artifact.getStatus() == ArtifactStatus.ACCEPTED) {
            return new AcceptSessionDraftResponse(
                    artifactId, ArtifactStatus.ACCEPTED, artifact.getAcceptedRef(),
                    artifact.getStartedSessionRef(), artifact.getStartedSessionRef() != null);
        }
        if (artifact.getStatus() == ArtifactStatus.DISCARDED) {
            throw new DraftNotAcceptableException("This draft was rejected and cannot be accepted.");
        }

        // Asked first so that, in the usual case, nothing is created for a session that cannot start.
        if (workoutClient.activeSession(call.bearerToken()) != null) {
            throw new DraftNotAcceptableException(WORKOUT_IN_PROGRESS_MESSAGE);
        }

        SessionDraft draft = readDraft(artifact);
        UUID planId = createPlan(call, draft);
        UUID sessionId = startPlan(call, planId);

        artifact.setStatus(ArtifactStatus.ACCEPTED);
        artifact.setAcceptedRef(planId);
        artifact.setAcceptedAt(Instant.now(clock));
        artifact.setStartedSessionRef(sessionId);
        artifactRepository.save(artifact);

        return new AcceptSessionDraftResponse(
                artifactId, ArtifactStatus.ACCEPTED, planId, sessionId, sessionId != null);
    }

    /**
     * Records that the user did not want this one, and why if they said.
     *
     * <p>Not a deletion. What a user turned down is the most direct signal the harness has, and it
     * is kept next to how the draft was produced so the two can be read together.
     */
    @Transactional
    public SessionDraftView reject(UUID userId, UUID artifactId, String reason) {
        GeneratedArtifactEntity artifact = artifactRepository
                .findByArtifactIdAndUserId(artifactId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft not found"));

        if (artifact.getStatus() == ArtifactStatus.ACCEPTED) {
            throw new DraftNotAcceptableException(
                    "This draft has already been accepted; discard the plan it became instead.");
        }
        artifact.setStatus(ArtifactStatus.DISCARDED);
        artifact.setRejectionReason(reason == null || reason.isBlank() ? null : reason.strip());
        artifactRepository.save(artifact);
        return toView(artifact);
    }

    private UUID createPlan(CoachCallContext call, SessionDraft draft) {
        String body;
        try {
            body = workoutClient.createPlan(call.bearerToken(), planMapper.toCreatePlanRequest(draft));
        } catch (DownstreamWriteException ex) {
            throw new DraftNotAcceptableException(
                    ex.status() == 0
                            ? "The workout service could not be reached, so the plan was not created. Try again shortly."
                            : "The workout service did not accept this session as a plan (status " + ex.status() + ").");
        }
        UUID planId = readId(body, "planId");
        if (planId == null) {
            throw new DraftNotAcceptableException("The workout service created a plan but did not say which.");
        }
        return planId;
    }

    /** @return the started session, or null when the plan was saved but could not be started */
    private UUID startPlan(CoachCallContext call, UUID planId) {
        try {
            return readId(workoutClient.startPlan(call.bearerToken(), planId), "sessionId");
        } catch (DownstreamWriteException ex) {
            if (isActiveSessionConflict(ex)) {
                // Another workout began between the check and the start. The plan was made for this
                // accept alone, so it is taken back rather than left behind, and the draft stays open.
                archiveQuietly(call, planId);
                throw new DraftNotAcceptableException(WORKOUT_IN_PROGRESS_MESSAGE);
            }
            LOG.warn("Plan {} was created but its session could not be started (status {})",
                    planId, ex.status());
            return null;
        }
    }

    private boolean isActiveSessionConflict(DownstreamWriteException ex) {
        if (ex.status() != 409 || ex.responseBody() == null) {
            return false;
        }
        try {
            return ACTIVE_SESSION_EXISTS.equals(objectMapper.readTree(ex.responseBody()).path("code").asText(null));
        } catch (JsonProcessingException parseFailure) {
            return false;
        }
    }

    private void archiveQuietly(CoachCallContext call, UUID planId) {
        try {
            workoutClient.archivePlan(call.bearerToken(), planId);
        } catch (DownstreamWriteException ex) {
            LOG.warn("Plan {} was created for a session that could not start and could not be archived (status {})",
                    planId, ex.status());
        }
    }

    private UUID readId(String body, String field) {
        if (body == null) {
            return null;
        }
        try {
            String raw = objectMapper.readTree(body).path(field).asText(null);
            return raw == null ? null : UUID.fromString(raw);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            LOG.warn("The workout service response did not carry a readable {}", field, ex);
            return null;
        }
    }

    private SessionDraft readDraft(GeneratedArtifactEntity artifact) {
        try {
            return objectMapper.readValue(artifact.getPayload(), SessionDraft.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("A stored draft could not be read back", ex);
        }
    }

    @Transactional(readOnly = true)
    public SessionDraftView get(UUID userId, UUID artifactId) {
        GeneratedArtifactEntity artifact = artifactRepository
                .findByArtifactIdAndUserId(artifactId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft not found"));
        return toView(artifact);
    }

    @Transactional(readOnly = true)
    public List<SessionDraftView> list(UUID userId) {
        return artifactRepository
                .findByUserIdAndKindOrderByDateTimeCreatedDesc(userId, ArtifactKind.SESSION_DRAFT)
                .stream()
                .map(this::toView)
                .toList();
    }

    /**
     * The three numbers that say whether the model is earning its place.
     *
     * @param userId scope to one user, or null for everyone, which is what the harness asks for
     */
    @Transactional(readOnly = true)
    public DraftGenerationMetrics metrics(UUID userId) {
        long model = 0;
        long repaired = 0;
        long fallback = 0;
        for (Object[] row : artifactRepository.generationCounts(ArtifactKind.SESSION_DRAFT, userId)) {
            DraftSource source = (DraftSource) row[0];
            long count = ((Number) row[1]).longValue();
            switch (source) {
                case MODEL -> model = count;
                case MODEL_REPAIRED -> repaired = count;
                case RULE_FALLBACK -> fallback = count;
                default -> LOG.warn("Unrecognised draft source in the artifact table");
            }
        }
        return new DraftGenerationMetrics(model, repaired, fallback);
    }

    /**
     * Exercises to choose from.
     *
     * <p>A named muscle group gets more depth, but the spread across the other groups is always
     * included as well. That is not hedging: a user asking for chest and back on the day after a
     * full-body session would otherwise have every candidate removed by the recovery rule, and be
     * told no session could be built when the true answer is that they should train something else
     * today. Offering alternatives lets the coach say that instead of refusing.
     */
    private List<CatalogueExercise> candidates(CoachCallContext call, String request) {
        List<String> requested = requestedGroups(request);
        Map<UUID, CatalogueExercise> candidates = new LinkedHashMap<>();
        for (String group : requested) {
            gather(call, group, CANDIDATES_PER_GROUP * 2, candidates);
        }
        for (String group : DEFAULT_GROUPS) {
            gather(call, group, CANDIDATES_PER_GROUP, candidates);
        }
        return List.copyOf(candidates.values());
    }

    private void gather(CoachCallContext call, String group, int size, Map<UUID, CatalogueExercise> into) {
        String body = workoutClient.searchExercises(call.bearerToken(), group, null, null, null, size);
        if (body == null) {
            return;
        }
        for (CatalogueExercise exercise : readPage(body)) {
            into.putIfAbsent(exercise.exerciseId(), exercise);
        }
    }

    /**
     * Why the fallback came back empty, in terms the user can act on.
     *
     * <p>The first version of this message blamed the equipment list for every failure, and the
     * first live run hit the recovery rule instead: a full-body session the day before had removed
     * every candidate. Being told to buy equipment when the real answer is to rest a day is worse
     * than saying nothing.
     */
    private static String whyNothingCouldBeBuilt(List<CatalogueExercise> candidates, DraftContext context) {
        boolean everythingRecentlyTrained = !context.recentMuscleGroups().isEmpty()
                && candidates.stream().allMatch(candidate ->
                        context.recentMuscleGroups().contains(
                                candidate.primaryMuscleGroup().toUpperCase(Locale.ROOT)));
        if (everythingRecentlyTrained) {
            return "Everything available trains a muscle group worked yesterday, so there is nothing "
                    + "to propose today that would not cut recovery short.";
        }
        return "No session could be built from the exercises available to this user. Adding "
                + "equipment to their profile, or more exercises to the catalogue, would fix it.";
    }

    private static List<String> requestedGroups(String request) {
        if (request == null || request.isBlank()) {
            return DEFAULT_GROUPS;
        }
        String lower = request.toLowerCase(Locale.ROOT);
        List<String> named = new ArrayList<>();
        for (String group : DEFAULT_GROUPS) {
            if (lower.contains(group.toLowerCase(Locale.ROOT))) {
                named.add(group);
            }
        }
        // "Chest and back" narrows the pool; anything else falls back to the spread, because a
        // request the keyword match does not understand is not a request for nothing.
        return named.isEmpty() ? DEFAULT_GROUPS : named;
    }

    private List<CatalogueExercise> readPage(String body) {
        List<CatalogueExercise> exercises = new ArrayList<>();
        try {
            for (JsonNode item : objectMapper.readTree(body).path("content")) {
                String id = item.path("exerciseId").asText(null);
                if (id == null) {
                    continue;
                }
                exercises.add(new CatalogueExercise(
                        UUID.fromString(id),
                        item.path("name").asText(""),
                        item.path("primaryMuscleGroup").asText(""),
                        List.of(),
                        item.path("equipment").asText(""),
                        // The search endpoint only returns exercises the user can use, so anything
                        // on this page is active by construction.
                        true));
            }
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            LOG.warn("A catalogue page could not be read while gathering draft candidates", ex);
        }
        return exercises;
    }

    private SessionDraftView persist(CoachCallContext call,
                                     UUID threadId,
                                     SessionDraft draft,
                                     DraftSource source,
                                     int attempts,
                                     List<DraftViolation> firstPassViolations) {
        GeneratedArtifactEntity artifact = new GeneratedArtifactEntity();
        artifact.setArtifactId(UUID.randomUUID());
        artifact.setUserId(call.userId());
        artifact.setThreadId(threadId);
        artifact.setTurnId(call.turnId());
        artifact.setKind(ArtifactKind.SESSION_DRAFT);
        artifact.setStatus(ArtifactStatus.DRAFT);
        artifact.setSource(source);
        artifact.setValidationAttempts(attempts);
        artifact.setPayload(write(draft));
        artifact.setFirstPassViolations(firstPassViolations.isEmpty() ? null : write(firstPassViolations));
        artifactRepository.save(artifact);

        return new SessionDraftView(
                artifact.getArtifactId(), ArtifactStatus.DRAFT, source, attempts, draft, null, null, null);
    }

    private SessionDraftView toView(GeneratedArtifactEntity artifact) {
        return new SessionDraftView(
                artifact.getArtifactId(),
                artifact.getStatus(),
                artifact.getSource(),
                artifact.getValidationAttempts(),
                readDraft(artifact),
                artifact.getAcceptedRef(),
                artifact.getStartedSessionRef(),
                artifact.getRejectionReason());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("A draft could not be serialised for storage", ex);
        }
    }

    /**
     * The generation-quality numbers.
     *
     * <p>Ratios rather than raw counts are what get quoted, but the counts are what is stored, so
     * they are returned alongside: a rate over four drafts means something different from the same
     * rate over four hundred, and hiding the denominator hides that.
     */
    public record DraftGenerationMetrics(long model, long modelRepaired, long ruleFallback) {

        public long total() {
            return model + modelRepaired + ruleFallback;
        }

        /** Drafts the model produced at all, whether or not a repair was needed. */
        public double modelSuccessRate() {
            return total() == 0 ? 0 : (double) (model + modelRepaired) / total();
        }

        /** Drafts that passed validation on the model's first attempt. */
        public double firstPassRate() {
            return total() == 0 ? 0 : (double) model / total();
        }

        /** Model drafts that needed the repair round. */
        public double repairRate() {
            long modelDrafts = model + modelRepaired;
            return modelDrafts == 0 ? 0 : (double) modelRepaired / modelDrafts;
        }
    }

    /**
     * Thrown when neither the model nor the rules can produce a session.
     *
     * <p>503 rather than 500, because nothing is broken. Either the catalogue was unreachable or
     * this user's equipment and recovery constraints leave too little to build from, and both are
     * states that resolve. It extends the project's own ApiException rather than carrying a
     * ResponseStatus annotation, because the shared handler maps exceptions by type and an
     * annotation it never reads would have quietly returned 500.
     */
    public static class DraftUnavailableException extends com.fitsync.web.error.ApiException {
        private static final long serialVersionUID = 1L;

        public DraftUnavailableException(String message) {
            super(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "DRAFT_UNAVAILABLE", message);
        }
    }

    /**
     * Thrown when a draft is in a state that does not allow the requested transition, or when the
     * service that would own the result declined it. 409, because the draft exists and the caller
     * is allowed to see it; it is the requested change that conflicts with where things stand.
     */
    public static class DraftNotAcceptableException extends com.fitsync.web.error.ApiException {
        private static final long serialVersionUID = 1L;

        public DraftNotAcceptableException(String message) {
            super(org.springframework.http.HttpStatus.CONFLICT, "DRAFT_NOT_ACCEPTABLE", message);
        }
    }
}
