package com.fitsync.gateway.config;

import java.util.List;
import java.util.Map;

import org.springframework.util.AntPathMatcher;

/**
 * The only {@code /api/v1/internal/**} paths the gateway forwards for the admin console, one entry per service
 * and path. A path is relative to the service's {@code /api/v1/internal} and may use {@code *} for one segment.
 *
 * <p>A service that gains an internal endpoint does not get it exposed through the gateway: the path has to be
 * added here, which is the decision. {@code InternalPathListTests} fails when a service has an internal path
 * that is neither listed here nor named there as deliberately not forwarded, and when an entry here matches no
 * path any service has.
 */
public final class GatewayInternalPaths {

    private static final String OUTBOX_PENDING = "/outbox/pending";
    private static final String OUTBOX_PUBLISH = "/outbox/publish";
    private static final String ANALYTICS_USER = "/analytics/users/*/";

    /** Keyed by the segment the console uses after {@code /api/v1/admin/}. */
    private static final Map<String, List<String>> FORWARDABLE = Map.of(
            "auth", List.of(OUTBOX_PENDING, OUTBOX_PUBLISH, "/users/lookup"),
            "users", List.of(OUTBOX_PENDING, OUTBOX_PUBLISH, "/goals/reconcile"),
            "workouts", List.of(OUTBOX_PENDING, OUTBOX_PUBLISH, "/workouts/exercises/summary",
                    "/workouts/exercises/import-catalog"),
            "nutrition", List.of(OUTBOX_PENDING, OUTBOX_PUBLISH),
            "analytics", List.of(ANALYTICS_USER + "dashboard/rebuild", ANALYTICS_USER + "nutrition/rebuild",
                    ANALYTICS_USER + "workouts/rebuild"),
            "coach", List.of("/coach/quality"),
            // The console's "send now" for one user's emails: GET lists the kinds, POST sends one.
            "notifications", List.of("/users/*/emails"));

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private GatewayInternalPaths() {
    }

    /** True when the service's internal path (after {@code /api/v1/internal}) is on the list. */
    public static boolean isForwardable(String service, String internalPath) {
        return FORWARDABLE.getOrDefault(service, List.of()).stream()
                .anyMatch(pattern -> MATCHER.match(pattern, internalPath));
    }

    /** Every listed path by service, for the test that compares the list with what the services expose. */
    public static Map<String, List<String>> all() {
        return FORWARDABLE;
    }
}
