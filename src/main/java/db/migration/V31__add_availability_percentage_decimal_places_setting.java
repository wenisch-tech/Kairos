package db.migration;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V31__add_availability_percentage_decimal_places_setting extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (!columnExists(context, "resource_type_config", "availability_percentage_decimal_places")) {
            try (Statement st = context.getConnection().createStatement()) {
                st.executeUpdate(
                        "ALTER TABLE resource_type_config "
                                + "ADD COLUMN availability_percentage_decimal_places INT DEFAULT 2");
            }
        }

        try (Statement st = context.getConnection().createStatement()) {
            st.executeUpdate(
                    "UPDATE resource_type_config "
                            + "SET availability_percentage_decimal_places = 2 "
                            + "WHERE availability_percentage_decimal_places IS NULL "
                            + "OR availability_percentage_decimal_places < 2 "
                            + "OR availability_percentage_decimal_places > 5");
        }
    }

    private boolean columnExists(Context context, String tableName, String columnName) throws Exception {
        DatabaseMetaData meta = context.getConnection().getMetaData();
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
