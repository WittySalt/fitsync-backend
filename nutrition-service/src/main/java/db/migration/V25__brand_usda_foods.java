package db.migration;

import com.fitsync.nutrition.catalog.UsdaBrandBackfill;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Moves the brand of the USDA foods that are already in the catalogue out of their names, for example
 * "McDONALD'S, BIG MAC" becomes the brand McDONALD'S and the name "Big Mac". On a table without USDA foods, such as
 * the tests or a database that has not run the V900 seed yet, it changes nothing; V905 does the same after V900.
 */
public class V25__brand_usda_foods extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        UsdaBrandBackfill.apply(context.getConnection());
    }
}
