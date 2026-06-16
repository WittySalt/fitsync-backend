package com.fitsync.analytics.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DashboardSnapshotInitializer {

    private static final String POSTGRES_INSERT = """
            insert into analytics_user_dashboard_snapshot (
                user_id,
                today_workout_count,
                week_workout_count,
                week_workout_minutes,
                active_streak_days,
                generated_at,
                date_time_created,
                date_time_updated,
                created_by,
                updated_by,
                version
            ) values (?, 0, 0, 0, 0, ?, ?, ?, 'analytics-service', 'analytics-service', 0)
            on conflict (user_id) do nothing
            """;

    private static final String H2_INSERT = """
            insert into analytics_user_dashboard_snapshot (
                user_id,
                today_workout_count,
                week_workout_count,
                week_workout_minutes,
                active_streak_days,
                generated_at,
                date_time_created,
                date_time_updated,
                created_by,
                updated_by,
                version
            ) values (?, 0, 0, 0, 0, ?, ?, ?, 'analytics-service', 'analytics-service', 0)
            """;

    private final JdbcTemplate jdbcTemplate;
    private String databaseProductName;

    public DashboardSnapshotInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertIfMissing(UUID userId, Instant generatedAt) {
        Timestamp timestamp = Timestamp.from(generatedAt);
        if (isH2()) {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from analytics_user_dashboard_snapshot where user_id = ?",
                    Integer.class,
                    userId);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.update(H2_INSERT, userId, timestamp, timestamp, timestamp);
            return;
        }
        jdbcTemplate.update(POSTGRES_INSERT, userId, timestamp, timestamp, timestamp);
    }

    private boolean isH2() {
        if (databaseProductName == null) {
            String productName = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());
            databaseProductName = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
        }
        return databaseProductName.contains("h2");
    }
}
