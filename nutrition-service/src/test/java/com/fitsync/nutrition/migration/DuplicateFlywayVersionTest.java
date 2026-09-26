package com.fitsync.nutrition.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the Flyway version namespace across BOTH configured locations.
 *
 * <p>nutrition-service is the only service that loads migrations from two locations
 * ({@code classpath:db/migration} and {@code classpath:db/seed}). Flyway shares a single version
 * namespace across every location, so a seed numbered inside the schema range collides with a
 * schema migration and the service refuses to start with "Found more than one migration with
 * version N".
 *
 * <p>This actually happened: {@code db/seed/V13__seed_usda_foods.sql} and
 * {@code db/migration/V13__add_food_provenance_and_category_map.sql} both claimed version 13, and
 * the service could not boot in any profile that loads both locations. Nothing caught it, because
 * the test profile narrows the locations to {@code db/migration} alone — so every Spring context
 * test stayed green while the runtime was broken. This test reads the files off disk instead of
 * booting a context, which is exactly why it sees what the test profile hides.
 *
 * <p>The convention it enforces: schema migrations occupy V1..V{@value #SEED_RANGE_START}-1, bulk
 * data seeds occupy V{@value #SEED_RANGE_START}+.
 */
class DuplicateFlywayVersionTest {

    /** First version reserved for {@code db/seed}; schema migrations must stay below it. */
    private static final int SEED_RANGE_START = 900;

    private static final Path MIGRATION_DIR = Paths.get("src/main/resources/db/migration");
    private static final Path SEED_DIR = Paths.get("src/main/resources/db/seed");

    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("^V(\\d+(?:[._]\\d+)*)__(.+)\\.sql$");

    @Test
    @DisplayName("no two migrations share a version across db/migration and db/seed")
    void versionsAreUniqueAcrossEveryConfiguredLocation() throws IOException {
        Map<String, List<String>> byVersion = new LinkedHashMap<>();
        for (Path dir : List.of(MIGRATION_DIR, SEED_DIR)) {
            for (Path file : sqlFilesIn(dir)) {
                Matcher matcher = VERSIONED_MIGRATION.matcher(file.getFileName().toString());
                if (matcher.matches()) {
                    byVersion
                            .computeIfAbsent(normalise(matcher.group(1)), key -> new ArrayList<>())
                            .add(dir.getFileName() + "/" + file.getFileName());
                }
            }
        }

        List<String> collisions = byVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "version " + entry.getKey() + " claimed by " + entry.getValue())
                .toList();

        assertThat(collisions)
                .withFailMessage(
                        "Flyway shares one version namespace across all locations, so these files "
                                + "make the service fail to start:%n  %s%n"
                                + "Schema migrations belong below V%d; bulk seeds at V%d or above.",
                        String.join("%n  ", collisions), SEED_RANGE_START, SEED_RANGE_START)
                .isEmpty();
    }

    @Test
    @DisplayName("schema migrations stay below the reserved seed range")
    void schemaMigrationsStayBelowTheSeedRange() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : sqlFilesIn(MIGRATION_DIR)) {
            Matcher matcher = VERSIONED_MIGRATION.matcher(file.getFileName().toString());
            if (matcher.matches() && majorVersion(matcher.group(1)) >= SEED_RANGE_START) {
                offenders.add(file.getFileName().toString());
            }
        }
        assertThat(offenders)
                .withFailMessage(
                        "db/migration must stay below V%d, which is reserved for db/seed: %s",
                        SEED_RANGE_START, offenders)
                .isEmpty();
    }

    @Test
    @DisplayName("bulk seeds sit inside the reserved seed range")
    void seedsSitInsideTheReservedRange() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : sqlFilesIn(SEED_DIR)) {
            Matcher matcher = VERSIONED_MIGRATION.matcher(file.getFileName().toString());
            if (matcher.matches() && majorVersion(matcher.group(1)) < SEED_RANGE_START) {
                offenders.add(file.getFileName().toString());
            }
        }
        assertThat(offenders)
                .withFailMessage(
                        "db/seed must use V%d or above so it can never collide with a schema "
                                + "migration: %s",
                        SEED_RANGE_START, offenders)
                .isEmpty();
    }

    private static List<Path> sqlFilesIn(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    /** Flyway treats {@code V1_1} and {@code V1.1} as the same version. */
    private static String normalise(String rawVersion) {
        return rawVersion.replace('_', '.');
    }

    private static int majorVersion(String rawVersion) {
        String major = normalise(rawVersion).split("\\.", 2)[0];
        return Integer.parseInt(major);
    }
}
