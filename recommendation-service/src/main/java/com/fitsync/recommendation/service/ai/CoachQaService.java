package com.fitsync.recommendation.service.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.config.RecommendationProperties;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * A4 — coach Q&A: one synchronous, single-turn answer grounded ONLY on the user's
 * pre-computed context facts. This is the one deliberate exception to "AI never on the
 * read path": the user explicitly asked and is watching a spinner, so the trade is
 * theirs — bounded by a per-user hourly question budget, a token cap, and a friendly
 * 503 when the tier is off or the model misbehaves. Nothing here is cached or reused;
 * the recommendation funnel stays untouched.
 */
@Service
public class CoachQaService {

    private static final Logger log = LoggerFactory.getLogger(CoachQaService.class);

    static final String SYSTEM_PROMPT = """
            You are FitSync's personal coach answering one quick question from a user.
            You get the user's current pre-computed fitness facts as JSON. Treat every \
            number and status word in them as ground truth - never invent, recompute or \
            reinterpret them - and say so plainly when the facts don't cover the question.
            Answer in 1-3 short sentences, plain text, no greetings, sign-offs or emojis.
            Stay within everyday training, nutrition and logging guidance grounded in the \
            facts. No medical, injury or supplement advice - recommend a professional \
            instead. Never suggest eating below 1200 kcal a day.""";

    private final RecommendationContextRepository contextRepository;
    private final ObjectProvider<ClaudeClient> claudeClientProvider;
    private final CoachFacts coachFacts;
    private final ObjectMapper objectMapper;
    private final RecommendationProperties properties;
    private final Clock clock;
    private final Map<UUID, QuestionWindow> questionWindows = new ConcurrentHashMap<>();

    public CoachQaService(RecommendationContextRepository contextRepository,
                          ObjectProvider<ClaudeClient> claudeClientProvider,
                          CoachFacts coachFacts,
                          ObjectMapper objectMapper,
                          RecommendationProperties properties,
                          Clock clock) {
        this.contextRepository = contextRepository;
        this.claudeClientProvider = claudeClientProvider;
        this.coachFacts = coachFacts;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public String ask(UUID userId, String question) {
        ClaudeClient claudeClient = claudeClientProvider.getIfAvailable();
        if (claudeClient == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "COACH_UNAVAILABLE",
                    "The coach is not enabled on this deployment.");
        }
        enforceQuestionBudget(userId);
        Map<String, Object> facts = contextRepository.findById(userId)
                .map(context -> coachFacts.assemble(context, null))
                .orElseGet(Map::of);
        try {
            String answer = claudeClient.complete(SYSTEM_PROMPT, promptFor(facts, question));
            if (answer.isBlank()) {
                throw new IllegalStateException("blank model answer");
            }
            return answer;
        } catch (RuntimeException ex) {
            // Values are sanitized inline (CRLF, CWE-117) — findsecbugs only accepts
            // replace() directly in the dataflow to the sink.
            log.warn("event=coach_answer_failed userId={} reason={}",
                    String.valueOf(userId).replace("\r", "_").replace("\n", "_"),
                    String.valueOf(ex.getMessage()).replace("\r", "_").replace("\n", "_"));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "COACH_UNAVAILABLE",
                    "The coach is temporarily unavailable - please try again in a moment.");
        }
    }

    /** Fixed one-hour window per user; the cap is configuration, not a model concern. */
    private void enforceQuestionBudget(UUID userId) {
        int questionsPerHour = properties.ai().coachQuestionsPerHour();
        Instant now = Instant.now(clock);
        QuestionWindow window = questionWindows.compute(userId, (id, current) ->
                current == null || Duration.between(current.start(), now).compareTo(Duration.ofHours(1)) >= 0
                        ? new QuestionWindow(now, 1)
                        : new QuestionWindow(current.start(), current.count() + 1));
        if (window.count() > questionsPerHour) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "COACH_RATE_LIMITED",
                    "Question limit reached - the coach answers up to %d questions per hour."
                            .formatted(questionsPerHour));
        }
    }

    private String promptFor(Map<String, Object> facts, String question) {
        try {
            return "The user's current facts as JSON:\n" + objectMapper.writeValueAsString(facts)
                    + "\n\nQuestion: " + question;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize coach facts", ex);
        }
    }

    private record QuestionWindow(Instant start, int count) {
    }
}
