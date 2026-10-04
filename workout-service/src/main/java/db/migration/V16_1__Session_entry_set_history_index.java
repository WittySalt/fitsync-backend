package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * The index that reads the history of one exercise by date. On PostgreSQL it covers SET entries only,
 * so segment rows never weigh on exercise history or the estimated best single. H2, which the tests
 * run on, has no partial indexes, so there it covers every row, which answers the same queries.
 */
public class V16_1__Session_entry_set_history_index extends BaseJavaMigration {

    private static final String POSTGRES_INDEX = """
            create index idx_workout_session_entries_set_history
                on workout_session_entries (exercise_id, logged_on)
                where entry_type = 'SET'
            """;

    private static final String PLAIN_INDEX = """
            create index idx_workout_session_entries_set_history
                on workout_session_entries (exercise_id, logged_on)
            """;

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        String product = connection.getMetaData().getDatabaseProductName();
        boolean postgres = product != null && product.toLowerCase(Locale.ROOT).contains("postgres");
        try (Statement statement = connection.createStatement()) {
            if (postgres) {
                statement.execute(POSTGRES_INDEX);
            } else {
                statement.execute(PLAIN_INDEX);
            }
        }
    }
}
