package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Lets the database refuse a second workout in progress for the same person. The service checks
 * before it starts a session, but two simultaneous starts can both pass that check; the index makes
 * the second insert fail instead.
 *
 * <p>Sessions that already break the rule are closed first, so the index can always be built. Per
 * person the newest in-progress session stays open (latest start, then highest id, the order the
 * service reads them in); every other one is closed the way the server closes a left workout that
 * was not trained: cancelled at its last activity, with the time it was closed recorded. A migration
 * cannot run the completion path (totals, records, the WorkoutLogged event), so a duplicate with
 * done sets is cancelled too; it keeps its sets, and the person can still complete it by editing it.
 * Deleted sessions are only marked, so they are left out of both the clean-up and the index.
 *
 * <p>The index is partial on PostgreSQL. H2, which the tests run on, has no partial indexes, so
 * there only the clean-up runs and the service's own check is what keeps one session in progress.
 */
public class V18__Single_in_progress_session_per_user extends BaseJavaMigration {

    private static final String OPEN_SESSIONS = """
            select session_id, user_id, started_at
            from workout_sessions
            where status = 'IN_PROGRESS'
              and deleted_at is null
            order by user_id, started_at desc, session_id desc
            """;

    // The later of the start and the last set or segment marked done or skipped, as the sweep reads it.
    private static final String LAST_ACTIVITY = """
            select max(coalesce(sl.updated_at, e.started_at))
            from workout_session_entries e
            left join workout_exercise_set_logs sl on sl.exercise_log_id = e.exercise_log_id
            where e.session_id = ?
              and (sl.status in ('DONE', 'SKIPPED')
                   or (e.entry_type = 'SEGMENT' and e.status in ('DONE', 'SKIPPED')))
            """;

    private static final String CLOSE_SESSION = """
            update workout_sessions
            set status = 'CANCELLED',
                ended_at = ?,
                duration_minutes = ?,
                auto_closed_at = ?,
                date_time_updated = ?,
                updated_by = 'system',
                version = version + 1
            where session_id = ?
              and status = 'IN_PROGRESS'
            """;

    private static final String POSTGRES_INDEX = """
            create unique index uq_workout_sessions_one_in_progress_per_user
                on workout_sessions (user_id)
                where status = 'IN_PROGRESS' and deleted_at is null
            """;

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        closeAllButNewestOpenSession(connection);
        String product = connection.getMetaData().getDatabaseProductName();
        if (product != null && product.toLowerCase(Locale.ROOT).contains("postgres")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(POSTGRES_INDEX);
            }
        }
    }

    private void closeAllButNewestOpenSession(Connection connection) throws SQLException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Set<UUID> usersSeen = new HashSet<>();
        try (Statement select = connection.createStatement();
             ResultSet open = select.executeQuery(OPEN_SESSIONS);
             PreparedStatement lastActivity = connection.prepareStatement(LAST_ACTIVITY);
             PreparedStatement close = connection.prepareStatement(CLOSE_SESSION)) {
            while (open.next()) {
                UUID sessionId = open.getObject("session_id", UUID.class);
                UUID userId = open.getObject("user_id", UUID.class);
                OffsetDateTime startedAt = open.getObject("started_at", OffsetDateTime.class);
                if (usersSeen.add(userId)) {
                    continue;
                }
                OffsetDateTime endedAt = lastActivity(lastActivity, sessionId, startedAt);
                close.setObject(1, endedAt);
                close.setInt(2, Math.toIntExact(Duration.between(startedAt, endedAt).toMinutes()));
                close.setObject(3, now);
                close.setObject(4, now);
                close.setObject(5, sessionId);
                close.executeUpdate();
            }
        }
    }

    private OffsetDateTime lastActivity(PreparedStatement query, UUID sessionId, OffsetDateTime startedAt)
            throws SQLException {
        query.setObject(1, sessionId);
        try (ResultSet row = query.executeQuery()) {
            OffsetDateTime lastSetAt = row.next() ? row.getObject(1, OffsetDateTime.class) : null;
            return lastSetAt == null || lastSetAt.isBefore(startedAt) ? startedAt : lastSetAt;
        }
    }
}
