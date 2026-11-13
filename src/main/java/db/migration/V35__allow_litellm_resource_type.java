package db.migration;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Makes sure monitored_resource.resource_type accepts newer resource types (LITELLM_MODEL).
 * Older schemas carry a restrictive CHECK constraint (PostgreSQL) or ENUM column (H2) that
 * only listed the types known at the time.
 */
public class V35__allow_litellm_resource_type extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (!tableExists(context)) {
            return;
        }
        String product = context.getConnection().getMetaData().getDatabaseProductName().toLowerCase();
        try (Statement statement = context.getConnection().createStatement()) {
            if (product.contains("h2")) {
                statement.execute("ALTER TABLE monitored_resource ALTER COLUMN resource_type VARCHAR(255)");
            } else if (product.contains("postgresql")) {
                statement.execute("""
                        DO $$
                        DECLARE c RECORD;
                        BEGIN
                            FOR c IN
                                SELECT con.conname AS name
                                FROM pg_constraint con
                                JOIN pg_class rel ON rel.oid = con.conrelid
                                WHERE rel.relname = 'monitored_resource'
                                  AND con.contype = 'c'
                                  AND pg_get_constraintdef(con.oid) ILIKE '%resource_type%'
                            LOOP
                                EXECUTE format('ALTER TABLE monitored_resource DROP CONSTRAINT %I', c.name);
                            END LOOP;
                        END $$;
                        """);
            }
        }
    }

    private boolean tableExists(Context context) throws Exception {
        DatabaseMetaData metaData = context.getConnection().getMetaData();
        for (String name : new String[] {"MONITORED_RESOURCE", "monitored_resource"}) {
            try (ResultSet resultSet = metaData.getTables(null, null, name, null)) {
                if (resultSet.next()) {
                    return true;
                }
            }
        }
        return false;
    }
}
