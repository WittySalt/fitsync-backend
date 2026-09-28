package com.fitsync.recommendation.coach.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.dto.CoachMessageContext;
import com.fitsync.recommendation.coach.dto.CoachMessageResponse;
import com.fitsync.recommendation.coach.service.CoachConversationService;
import com.fitsync.recommendation.coach.service.CoachThreadService;
import com.fitsync.recommendation.security.CurrentUserPrincipal;
import com.fitsync.recommendation.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Request-body validation for {@code SendCoachMessageRequest}: the message itself, unchanged, plus
 * the optional {@link CoachMessageContext} it now carries.
 *
 * <p>Only the shape of the request is under test here. What the service does with a valid context
 * is proven in {@code CoachConversationServiceTests} and {@code CoachOrchestratorTests}.
 */
@WebMvcTest(controllers = CoachThreadController.class)
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class CoachThreadControllerRequestValidationTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID THREAD_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TURN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CoachThreadService threadService;

    @MockBean
    private CoachConversationService conversationService;

    @MockBean
    private JwtService jwtService;

    @Test
    void aBodyWithNoContextStillWorks() throws Exception {
        when(conversationService.send(eq(USER_ID), eq(THREAD_ID), any(String.class), eq("Should I train today?"),
                isNull()))
                .thenReturn(new CoachMessageResponse(THREAD_ID, TURN_ID, "Rest today.", 2, List.of(), 10, null));

        mockMvc.perform(post("/api/v1/coach/threads/{threadId}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Should I train today?"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void anUnknownSurfaceIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/coach/threads/{threadId}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Tell me about this.","context":{"surface":"KITCHEN"}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aReadingIdOfTheWrongShapeIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/coach/threads/{threadId}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Tell me about this.","context":{"surface":"WORKOUT","readingId":"not-a-hash"}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTimezoneContainingASpaceIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/coach/threads/{threadId}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Tell me about this.","context":{"surface":"WORKOUT","timezone":"Asia/Singa pore"}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aFullyValidContextIsAccepted() throws Exception {
        when(conversationService.send(eq(USER_ID), eq(THREAD_ID), any(String.class), eq("Tell me about this."),
                any(CoachMessageContext.class)))
                .thenReturn(new CoachMessageResponse(THREAD_ID, TURN_ID, "You moved more weight.", 2, List.of(),
                        10, "9f2c4e1ab03d77aa"));

        mockMvc.perform(post("/api/v1/coach/threads/{threadId}/messages", THREAD_ID)
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Tell me about this.",\
                                "context":{"surface":"WORKOUT","readingId":"9f2c4e1ab03d77aa","timezone":"Asia/Singapore"}}
                                """))
                .andExpect(status().isOk());
    }

    private static UsernamePasswordAuthenticationToken authToken() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                USER_ID, "user@example.com", "User", "USER");
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_USER"));
    }
}
