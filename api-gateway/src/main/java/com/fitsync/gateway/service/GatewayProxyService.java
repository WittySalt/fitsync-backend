package com.fitsync.gateway.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public interface GatewayProxyService {

    ResponseEntity<byte[]> forwardToAuthService(HttpServletRequest request, byte[] body);

    ResponseEntity<byte[]> forwardToUserService(HttpServletRequest request, byte[] body);

    ResponseEntity<byte[]> forwardToWorkoutService(HttpServletRequest request, byte[] body);

    ResponseEntity<byte[]> forwardToNutritionService(HttpServletRequest request, byte[] body);

    ResponseEntity<byte[]> forwardToAnalyticsService(HttpServletRequest request, byte[] body);

    ResponseEntity<byte[]> forwardToRecommendationService(HttpServletRequest request, byte[] body);

    /**
     * Forwards to the coach, which lives in recommendation-service but cannot share its route.
     *
     * <p>Two things make it different. A coach turn waits on a language model, so it takes seconds
     * where every other route takes milliseconds, and the five-second read timeout that protects
     * those routes would cut every answer off. And an event stream has to be passed through as it
     * arrives rather than collected and forwarded.
     *
     * <p>Every coach response is passed through, including the short ones. Declaring the type
     * exactly is not cosmetic: Spring decides whether to stream a response from the handler's
     * declared return type, and a wildcard there sends the body to the JSON converter instead,
     * which serialises the unwritten stream as an empty object.
     */
    ResponseEntity<StreamingResponseBody> forwardToCoachService(HttpServletRequest request, byte[] body);
}
