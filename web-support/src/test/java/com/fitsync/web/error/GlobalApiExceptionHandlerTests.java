package com.fitsync.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

/**
 * Regression tests for two defects found by exercising the running stack on 2026-09-26.
 *
 * <p><b>Unknown paths returned 500.</b> The {@code Exception.class} catch-all intercepted Spring's
 * no-handler exceptions, so any path a controller did not map came back as {@code INTERNAL_ERROR}.
 * Reproduced against four services; only recommendation-service, which never registers this
 * advice, answered correctly.
 *
 * <p><b>Nothing was ever logged.</b> The class did not import a logger. Every unhandled exception
 * returned a correlation id to the caller and wrote no server-side line, so the id correlated to
 * nothing and no 500 in production could be diagnosed. Verified at the time: three endpoints
 * returned 500 with distinct correlation ids while {@code docker logs} showed nothing at all.
 */
class GlobalApiExceptionHandlerTests {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private GlobalApiExceptionHandler handler;
    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void setUp() {
        handler = new GlobalApiExceptionHandler(new ApiErrorFactory());
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        logger = context.getLogger(GlobalApiExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("an unmapped path is a 404, not a 500")
    void noResourceFoundIsNotFound() {
        ResponseEntity<ApiError> response = handler.handleNoHandler(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/analytics/not-a-real-endpoint"),
                request("GET", "/api/v1/analytics/not-a-real-endpoint"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("the older no-handler exception is also a 404")
    void noHandlerFoundIsNotFound() {
        ResponseEntity<ApiError> response = handler.handleNoHandler(
                new NoHandlerFoundException("GET", "/api/v1/workouts/nope", new org.springframework.http.HttpHeaders()),
                request("GET", "/api/v1/workouts/nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("an unhandled exception is logged at ERROR with its correlation id and stack trace")
    void unhandledExceptionIsLogged() {
        MockHttpServletRequest request = request("POST", "/api/v1/analytics/dashboard");
        request.addHeader(CORRELATION_HEADER, "corr-1234");
        IllegalStateException boom = new IllegalStateException("boom");

        ResponseEntity<ApiError> response = handler.handleUnhandled(boom, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().correlationId()).isEqualTo("corr-1234");

        assertThat(appender.list)
                .withFailMessage("an unhandled exception must leave a server-side log line, "
                        + "otherwise the correlation id in the response correlates to nothing")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage())
                            .contains("corr-1234")
                            .contains("/api/v1/analytics/dashboard");
                    assertThat(event.getThrowableProxy()).isNotNull();
                    assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
                });
    }

    @Test
    @DisplayName("a 404 does not log at ERROR, so bad client paths do not look like server faults")
    void notFoundIsNotAnError() {
        handler.handleNoHandler(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/users/nope"),
                request("GET", "/api/v1/users/nope"));

        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    @DisplayName("a line break in a caller-supplied value cannot forge a log line")
    void loggedValuesAreSanitised() {
        MockHttpServletRequest request = request("GET", "/api/v1/users/me\r\nERROR forged entry");
        request.addHeader(CORRELATION_HEADER, "corr\nINJECTED");

        handler.handleUnhandled(new IllegalStateException("boom"), request);

        assertThat(appender.list)
                .withFailMessage("the request path and the correlation-id header are caller-controlled; "
                        + "SpotBugs flagged CRLF_INJECTION_LOGS on the first version of this logging")
                .allSatisfy(event -> assertThat(event.getFormattedMessage())
                        .doesNotContain("\r")
                        .doesNotContain("\n"));
        assertThat(appender.list).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("corr_INJECTED"));
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }
}
