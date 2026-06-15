package db.migration;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V29__add_only_tagged_resources_to_discovery extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (!columnExists(context, "resource_discovery", "only_tagged_resources")) {
            try (Statement st = context.getConnection().createStatement()) {
                st.executeUpdate(
                        "ALTER TABLE resource_discovery ADD COLUMN only_tagged_resources BOOLEAN NOT NULL DEFAULT FALSE"
                );
            }
        }
    }

    private boolean columnExists(Context context, String tableName, String columnName) throws Exception {
        DatabaseMetaData meta = context.getConnection().getMetaData();
        // H2 stores names in uppercase, PostgreSQL in lowercase — check both
        for (String tbl : new String[]{tableName.toUpperCase(), tableName}) {
            try (ResultSet rs = meta.getColumns(null, null, tbl, columnName)) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }
}
