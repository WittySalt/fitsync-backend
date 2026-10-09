package com.fitsync.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.auth.entity.AppUser;
import com.fitsync.auth.entity.AuthSession;
import com.fitsync.auth.entity.SessionRevocationReason;
import com.fitsync.auth.repository.AppUserRepository;
import com.fitsync.auth.repository.AuthSessionRepository;
import com.fitsync.auth.security.RefreshTokens;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Multi-device sessions: what sign-in returns, refresh rotation and reuse detection, sign-out, the session list
 * and revocation, and the Redis deny-list entries written for the gateway. Redis is a mock here.
 */
@SpringBootTest(properties = "fitsync.auth.local-accounts-enabled=true")
@AutoConfigureMockMvc
class AuthSessionApiTests {

    /** A signing key that is not the service's, made at run time so no key-like literal sits in the source. */
    private static final String OTHER_KEY = "x".repeat(48);
    /** {@code security.jwt.expiration} in the test configuration (PT1H) plus the minute of clock skew. */
    private static final Duration DENY_LIST_TTL = Duration.ofHours(1).plusSeconds(60);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthSessionRepository sessionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Value("${security.jwt.secret}")
    private String signingSecret;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubRedis() {
        valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        reset(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ── sign-in ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void bootstrapCreatesASessionAndReturnsTheNewFields() throws Exception {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JsonNode body = json(mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(cognitoJwt("sess-boot-1", "sess-boot-1@example.com"))
                        .header("X-Device-Label", "Ruixian's laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.email").value("sess-boot-1@example.com"))
                .andExpect(jsonPath("$.displayName").value("Test Person"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty()));

        String refreshToken = body.get("refreshToken").asText();
        UUID sessionId = UUID.fromString(body.get("sessionId").asText());
        Instant expiresAt = Instant.parse(body.get("accessTokenExpiresAt").asText());

        // 256 random bits, base64url without padding.
        assertThat(refreshToken).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(refreshToken)).hasSize(32);
        assertThat(expiresAt).isBetween(before.plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));

        JsonNode claims = claimsOf(body.get("accessToken").asText());
        assertThat(claims.get("sid").asText()).isEqualTo(sessionId.toString());
        assertThat(claims.get("exp").asLong()).isEqualTo(expiresAt.getEpochSecond());

        AuthSession session = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getUserId()).isEqualTo(UUID.fromString(body.get("userId").asText()));
        assertThat(session.getDeviceLabel()).isEqualTo("Ruixian's laptop");
        assertThat(session.getRefreshTokenHash()).isEqualTo(RefreshTokens.hash(refreshToken))
                .isNotEqualTo(refreshToken);
        assertThat(session.getPreviousRefreshTokenHash()).isNull();
        assertThat(session.getRevokedAt()).isNull();
        assertThat(session.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(29)));
        assertThat(session.getCreatedAt().getNano() % 1_000).isZero();
    }

    @Test
    void theDeviceLabelComesFromTheUserAgentWhenNoHeaderIsSent() throws Exception {
        JsonNode body = json(mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(cognitoJwt("sess-ua-1", "sess-ua-1@example.com"))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"))
                .andExpect(status().isOk()));

        assertThat(sessionRepository.findById(sessionIdOf(body)).orElseThrow().getDeviceLabel())
                .isEqualTo("Chrome on Windows");
    }

    @Test
    void aDeviceLabelHeaderIsSanitisedAndCut() throws Exception {
        JsonNode body = json(mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(cognitoJwt("sess-label-1", "sess-label-1@example.com"))
                        .header("X-Device-Label", "  <b>Phone</b>\tof‮  " + "x".repeat(100)))
                .andExpect(status().isOk()));

        String label = sessionRepository.findById(sessionIdOf(body)).orElseThrow().getDeviceLabel();
        assertThat(label).startsWith("bPhone/b of x").hasSize(60).doesNotContain("<", ">", "\t", "‮");
    }

    @Test
    void localSignInOpensASessionToo() throws Exception {
        register("sess-local@example.com");
        JsonNode body = json(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sess-local@example.com\",\"password\":\"supersecret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty()));

        UUID sessionId = sessionIdOf(body);
        assertThat(claimsOf(body.get("accessToken").asText()).get("sid").asText()).isEqualTo(sessionId.toString());
        assertThat(sessionRepository.findById(sessionId)).isPresent();
    }

    // ── refresh ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void refreshRotatesTheTokenAndKeepsTheSession() throws Exception {
        JsonNode signIn = bootstrap("sess-rot-1");
        UUID sessionId = sessionIdOf(signIn);
        Instant firstSeen = sessionRepository.findById(sessionId).orElseThrow().getLastSeenAt();
        String first = signIn.get("refreshToken").asText();

        JsonNode refreshed = json(refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty()));

        String second = refreshed.get("refreshToken").asText();
        assertThat(second).isNotEqualTo(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(claimsOf(refreshed.get("accessToken").asText()).get("sid").asText())
                .isEqualTo(sessionId.toString());
        assertThat(refreshed.size()).isEqualTo(4);

        AuthSession session = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getRefreshTokenHash()).isEqualTo(RefreshTokens.hash(second));
        assertThat(session.getPreviousRefreshTokenHash()).isEqualTo(RefreshTokens.hash(first));
        assertThat(session.getLastSeenAt()).isAfterOrEqualTo(firstSeen);

        // The new token works in turn.
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeSession() throws Exception {
        JsonNode signIn = bootstrap("sess-reuse-1");
        UUID sessionId = sessionIdOf(signIn);
        String first = signIn.get("refreshToken").asText();
        String second = json(refresh(first).andExpect(status().isOk())).get("refreshToken").asText();
        rotatedAgo(sessionId, Duration.ofSeconds(11));

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        // The legitimate holder of the newer token is signed out too.
        refresh(second)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        AuthSession session = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getRevokedAt()).isNotNull();
        assertThat(session.getRevokedReason()).isEqualTo(SessionRevocationReason.REFRESH_TOKEN_REUSE);
        verify(valueOperations).set("fitsync:revoked-sid:" + sessionId, "1", DENY_LIST_TTL);
    }

    @Test
    void aReplacedTokenPresentedWithinTheGracePeriodIsRefusedButTheSessionLives() throws Exception {
        JsonNode signIn = bootstrap("sess-grace-1");
        UUID sessionId = sessionIdOf(signIn);
        String first = signIn.get("refreshToken").asText();
        String second = json(refresh(first).andExpect(status().isOk())).get("refreshToken").asText();

        // A second tab or a retried request arrives with the old token a moment after the rotation.
        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));

        assertThat(sessionRepository.findById(sessionId).orElseThrow().getRevokedAt()).isNull();
        verify(redisTemplate, never()).opsForValue();
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void theGracePeriodCountsFromTheLatestRotation() throws Exception {
        JsonNode signIn = bootstrap("sess-grace-2");
        UUID sessionId = sessionIdOf(signIn);
        String first = signIn.get("refreshToken").asText();
        String second = json(refresh(first).andExpect(status().isOk())).get("refreshToken").asText();
        rotatedAgo(sessionId, Duration.ofSeconds(9));

        refresh(first).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getRevokedAt()).isNull();

        rotatedAgo(sessionId, Duration.ofSeconds(10));
        refresh(first).andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        refresh(second).andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getRevokedReason())
                .isEqualTo(SessionRevocationReason.REFRESH_TOKEN_REUSE);
    }

    @Test
    void anUnknownRefreshTokenIsExpired() throws Exception {
        refresh(RefreshTokens.generate())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void aRefreshTokenOfAnExpiredSessionIsExpired() throws Exception {
        JsonNode signIn = bootstrap("sess-exp-1");
        AuthSession session = sessionRepository.findById(sessionIdOf(signIn)).orElseThrow();
        session.setExpiresAt(Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MICROS));
        sessionRepository.save(session);

        refresh(signIn.get("refreshToken").asText())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void aRefreshTokenOfARevokedSessionIsRevoked() throws Exception {
        JsonNode signIn = bootstrap("sess-revref-1");
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(signIn)))
                .andExpect(status().isNoContent());

        refresh(signIn.get("refreshToken").asText())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void refreshNeedsNoAccessTokenButDoesNeedABody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + "a".repeat(129) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── sign-out and the session list ──────────────────────────────────────────────────────────────────

    @Test
    void logoutRevokesTheCurrentSessionAndListsItForTheGateway() throws Exception {
        JsonNode signIn = bootstrap("sess-out-1");
        UUID sessionId = sessionIdOf(signIn);

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(signIn)))
                .andExpect(status().isNoContent());

        AuthSession session = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getRevokedAt()).isNotNull();
        assertThat(session.getRevokedReason()).isEqualTo(SessionRevocationReason.LOGOUT);
        verify(valueOperations).set("fitsync:revoked-sid:" + sessionId, "1", DENY_LIST_TTL);

        // Signing out twice is harmless.
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(signIn)))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutStillSucceedsWhenRedisIsDown() throws Exception {
        JsonNode signIn = bootstrap("sess-out-redis-1");
        doThrow(new RedisConnectionFailureException("down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(signIn)))
                .andExpect(status().isNoContent());

        assertThat(sessionRepository.findById(sessionIdOf(signIn)).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void theSessionListShowsActiveSessionsNewestFirstAndMarksTheCurrentOne() throws Exception {
        JsonNode older = bootstrap("sess-list-1");
        JsonNode newer = bootstrap("sess-list-1");
        JsonNode signedOut = bootstrap("sess-list-1");
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(signedOut)))
                .andExpect(status().isNoContent());
        // Using the older device makes it the most recently seen.
        String olderAccess = "Bearer " + json(refresh(older.get("refreshToken").asText())
                .andExpect(status().isOk())).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/sessions").header("Authorization", bearer(newer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].sessionId").value(sessionIdOf(older).toString()))
                .andExpect(jsonPath("$.sessions[0].current").value(false))
                .andExpect(jsonPath("$.sessions[1].sessionId").value(sessionIdOf(newer).toString()))
                .andExpect(jsonPath("$.sessions[1].current").value(true))
                .andExpect(jsonPath("$.sessions[1].deviceLabel").value("Unknown device"))
                .andExpect(jsonPath("$.sessions[1].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.sessions[1].lastSeenAt").isNotEmpty())
                .andExpect(jsonPath("$.sessions[1].refreshTokenHash").doesNotExist());

        mockMvc.perform(get("/api/v1/auth/sessions").header("Authorization", olderAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].current").value(true));
    }

    // ── revoking ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    void revokingAnotherOfMySessionsSignsThatDeviceOut() throws Exception {
        JsonNode phone = bootstrap("sess-rev1-1");
        JsonNode laptop = bootstrap("sess-rev1-1");

        mockMvc.perform(delete("/api/v1/auth/sessions/" + sessionIdOf(phone)).header("Authorization", bearer(laptop)))
                .andExpect(status().isNoContent());

        AuthSession revoked = sessionRepository.findById(sessionIdOf(phone)).orElseThrow();
        assertThat(revoked.getRevokedReason()).isEqualTo(SessionRevocationReason.REVOKED_BY_USER);
        verify(valueOperations).set("fitsync:revoked-sid:" + sessionIdOf(phone), "1", DENY_LIST_TTL);
        refresh(phone.get("refreshToken").asText())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        mockMvc.perform(get("/api/v1/auth/sessions").header("Authorization", bearer(laptop)))
                .andExpect(jsonPath("$.sessions.length()").value(1));
    }

    @Test
    void someoneElsesSessionIdAnswersNotFoundAndIsLeftAlone() throws Exception {
        JsonNode mine = bootstrap("sess-own-1");
        JsonNode theirs = bootstrap("sess-own-2");

        mockMvc.perform(delete("/api/v1/auth/sessions/" + sessionIdOf(theirs)).header("Authorization", bearer(mine)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/auth/sessions/" + UUID.randomUUID()).header("Authorization", bearer(mine)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/auth/sessions/not-a-uuid").header("Authorization", bearer(mine)))
                .andExpect(status().isNotFound());

        assertThat(sessionRepository.findById(sessionIdOf(theirs)).orElseThrow().getRevokedAt()).isNull();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void revokingTheCurrentSessionBehavesLikeLogout() throws Exception {
        JsonNode signIn = bootstrap("sess-revcur-1");

        mockMvc.perform(delete("/api/v1/auth/sessions/" + sessionIdOf(signIn)).header("Authorization", bearer(signIn)))
                .andExpect(status().isNoContent());

        assertThat(sessionRepository.findById(sessionIdOf(signIn)).orElseThrow().getRevokedReason())
                .isEqualTo(SessionRevocationReason.LOGOUT);
        // Even if the gateway could not tell, auth-service refuses a revoked session's token for session calls.
        mockMvc.perform(get("/api/v1/auth/sessions").header("Authorization", bearer(signIn)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void revokingOthersKeepsOnlyTheCurrentSession() throws Exception {
        JsonNode current = bootstrap("sess-others-1");
        JsonNode second = bootstrap("sess-others-1");
        JsonNode third = bootstrap("sess-others-1");
        JsonNode someoneElse = bootstrap("sess-others-2");

        mockMvc.perform(delete("/api/v1/auth/sessions").param("scope", "others")
                        .header("Authorization", bearer(current)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Revoked-Count", "2"));

        assertThat(sessionRepository.findById(sessionIdOf(current)).orElseThrow().getRevokedAt()).isNull();
        assertThat(sessionRepository.findById(sessionIdOf(second)).orElseThrow().getRevokedReason())
                .isEqualTo(SessionRevocationReason.REVOKED_OTHERS);
        assertThat(sessionRepository.findById(sessionIdOf(third)).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(sessionRepository.findById(sessionIdOf(someoneElse)).orElseThrow().getRevokedAt()).isNull();

        mockMvc.perform(delete("/api/v1/auth/sessions").param("scope", "others")
                        .header("Authorization", bearer(current)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Revoked-Count", "0"));
    }

    @Test
    void revokingManyNeedsTheOthersScope() throws Exception {
        JsonNode signIn = bootstrap("sess-scope-1");

        mockMvc.perform(delete("/api/v1/auth/sessions").header("Authorization", bearer(signIn)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/auth/sessions").param("scope", "all").header("Authorization", bearer(signIn)))
                .andExpect(status().isBadRequest());
        assertThat(sessionRepository.findById(sessionIdOf(signIn)).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void requestingAccountDeletionRevokesEverySession() throws Exception {
        JsonNode phone = bootstrap("sess-del-1");
        JsonNode laptop = bootstrap("sess-del-1");

        mockMvc.perform(post("/api/v1/auth/account-deletion/request")
                        .header("Authorization", bearer(laptop))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationText\":\"DELETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        for (JsonNode signIn : new JsonNode[] {phone, laptop}) {
            AuthSession session = sessionRepository.findById(sessionIdOf(signIn)).orElseThrow();
            assertThat(session.getRevokedReason()).isEqualTo(SessionRevocationReason.ACCOUNT_DELETION);
            verify(valueOperations).set("fitsync:revoked-sid:" + session.getId(), "1", DENY_LIST_TTL);
            refresh(signIn.get("refreshToken").asText())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        }
    }

    // ── tokens issued before sessions existed ──────────────────────────────────────────────────────────

    @Test
    void aTokenWithoutASessionIdStillWorksAndHasNoCurrentSession() throws Exception {
        JsonNode signIn = bootstrap("sess-legacy-1");
        AppUser user = appUserRepository.findByExternalSubject("sess-legacy-1").orElseThrow();
        String legacy = "Bearer " + legacyToken(user);

        mockMvc.perform(get("/api/v1/auth/sessions").header("Authorization", legacy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].sessionId").value(sessionIdOf(signIn).toString()))
                .andExpect(jsonPath("$.sessions[0].current").value(false));
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", legacy))
                .andExpect(status().isNoContent());
        assertThat(sessionRepository.findById(sessionIdOf(signIn)).orElseThrow().getRevokedAt()).isNull();
        verify(redisTemplate, never()).opsForValue();
    }

    // ── authentication ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void sessionEndpointsRejectMissingAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/auth/sessions/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/auth/sessions").param("scope", "others"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionEndpointsRejectATokenSignedWithAnotherKey() throws Exception {
        bootstrap("sess-forged-1");
        AppUser user = appUserRepository.findByExternalSubject("sess-forged-1").orElseThrow();
        String forged = "Bearer " + Jwts.builder()
                .subject(user.getEmail())
                .claims(Map.of("userId", user.getId().toString(), "role", "USER", "sid", UUID.randomUUID().toString()))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(OTHER_KEY.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/v1/auth/sessions").header("Authorization", forged))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionCallWithATokenForAnotherUsersSessionIsRefused() throws Exception {
        // A validly signed token whose sid belongs to someone else (it cannot be issued, but must not help).
        JsonNode theirs = bootstrap("sess-cross-2");
        bootstrap("sess-cross-1");
        AppUser me = appUserRepository.findByExternalSubject("sess-cross-1").orElseThrow();
        String crossed = "Bearer " + signedToken(me, sessionIdOf(theirs).toString());

        mockMvc.perform(delete("/api/v1/auth/sessions").param("scope", "others").header("Authorization", crossed))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", crossed))
                .andExpect(status().isNoContent());
        assertThat(sessionRepository.findById(sessionIdOf(theirs)).orElseThrow().getRevokedAt()).isNull();
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void theDenyListEntryOutlivesTheAccessToken() throws Exception {
        JsonNode signIn = bootstrap("sess-ttl-1");
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(signIn)))
                .andExpect(status().isNoContent());
        verify(valueOperations, atLeastOnce()).set(eq("fitsync:revoked-sid:" + sessionIdOf(signIn)), eq("1"),
                eq(DENY_LIST_TTL));
    }

    // ── helpers ────────────────────────────────────────────────────────────────────────────────────────

    private JsonNode bootstrap(String subject) throws Exception {
        return json(mockMvc.perform(post("/api/v1/auth/bootstrap").with(cognitoJwt(subject, subject + "@example.com")))
                .andExpect(status().isOk()));
    }

    /** Moves the session's last rotation into the past, as if {@code ago} had passed since it. */
    private void rotatedAgo(UUID sessionId, Duration ago) {
        AuthSession session = sessionRepository.findById(sessionId).orElseThrow();
        session.setLastSeenAt(Instant.now().minus(ago).truncatedTo(ChronoUnit.MICROS));
        sessionRepository.save(session);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"displayName\":\"Sess\",\"password\":\"supersecret\"}"))
                .andExpect(status().isCreated());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static UUID sessionIdOf(JsonNode signIn) {
        return UUID.fromString(signIn.get("sessionId").asText());
    }

    private static String bearer(JsonNode signIn) {
        return "Bearer " + signIn.get("accessToken").asText();
    }

    private JsonNode claimsOf(String token) throws Exception {
        String payload = token.split("\\.")[1];
        return objectMapper.readTree(Base64.getUrlDecoder().decode(payload));
    }

    private String legacyToken(AppUser user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claims(Map.of("userId", user.getId().toString(), "displayName", user.getDisplayName(),
                        "role", user.getRole().name()))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String signedToken(AppUser user, String sid) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claims(Map.of("userId", user.getId().toString(), "displayName", user.getDisplayName(),
                        "role", user.getRole().name(), "sid", sid))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static JwtRequestPostProcessor cognitoJwt(String subject, String email) {
        return jwt().jwt(jwt -> jwt
                .subject(subject)
                .claim("email", email)
                .claim("name", "Test Person")
                .claim("email_verified", true));
    }
}
