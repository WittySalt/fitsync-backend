package com.fitsync.gateway.controller;

import java.time.Instant;
import java.util.Set;

import com.fitsync.gateway.dto.ApiError;
import com.fitsync.gateway.service.GatewayProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class GatewayProxyController {

    /** The only admin console destinations; anything else is a 404 before any upstream call. */
    private static final Set<String> ADMIN_SERVICE_NAMES =
            Set.of("auth", "users", "workouts", "nutrition", "analytics", "coach");

    private final GatewayProxyService gatewayProxyService;

    public GatewayProxyController(GatewayProxyService gatewayProxyService) {
        this.gatewayProxyService = gatewayProxyService;
    }

    @RequestMapping(path = "/api/v1/auth/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyAuth(HttpServletRequest request,
                                            @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToAuthService(request, body);
    }

    @RequestMapping(path = "/api/v1/users/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyUser(HttpServletRequest request,
                                            @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToUserService(request, body);
    }

    @RequestMapping(path = "/api/v1/workouts/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyWorkout(HttpServletRequest request,
                                               @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToWorkoutService(request, body);
    }

    @RequestMapping(path = "/api/v1/nutrition/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyNutrition(HttpServletRequest request,
                                                 @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToNutritionService(request, body);
    }

    @RequestMapping(path = "/api/v1/analytics/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyAnalytics(HttpServletRequest request,
                                                 @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToAnalyticsService(request, body);
    }

    @RequestMapping(path = "/api/v1/recommendations/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyRecommendation(HttpServletRequest request,
                                                      @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToRecommendationService(request, body);
    }

    /**
     * The coach, which answers in seconds and may answer as a stream.
     *
     * <p>Its own route rather than a branch of the recommendations one, because the timeout and the
     * pass-through both belong to the coach alone. The return type is written out rather than left
     * as a wildcard because Spring reads it to decide whether to stream: a wildcard sends the body
     * to the JSON converter, which turns a stream nobody has written yet into an empty object.
     */
    @RequestMapping(path = "/api/v1/coach/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<StreamingResponseBody> proxyCoach(HttpServletRequest request,
                                                            @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToCoachService(request, body);
    }

    /**
     * The admin console's read/write surface onto each service's own {@code /api/v1/internal/**}.
     *
     * <p>{@code service} is matched against a fixed set before anything is forwarded, and an
     * unmatched value answers 404 without ever reaching the proxy service, so it can never be used
     * to reach a host outside that fixed set. Role enforcement lives in {@code SecurityConfig},
     * which requires ADMIN for the whole {@code /api/v1/admin/**} tree.
     */
    @RequestMapping(path = "/api/v1/admin/{service}/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<?> proxyAdmin(@PathVariable String service,
                                        HttpServletRequest request,
                                        @RequestBody(required = false) byte[] body) {
        if (!ADMIN_SERVICE_NAMES.contains(service)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError(
                            "NOT_FOUND",
                            "Unknown admin service",
                            request.getRequestURI(),
                            Instant.now(),
                            request.getHeader("X-Correlation-Id")));
        }

        try {
            return gatewayProxyService.forwardToAdminService(service, request, body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiError(
                            "INVALID_PATH",
                            ex.getMessage(),
                            request.getRequestURI(),
                            Instant.now(),
                            request.getHeader("X-Correlation-Id")));
        }
    }

    @RequestMapping(path = "/api/v1/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<ApiError> unknownApiRoute(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(
                        "NOT_FOUND",
                        "API route not found",
                        request.getRequestURI(),
                        Instant.now(),
                        request.getHeader("X-Correlation-Id")));
    }
}
