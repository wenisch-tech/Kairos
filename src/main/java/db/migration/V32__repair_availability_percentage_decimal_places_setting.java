package db.migration;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V32__repair_availability_percentage_decimal_places_setting extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (!tableExists(context, "resource_type_config")) {
            return;
        }

        try (Statement st = context.getConnection().createStatement()) {
            if (!columnExists(context, "resource_type_config", "availability_percentage_decimal_places")) {
                st.executeUpdate(
                        "ALTER TABLE resource_type_config "
                                + "ADD COLUMN availability_percentage_decimal_places INT DEFAULT 2");
            }

            st.executeUpdate(
                    "UPDATE resource_type_config "
                            + "SET availability_percentage_decimal_places = 2 "
                            + "WHERE availability_percentage_decimal_places IS NULL "
                            + "OR availability_percentage_decimal_places < 2 "
                            + "OR availability_percentage_decimal_places > 5");
        }
    }

    private boolean tableExists(Context context, String tableName) throws Exception {
        DatabaseMetaData meta = context.getConnection().getMetaData();
        for (String tbl : new String[]{tableName.toUpperCase(), tableName.toLowerCase(), tableName}) {
            try (ResultSet rs = meta.getTables(null, null, tbl, null)) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean columnExists(Context context, String tableName, String columnName) throws Exception {
        DatabaseMetaData meta = context.getConnection().getMetaData();
        for (String tbl : new String[]{tableName.toUpperCase(), tableName.toLowerCase(), tableName}) {
            try (ResultSet rs = meta.getColumns(null, null, tbl, columnName.toUpperCase())) {
                if (rs.next()) {
                    return true;
                }
            }
            try (ResultSet rs = meta.getColumns(null, null, tbl, columnName.toLowerCase())) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }
}