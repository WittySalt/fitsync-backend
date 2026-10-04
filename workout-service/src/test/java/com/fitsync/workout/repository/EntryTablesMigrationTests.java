package com.fitsync.workout.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

/**
 * Migration V16 on H2: rows written before it read back as SET entries with their meaning untouched,
 * their position taken from their order, and the new check constraint refuses a move without an
 * exercise while a segment without one is accepted.
 */
class EntryTablesMigrationTests {

    @Test
    void oldRowsReadBackAsSetEntries() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:entry_migration_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE SCHEMA IF NOT EXISTS workout;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        flyway(dataSource, "15").migrate();
        UUID exerciseId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID planExerciseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("set schema workout");
            insert(connection, "insert into exercises (exercise_id, code, name, exercise_type, primary_muscle_group,"
                    + " equipment, difficulty, status, custom, date_time_created, date_time_updated, created_by,"
                    + " updated_by, version) values (?, 'OLD_SQUAT', 'Old Squat', 'STRENGTH', 'LEGS', 'BARBELL',"
                    + " 'BEGINNER', 'ACTIVE', false, current_timestamp, current_timestamp, 't', 't', 0)", exerciseId);
            insert(connection, "insert into workout_plans (plan_id, user_id, name, goal_focus, workout_type, difficulty,"
                    + " status, date_time_created, date_time_updated, created_by, updated_by, version) values (?, ?,"
                    + " 'Old plan', 'STRENGTH', 'STRENGTH', 'BEGINNER', 'DRAFT', current_timestamp, current_timestamp,"
                    + " 't', 't', 0)", planId, UUID.randomUUID());
            insert(connection, "insert into workout_plan_exercises (plan_exercise_id, plan_id, exercise_id,"
                    + " exercise_name_snapshot, sequence_no, target_sets) values (?, ?, ?, 'Old Squat', 3, 4)",
                    planExerciseId, planId, exerciseId);
            insert(connection, "insert into workout_sessions (session_id, user_id, name, workout_type, status, source,"
                    + " started_at, date_time_created, date_time_updated, created_by, updated_by, version, kind)"
                    + " values (?, ?, 'Old session', 'STRENGTH', 'COMPLETED', 'MANUAL',"
                    + " timestamp with time zone '2026-05-02 10:00:00+00', current_timestamp, current_timestamp,"
                    + " 't', 't', 0, 'STRENGTH')", sessionId, UUID.randomUUID());
            insert(connection, "insert into workout_exercise_logs (exercise_log_id, session_id, exercise_id,"
                    + " exercise_name_snapshot, sequence_no) values (?, ?, ?, 'Old Squat', 2)", logId, sessionId,
                    exerciseId);
        }

        flyway(dataSource, null).migrate();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("set schema workout");
            try (ResultSet plan = statement.executeQuery(
                    "select entry_type, position, sequence_no, exercise_id, target_sets, kind from workout_plan_entries")) {
                assertTrue(plan.next());
                assertEquals("SET", plan.getString("entry_type"));
                assertEquals(3, plan.getInt("position"));
                assertEquals(3, plan.getInt("sequence_no"));
                assertEquals(exerciseId, plan.getObject("exercise_id", UUID.class));
                assertEquals(4, plan.getInt("target_sets"));
                assertEquals(null, plan.getString("kind"));
            }
            try (ResultSet session = statement.executeQuery(
                    "select entry_type, position, logged_on, status from workout_session_entries")) {
                assertTrue(session.next());
                assertEquals("SET", session.getString("entry_type"));
                assertEquals(2, session.getInt("position"));
                assertEquals("2026-05-02", session.getDate("logged_on").toLocalDate().toString());
                assertEquals(null, session.getString("status"));
            }
            try (ResultSet day = statement.executeQuery(
                    "select count(*) from information_schema.columns where table_name = 'workout_plan_days'"
                            + " and column_name in ('target_kind', 'target_value', 'location', 'effort_target')")) {
                assertTrue(day.next());
                assertEquals(4, day.getInt(1));
            }

            insert(connection, "insert into workout_session_entries (exercise_log_id, session_id, exercise_id,"
                    + " exercise_name_snapshot, sequence_no, position, entry_type, kind, planned_seconds)"
                    + " values (?, ?, null, 'Steady', 1001, 1, 'SEGMENT', 'STEADY', 1200)", UUID.randomUUID(), sessionId);
            assertThrows(SQLException.class, () -> insert(connection, "insert into workout_session_entries"
                    + " (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no, position)"
                    + " values (?, ?, null, 'No exercise', 5, 5)", UUID.randomUUID(), sessionId));
        }
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
