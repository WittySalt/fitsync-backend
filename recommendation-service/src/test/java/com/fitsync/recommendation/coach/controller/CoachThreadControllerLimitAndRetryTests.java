package com.fitsync.recommendation.coach.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.dto.CoachMessageResponse;
import com.fitsync.recommendation.coach.dto.CoachThreadView;
import com.fitsync.recommendation.coach.service.CoachConversationService;
import com.fitsync.recommendation.coach.service.CoachLimitException;
import com.fitsync.recommendation.coach.service.CoachThreadService;
import com.fitsync.recommendation.security.CurrentUserPrincipal;
import com.fitsync.recommendation.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The 429 body, the retry id and the paged history list, as a client sees them over HTTP. */
@WebMvcTest(controllers = CoachThreadController.class)
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class CoachThreadControllerLimitAndRetryTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID THREAD_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TURN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CLIENT_TURN = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String BODY = "{\"message\":\"What changed this week?\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CoachThreadService threadService;

    @MockBean
    private CoachConversationService conversationService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("a full limit answers 429 with code, retryAfterSeconds and a sentence, on the plain endpoint")
    void limitOnThePlainEndpoint() throws Exception {
        when(conversationService.send(eq(USER_ID), eq(THREAD_ID), any(String.class), any(String.class),
                isNull(), isNull()))
                .thenThrow(new CoachLimitException(1500, "You have asked a lot this hour. You can ask again at 14:05."));

        mockMvc.perform(post("/api/v1/coach/threads/{id}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1500"))
                .andExpect(jsonPath("$.code").value("COACH_LIMIT"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(1500))
                .andExpect(jsonPath("$.message").value("You have asked a lot this hour. You can ask again at 14:05."));
    }

    @Test
    @DisplayName("the stream endpoint refuses a full limit the same way, before any stream opens")
    void limitOnTheStreamEndpoint() throws Exception {
        when(conversationService.stream(eq(USER_ID), eq(THREAD_ID), any(String.class), any(String.class),
                isNull(), isNull()))
                .thenThrow(new CoachLimitException(60, "You have asked a lot this hour. You can ask again at 09:30."));

        mockMvc.perform(post("/api/v1/coach/threads/{id}/messages/stream", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COACH_LIMIT"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));
    }

    @Test
    @DisplayName("the Idempotency-Key header and the clientTurnId body field both reach the service, the header first")
    void theRetryIdReachesTheService() throws Exception {
        when(conversationService.send(eq(USER_ID), eq(THREAD_ID), any(String.class), any(String.class),
                isNull(), eq(CLIENT_TURN)))
                .thenReturn(new CoachMessageResponse(THREAD_ID, TURN_ID, "Same answer.", 2, List.of(), 10, null));

        mockMvc.perform(post("/api/v1/coach/threads/{id}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .header("Idempotency-Key", CLIENT_TURN.toString())
                        .with(authentication(authToken())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/coach/threads/{id}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What changed this week?\",\"clientTurnId\":\"" + CLIENT_TURN + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/coach/threads/{id}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .header("Idempotency-Key", CLIENT_TURN.toString())
                        .with(authentication(authToken())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What changed this week?\",\"clientTurnId\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());

        verify(conversationService, org.mockito.Mockito.times(3)).send(eq(USER_ID), eq(THREAD_ID),
                any(String.class), any(String.class), isNull(), eq(CLIENT_TURN));
    }

    @Test
    @DisplayName("a key that is not a UUID is a bad request, in the header and in the body")
    void aBadKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/coach/threads/{id}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "not-a-uuid")
                        .with(authentication(authToken())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/coach/threads/{id}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hi\",\"clientTurnId\":\"1; drop table\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a retry that finds its first attempt still running answers 202 with the turn id")
    void aRunningTurnAnswersAccepted() throws Exception {
        when(conversationService.send(eq(USER_ID), eq(THREAD_ID), any(String.class), any(String.class),
                isNull(), eq(CLIENT_TURN)))
                .thenReturn(new CoachMessageResponse(THREAD_ID, TURN_ID, "", 0, List.of(), 0, null,
                        CoachMessageResponse.RUNNING));

        mockMvc.perform(post("/api/v1/coach/threads/{id}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .header("Idempotency-Key", CLIENT_TURN.toString())
                        .with(authentication(authToken())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.turnId").value(TURN_ID.toString()));
    }

    @Test
    @DisplayName("the history list is paged, capped at 50 by the service, and reports the total in a header")
    void theHistoryListIsPaged() throws Exception {
        CoachThreadView item = new CoachThreadView(THREAD_ID, "Should I train today?", 4,
                Instant.parse("2026-10-03T08:00:00Z"), Instant.parse("2026-10-03T07:00:00Z"), "WORKOUT",
                Instant.parse("2026-10-03T08:00:00Z"));
        when(threadService.listThreads(USER_ID, 1, 5))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/v1/coach/threads?page=1&size=5")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "6"))
                .andExpect(jsonPath("$[0].threadId").value(THREAD_ID.toString()))
                .andExpect(jsonPath("$[0].title").value("Should I train today?"))
                .andExpect(jsonPath("$[0].surface").value("WORKOUT"))
                .andExpect(jsonPath("$[0].lastMessageAt").value("2026-10-03T08:00:00Z"))
                .andExpect(jsonPath("$[0].turnCount").value(4));
    }

    private static UsernamePasswordAuthenticationToken authToken() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                USER_ID, "user@example.com", "User", "USER");
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_USER"));
    }
}
