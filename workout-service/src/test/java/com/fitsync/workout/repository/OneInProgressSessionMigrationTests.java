package com.fitsync.workout.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

/**
 * Migration V18 on H2: before the one-in-progress index can exist, every person's in-progress
 * sessions but the newest are closed the way a left, untrained workout is closed (cancelled at its
 * last activity, with the closing time recorded). Deleted sessions and other people's are untouched.
 */
class OneInProgressSessionMigrationTests {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void allButTheNewestInProgressSessionPerPersonAreCancelled() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:one_in_progress_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE SCHEMA IF NOT EXISTS workout;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        flyway(dataSource, "16.1").migrate();
        UUID oldest = UUID.randomUUID();
        UUID older = UUID.randomUUID();
        UUID newest = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        UUID bobsOpen = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("set schema workout");
            session(connection, oldest, ALICE, "2026-06-01 09:00:00+00", null);
            session(connection, older, ALICE, "2026-06-02 09:00:00+00", null);
            session(connection, newest, ALICE, "2026-06-03 09:00:00+00", null);
            session(connection, deleted, ALICE, "2026-06-04 09:00:00+00", "2026-06-04 10:00:00+00");
            session(connection, bobsOpen, BOB, "2026-06-01 09:00:00+00", null);
            // The older one was worked on for 25 minutes; the oldest never was.
            insert(connection, "insert into workout_session_entries (exercise_log_id, session_id, entry_type, kind,"
                    + " label, exercise_name_snapshot, sequence_no, position, status, started_at) values (?, ?,"
                    + " 'SEGMENT', 'STEADY', 'Steady', 'Steady', 1001, 1, 'DONE',"
                    + " timestamp with time zone '2026-06-02 09:25:00+00')", UUID.randomUUID(), older);
        }

        flyway(dataSource, null).migrate();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("set schema workout");
            assertOpen(connection, newest);
            assertOpen(connection, deleted);
            assertOpen(connection, bobsOpen);
            assertCancelled(connection, oldest, "2026-06-01T09:00Z", 0);
            assertCancelled(connection, older, "2026-06-02T09:25Z", 25);
        }
    }

    private static void assertOpen(Connection connection, UUID sessionId) throws SQLException {
        try (ResultSet row = row(connection, sessionId)) {
            assertEquals("IN_PROGRESS", row.getString("status"));
            assertNull(row.getObject("auto_closed_at"));
            assertNull(row.getObject("ended_at"));
        }
    }

    private static void assertCancelled(Connection connection, UUID sessionId, String endedAt, int minutes)
            throws SQLException {
        try (ResultSet row = row(connection, sessionId)) {
            assertEquals("CANCELLED", row.getString("status"));
            assertEquals(OffsetDateTime.parse(endedAt).toInstant(),
                    row.getObject("ended_at", OffsetDateTime.class).toInstant());
            assertEquals(minutes, row.getInt("duration_minutes"));
            assertNotNull(row.getObject("auto_closed_at"));
            assertEquals(1L, row.getLong("version"));
        }
    }

    private static ResultSet row(Connection connection, UUID sessionId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "select status, ended_at, duration_minutes, auto_closed_at, version from workout_sessions"
                        + " where session_id = ?");
        statement.setObject(1, sessionId);
        statement.closeOnCompletion();
        ResultSet row = statement.executeQuery();
        row.next();
        return row;
    }

    private static void session(Connection connection, UUID sessionId, UUID userId, String startedAt,
                                String deletedAt) throws SQLException {
        insert(connection, "insert into workout_sessions (session_id, user_id, name, workout_type, status, source,"
                + " started_at, deleted_at, date_time_created, date_time_updated, created_by, updated_by, version,"
                + " kind) values (?, ?, 'Open', 'CARDIO', 'IN_PROGRESS', 'MANUAL', cast(? as timestamp with time zone),"
                + " cast(? as timestamp with time zone), current_timestamp, current_timestamp, 't', 't', 0,"
                + " 'CARDIO')", sessionId, userId, startedAt, deletedAt);
    }

    private static Flyway flyway(JdbcDataSource dataSource, String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas("workout")
                .defaultSchema("workout")
                .table("workout_flyway_schema_history")
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static void insert(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }
}
