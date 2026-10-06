package db.migration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-off: recomputes the goal pace baseline of every context row with a weight goal from the analytics
 * body projection, the way the Progress goal road takes its start: the first weigh-in on or after the day
 * the goal was set, else the first weigh-in on record.
 *
 * <p>The analytics tables live in the same database under the analytics schema. Where this service's
 * database role cannot see them (a least-privilege deployment) or they do not exist (the H2 tests), the
 * recompute is skipped and the rows keep their baseline until the next new goal sets a fresh one. The work
 * runs behind a savepoint so a refused read never fails the migration.
 */
public class V16__recompute_goal_baseline_from_start extends BaseJavaMigration {

    private static final Logger LOG = LoggerFactory.getLogger(V16__recompute_goal_baseline_from_start.class);

    private static final String VISIBLE_TABLES = """
            select count(*) from information_schema.tables
             where lower(table_schema) = 'analytics'
               and lower(table_name) in ('analytics_user_daily_body', 'analytics_user_goal_progress')
            """;

    private static final String WEIGHT_GOAL_ROWS = """
            select user_id, timezone from recommendation_context
             where upper(current_goal_type) = 'WEIGHT'
            """;

    private static final String ACTIVE_WEIGHT_GOAL = """
            select goal_id, date_time_created from analytics.analytics_user_goal_progress
             where user_id = ? and upper(goal_type) = 'WEIGHT' and upper(goal_status) = 'ACTIVE'
             order by date_time_created desc
            """;

    private static final String FIRST_WEIGH_IN_FROM = """
            select metric_date, weight_kg from analytics.analytics_user_daily_body
             where user_id = ? and weight_kg is not null and metric_date >= ?
             order by metric_date asc
            """;

    private static final String UPDATE_BASELINE = """
            update recommendation_context
               set goal_baseline_weight_kg = ?, goal_baseline_on = ?, current_goal_id = ?, stale = ?
             where user_id = ?
            """;

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        if (!analyticsVisible(connection)) {
            LOG.info("event=goal_baseline_recompute_skipped reason=analytics_tables_not_visible");
            return;
        }
        Savepoint savepoint = connection.setSavepoint();
        try {
            int updated = recompute(connection);
            connection.releaseSavepoint(savepoint);
            LOG.info("event=goal_baseline_recomputed rows={}", updated);
        } catch (SQLException ex) {
            connection.rollback(savepoint);
            // Sanitised inline against CRLF injection (CWE-117), as elsewhere in this service.
            LOG.warn("event=goal_baseline_recompute_skipped reason=analytics_read_failed sqlState={}",
                    String.valueOf(ex.getSQLState()).replace("\r", "_").replace("\n", "_"));
        }
    }

    private static boolean analyticsVisible(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(VISIBLE_TABLES);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() && rows.getInt(1) == 2;
        }
    }

    private static int recompute(Connection connection) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(WEIGHT_GOAL_ROWS);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rows.add(new Row(result.getObject(1, UUID.class), result.getString(2)));
            }
        }
        int updated = 0;
        for (Row row : rows) {
            Goal goal = activeWeightGoal(connection, row.userId());
            if (goal == null) {
                continue;
            }
            LocalDate startedOn = goal.createdAt() == null ? LocalDate.of(1970, 1, 1)
                    : goal.createdAt().atZone(zone(row.timezone())).toLocalDate();
            WeighIn baseline = firstWeighIn(connection, row.userId(), startedOn);
            if (baseline == null) {
                baseline = firstWeighIn(connection, row.userId(), LocalDate.of(1970, 1, 1));
            }
            if (baseline == null) {
                continue;
            }
            try (PreparedStatement update = connection.prepareStatement(UPDATE_BASELINE)) {
                update.setBigDecimal(1, baseline.weightKg());
                update.setDate(2, Date.valueOf(baseline.on()));
                update.setObject(3, goal.goalId());
                update.setBoolean(4, true);
                update.setObject(5, row.userId());
                updated += update.executeUpdate();
            }
        }
        return updated;
    }

    private static Goal activeWeightGoal(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ACTIVE_WEIGHT_GOAL)) {
            statement.setObject(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new Goal(result.getObject(1, UUID.class),
                        result.getTimestamp(2) == null ? null : result.getTimestamp(2).toInstant()) : null;
            }
        }
    }

    private static WeighIn firstWeighIn(Connection connection, UUID userId, LocalDate from) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIRST_WEIGH_IN_FROM)) {
            statement.setObject(1, userId);
            statement.setDate(2, Date.valueOf(from));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new WeighIn(result.getDate(1).toLocalDate(), result.getBigDecimal(2)) : null;
            }
        }
    }

    private static ZoneId zone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            return ZoneOffset.UTC;
        }
    }

    private record Row(UUID userId, String timezone) {
    }

    private record Goal(UUID goalId, Instant createdAt) {
    }

    private record WeighIn(LocalDate on, BigDecimal weightKg) {
    }
}
