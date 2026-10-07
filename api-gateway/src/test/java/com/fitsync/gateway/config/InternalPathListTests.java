package com.fitsync.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

/**
 * Keeps the gateway's list of forwardable internal paths honest. It reads the controllers of every service off
 * disk, like the Flyway version test does, so a new {@code /api/v1/internal} endpoint cannot be added without
 * someone deciding here whether the admin console may reach it.
 */
class InternalPathListTests {

    /** The console's name for each service, and the module that holds it. */
    private static final Map<String, String> MODULES = Map.of(
            "auth", "auth-service",
            "users", "user-service",
            "workouts", "workout-service",
            "nutrition", "nutrition-service",
            "analytics", "analytics-service",
            "coach", "recommendation-service");

    /**
     * Internal paths that exist and that the gateway deliberately does not forward, each with the reason. The
     * console never calls them; they stay reachable inside the network for an operator.
     */
    private static final Map<String, List<String>> NOT_FORWARDED = Map.of(
            "users", List.of("/profiles/reconcile"));

    private static final String INTERNAL_PREFIX = "/api/v1/internal";
    private static final Pattern CLASS_MAPPING = Pattern.compile(
            "@RequestMapping\\(\\s*\"(" + INTERNAL_PREFIX + "[^\"]*)\"\\s*\\)");
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(?:Get|Post|Put|Patch|Delete)Mapping\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"");
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Test
    void everyInternalPathOfEveryServiceIsListedOrNamedAsNotForwarded() throws IOException {
        List<String> undecided = new ArrayList<>();
        for (var service : MODULES.entrySet()) {
            for (String path : internalPaths(service.getValue())) {
                boolean listed = GatewayInternalPaths.isForwardable(service.getKey(), path);
                boolean excluded = NOT_FORWARDED.getOrDefault(service.getKey(), List.of()).contains(path);
                if (listed == excluded) {
                    undecided.add(service.getKey() + " " + path + (listed ? " is both listed and excluded"
                            : " is neither listed in GatewayInternalPaths nor excluded here"));
                }
            }
        }
        assertThat(undecided).as("internal paths without exactly one decision").isEmpty();
    }

    @Test
    void everyListedPathMatchesSomethingTheServiceExposes() throws IOException {
        List<String> stale = new ArrayList<>();
        for (var entry : GatewayInternalPaths.all().entrySet()) {
            assertThat(MODULES).as("service key " + entry.getKey()).containsKey(entry.getKey());
            List<String> exposed = internalPaths(MODULES.get(entry.getKey()));
            for (String pattern : entry.getValue()) {
                if (exposed.stream().noneMatch(path -> MATCHER.match(pattern, path) || path.equals(pattern)
                        || MATCHER.match(pattern, path.replaceAll("\\{[^/]*}", "x")))) {
                    stale.add(entry.getKey() + " " + pattern);
                }
            }
        }
        assertThat(stale).as("listed paths no service exposes").isEmpty();
    }

    @Test
    void theExcludedPathsReallyExist() throws IOException {
        for (var entry : NOT_FORWARDED.entrySet()) {
            assertThat(internalPaths(MODULES.get(entry.getKey()))).containsAll(entry.getValue());
        }
    }

    @Test
    void theScanSeesTheControllersItShouldSo() throws IOException {
        // A scan that finds nothing would make the two tests above pass for the wrong reason.
        assertThat(internalPaths("workout-service")).contains("/workouts/exercises/summary", "/outbox/pending");
        assertThat(internalPaths("analytics-service")).contains("/analytics/users/{userId}/dashboard/rebuild");
        assertThat(internalPaths("recommendation-service")).containsExactly("/coach/quality");
    }

    /** The service's internal paths after {@code /api/v1/internal}, with path variables written {@code {name}}. */
    private static List<String> internalPaths(String module) throws IOException {
        Path source = Paths.get("..", module, "src", "main", "java");
        assertThat(source).as("module source " + source.toAbsolutePath().normalize()).isDirectory();
        TreeSet<String> paths = new TreeSet<>();
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher prefix = CLASS_MAPPING.matcher(text);
                if (!prefix.find()) {
                    continue;
                }
                String base = prefix.group(1).substring(INTERNAL_PREFIX.length());
                Matcher method = METHOD_MAPPING.matcher(text);
                boolean any = false;
                while (method.find()) {
                    any = true;
                    paths.add(base + method.group(1));
                }
                assertThat(any).as("an internal controller with no mapped methods: " + file).isTrue();
            }
        }
        return List.copyOf(paths);
    }
}
