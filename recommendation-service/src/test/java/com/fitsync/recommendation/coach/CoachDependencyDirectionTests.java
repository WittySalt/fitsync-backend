package com.fitsync.recommendation.coach;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces the one dependency rule the coach layer rests on.
 *
 * <p>{@code coach} may depend on the projection and the rules. The projection must never depend on
 * {@code coach}. That direction is what keeps the service's oldest invariant true: the Kafka
 * consumers never call a model. A consumer that reached into the coach package could, at some
 * point, end up awaiting a two-second inference inside a listener and stall the projection behind
 * it.
 *
 * <p>Stated as a convention it is a comment someone eventually ignores. Stated here it is a failing
 * build. The check reads imports off disk rather than pulling in a bytecode analysis library,
 * because one rule does not justify a new dependency, and it matches
 * {@code DuplicateFlywayVersionTest} in nutrition-service, which guards its own invariant the same
 * way.
 */
class CoachDependencyDirectionTests {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java/com/fitsync/recommendation");
    private static final String COACH_PACKAGE = "com.fitsync.recommendation.coach";

    @Test
    @DisplayName("the projection and rules never import the coach package")
    void projectionDoesNotDependOnCoach() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path file : javaFilesOutsideCoach()) {
            List<String> imports = Files.readAllLines(file).stream()
                    .map(String::strip)
                    .filter(line -> line.startsWith("import "))
                    .filter(line -> line.contains(COACH_PACKAGE))
                    .toList();
            if (!imports.isEmpty()) {
                offenders.add(SOURCE_ROOT.relativize(file) + " -> " + String.join(", ", imports));
            }
        }

        assertThat(offenders)
                .withFailMessage("""
                        The projection must not depend on the coach layer. A consumer that reaches \
                        into coach can end up awaiting an inference inside a Kafka listener and \
                        stalling the projection behind it.%n  %s""",
                        String.join("%n  ", offenders))
                .isEmpty();
    }

    @Test
    @DisplayName("the check is looking at real files, not an empty directory")
    void theSourceTreeWasActuallyScanned() throws IOException {
        assertThat(javaFilesOutsideCoach())
                .withFailMessage("the rule above passes trivially if it scans nothing")
                .hasSizeGreaterThan(20);
    }

    private static List<Path> javaFilesOutsideCoach() throws IOException {
        if (!Files.isDirectory(SOURCE_ROOT)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/coach/"))
                    .sorted()
                    .toList();
        }
    }
}
