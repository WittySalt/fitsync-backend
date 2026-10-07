package com.fitsync.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards against a table being added to analytics-service without anyone deciding what happens to its rows when an
 * account is deleted. Every table the migrations create must be listed in {@link #DECISIONS} with one of the
 * {@link Fate}s, and every table an entity maps must be among them, so a new table fails the build until the
 * decision is written down. The behaviour of the deletion itself is tested next to the deletion code.
 */
class AccountDeletionDecisionTests {

    /** What becomes of a table's rows when the account that owns them is deleted. */
    enum Fate {
        /** The rows of the deleted user are removed. Shared rows of the same table, if any, stay. */
        DELETED_WITH_ACCOUNT,
        /** The rows stay, with nothing that identifies a person beyond what the decision names. */
        KEPT_ANONYMISED,
        /** The table holds no per-user rows: reference data, a catalogue or code sets. */
        NOT_PER_USER,
        /** The rows stay and still carry the user id; nobody has decided yet whether that is acceptable. */
        KEPT_PENDING_DECISION
    }

    /** Every table of the service and its fate on account deletion. */
    private static final Map<String, Fate> DECISIONS = Map.ofEntries(
            Map.entry("analytics_meal_contributions", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_processed_events", Fate.KEPT_ANONYMISED),
            Map.entry("analytics_user_achievements", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_user_cooks", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_user_daily_body", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_user_daily_nutrition", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_user_daily_workout", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_user_dashboard_snapshot", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_user_energy_profiles", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_user_goal_progress", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_user_streak_rollup", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_user_workout_records", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_weekly_recap_marks", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("analytics_workout_contributions", Fate.DELETED_WITH_ACCOUNT),
            Map.entry("outbox_events", Fate.KEPT_PENDING_DECISION));

    private static final Pattern STATEMENT = Pattern.compile(
            "(?im)^\\s*(?:create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(?<create>\\w+)"
                    + "|drop\\s+table\\s+(?:if\\s+exists\\s+)?(?<drop>\\w+)"
                    + "|alter\\s+table\\s+(?<from>\\w+)\\s+rename\\s+to\\s+(?<to>\\w+))");
    private static final Pattern MIGRATION_FILE = Pattern.compile("^V(\\d+)__.*\\.sql$");
    private static final Pattern MAPPED_TABLE = Pattern.compile("@Table\\(\\s*name\\s*=\\s*\"(\\w+)\"");

    @Test
    void everyTableTheMigrationsCreateHasADecision() throws IOException {
        TreeSet<String> migrated = migratedTables();

        assertThat(migrated).as("tables found in the migrations").isNotEmpty();
        assertThat(migrated)
                .withFailMessage("a migration creates a table that has no decision on account deletion; add it to "
                        + "AccountDeletionDecisionTests.DECISIONS (or remove a decision whose table is gone). "
                        + "Migrated: %s, decided: %s", migrated, DECISIONS.keySet())
                .isEqualTo(new TreeSet<>(DECISIONS.keySet()));
    }

    @Test
    void everyTableAnEntityMapsHasADecision() throws IOException {
        TreeSet<String> mapped = mappedTables();

        assertThat(mapped).as("tables found on entities").isNotEmpty();
        assertThat(DECISIONS.keySet())
                .withFailMessage("an entity maps a table that has no decision on account deletion: %s", mapped)
                .containsAll(mapped);
    }

    private static TreeSet<String> migratedTables() throws IOException {
        Path migrations = Paths.get("src/main/resources/db/migration");
        List<Path> files = new ArrayList<>();
        try (Stream<Path> listed = Files.list(migrations)) {
            listed.filter(path -> MIGRATION_FILE.matcher(path.getFileName().toString()).matches()).forEach(files::add);
        }
        files.sort(Comparator.comparingInt(AccountDeletionDecisionTests::version));
        TreeSet<String> tables = new TreeSet<>();
        for (Path file : files) {
            Matcher matcher = STATEMENT.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                if (matcher.group("create") != null) {
                    tables.add(lower(matcher.group("create")));
                } else if (matcher.group("drop") != null) {
                    tables.remove(lower(matcher.group("drop")));
                } else if (tables.remove(lower(matcher.group("from")))) {
                    tables.add(lower(matcher.group("to")));
                }
            }
        }
        return tables;
    }

    private static TreeSet<String> mappedTables() throws IOException {
        TreeSet<String> tables = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = MAPPED_TABLE.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    tables.add(lower(matcher.group(1)));
                }
            }
        }
        return tables;
    }

    private static int version(Path file) {
        Matcher matcher = MIGRATION_FILE.matcher(file.getFileName().toString());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : Integer.MAX_VALUE;
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
