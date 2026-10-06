package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class V16RecomputeGoalBaselineTests {

    private static final UUID STEADY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GOAL = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private Connection connection;

    @BeforeEach
    void openDatabase() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:v16_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table recommendation_context (
                        user_id uuid primary key, current_goal_type varchar(40), timezone varchar(80),
                        goal_baseline_weight_kg numeric(6, 2), goal_baseline_on date, current_goal_id uuid,
                        stale boolean not null)""");
            statement.execute("insert into recommendation_context values ('" + STEADY
                    + "', 'WEIGHT', 'Asia/Singapore', 71.20, date '2026-10-01', null, false)");
        }
    }

    @AfterEach
    void closeDatabase() throws SQLException {
        connection.close();
    }

    @Test
    @DisplayName("the baseline becomes the first weigh-in on or after the day the goal was set")
    void recomputesTheBaselineFromTheGoalStart() throws Exception {
        createAnalytics();
        try (Statement statement = connection.createStatement()) {
            statement.execute("insert into analytics.analytics_user_goal_progress values ('" + GOAL + "', '" + STEADY
                    + "', 'WEIGHT', 'ACTIVE', timestamp with time zone '2026-08-10 02:00:00+00')");
            statement.execute("insert into analytics.analytics_user_daily_body values ('" + STEADY
                    + "', date '2026-08-01', 75.00), ('" + STEADY + "', date '2026-08-10', 74.20), ('" + STEADY
                    + "', date '2026-10-01', 71.20)");
        }

        new V16__recompute_goal_baseline_from_start().migrate(context());

        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("select goal_baseline_weight_kg, goal_baseline_on, "
                     + "current_goal_id, stale from recommendation_context")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getBigDecimal(1)).isEqualByComparingTo("74.20");
            assertThat(row.getDate(2).toLocalDate()).hasToString("2026-08-10");
            assertThat(row.getObject(3, UUID.class)).isEqualTo(GOAL);
            assertThat(row.getBoolean(4)).isTrue();
        }
    }

    @Test
    @DisplayName("without the analytics tables the rows are left as they are")
    void skipsWithoutTheAnalyticsTables() throws Exception {
        new V16__recompute_goal_baseline_from_start().migrate(context());

        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("select goal_baseline_weight_kg from recommendation_context")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getBigDecimal(1)).isEqualByComparingTo("71.20");
        }
    }

    private void createAnalytics() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("create schema analytics");
            statement.execute("""
                    create table analytics.analytics_user_goal_progress (
                        goal_id uuid, user_id uuid, goal_type varchar(40), goal_status varchar(30),
                        date_time_created timestamp with time zone)""");
            statement.execute("""
                    create table analytics.analytics_user_daily_body (
                        user_id uuid, metric_date date, weight_kg numeric(6, 2))""");
        }
    }

    private Context context() {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        return context;
    }
}
