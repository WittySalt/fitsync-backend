package db.seed;

import com.fitsync.nutrition.catalog.UsdaBrandBackfill;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * The brand rules of V25 for a database that runs V900 after V25, which is every new database: Flyway applies the
 * versions in order, so V25 finds no USDA foods there. On a database that already ran V25 this finds nothing left
 * to brand.
 */
public class V905__brand_usda_foods extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        UsdaBrandBackfill.apply(context.getConnection());
    }
}
