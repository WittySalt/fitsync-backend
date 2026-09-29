package com.fitsync.recommendation.coach;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.dto.CoachQualityResponse;
import com.fitsync.recommendation.coach.entity.CoachThreadEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.orchestrator.CoachOutcome;
import com.fitsync.recommendation.coach.repository.CoachThreadRepository;
import com.fitsync.recommendation.coach.repository.CoachTurnRepository;
import com.fitsync.recommendation.coach.service.CoachQualityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The hard requirement for this page: the response can carry counts, rule codes, tool names, model
 * names and durations, and nothing that names a user, a thread, a turn or what was said.
 *
 * <p>Rather than trust the field list, this seeds a thread and a turn carrying an identifiable user
 * id, thread id and message text, builds the real response over them, serialises it exactly as the
 * controller would, and greps the JSON for every one of those values.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachQualityPrivacyTests {

    @Autowired
    private CoachQualityService service;

    @Autowired
    private CoachThreadRepository threadRepository;

    @Autowired
    private CoachTurnRepository turnRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void theSerialisedResponseNamesNoUserThreadOrMessageText() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID threadId = UUID.randomUUID();
        String secretMessage = "My weight this week was a very specific and identifiable 71.4kg on Tuesday";

        CoachThreadEntity thread = new CoachThreadEntity();
        thread.setThreadId(threadId);
        thread.setUserId(userId);
        thread.setArchived(false);
        thread.setLastActiveAt(Instant.now());
        threadRepository.save(thread);

        CoachTurnEntity turn = new CoachTurnEntity();
        turn.setTurnId(UUID.randomUUID());
        turn.setThreadId(threadId);
        turn.setSeq(1);
        turn.setRole(CoachTurnRole.ASSISTANT);
        turn.setContent(secretMessage);
        turn.setTier(2);
        turn.setModel("claude-haiku-4-5-20251001");
        turn.setLatencyMs(1800);
        turn.setGuardrailVerdict(CoachOutcome.VERDICT_PASSED);
        turnRepository.save(turn);

        LocalDate today = LocalDate.now();
        CoachQualityResponse response = service.quality(today.minusDays(6), today, null);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain(userId.toString());
        assertThat(json).doesNotContain(threadId.toString());
        assertThat(json).doesNotContain(turn.getTurnId().toString());
        assertThat(json).doesNotContain(secretMessage);
        assertThat(json).doesNotContain("71.4");
    }
}
