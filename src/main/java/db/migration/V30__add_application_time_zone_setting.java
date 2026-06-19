package db.migration;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V30__add_application_time_zone_setting extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (!columnExists(context, "resource_type_config", "time_zone")) {
            try (Statement st = context.getConnection().createStatement()) {
                st.executeUpdate("ALTER TABLE resource_type_config ADD COLUMN time_zone VARCHAR(100)");
            }
        }

        String systemTimeZone = ZoneId.systemDefault().getId().replace("'", "''");
        try (Statement st = context.getConnection().createStatement()) {
            st.executeUpdate("UPDATE resource_type_config SET time_zone = '" + systemTimeZone + "' WHERE time_zone IS NULL OR time_zone = ''");
        }

        ZoneId sourceZone = ZoneId.systemDefault();
        convertLocalColumnToUtc(context, "announcement", "active_until", sourceZone);
        convertLocalColumnToUtc(context, "announcement", "created_at", sourceZone);
        convertLocalColumnToUtc(context, "announcement", "updated_at", sourceZone);
        convertLocalColumnToUtc(context, "api_key", "created_at", sourceZone);
        convertLocalColumnToUtc(context, "app_user", "created_at", sourceZone);
        convertLocalColumnToUtc(context, "app_user", "last_login_at", sourceZone);
        convertLocalColumnToUtc(context, "check_result", "checked_at", sourceZone);
        convertLocalColumnToUtc(context, "monitored_resource", "created_at", sourceZone);
        convertLocalColumnToUtc(context, "outage", "start_date", sourceZone);
        convertLocalColumnToUtc(context, "outage", "end_date", sourceZone);
        convertLocalColumnToUtc(context, "resource_discovery", "created_at", sourceZone);
    }

    private void convertLocalColumnToUtc(Context context, String tableName, String columnName, ZoneId sourceZone) throws Exception {
        if (!tableExists(context, tableName) || !columnExists(context, tableName, columnName)) {
            return;
        }

        String selectSql = "SELECT id, " + columnName + " FROM " + tableName + " WHERE " + columnName + " IS NOT NULL";
        String updateSql = "UPDATE " + tableName + " SET " + columnName + " = ? WHERE id = ?";
        try (Statement select = context.getConnection().createStatement();
             ResultSet rs = select.executeQuery(selectSql);
             PreparedStatement update = context.getConnection().prepareStatement(updateSql)) {
            while (rs.next()) {
                Timestamp timestamp = rs.getTimestamp(columnName);
                if (timestamp == null) {
                    continue;
                }
                LocalDateTime utc = timestamp.toLocalDateTime()
                        .atZone(sourceZone)
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
                update.setTimestamp(1, Timestamp.valueOf(utc));
                update.setLong(2, rs.getLong("id"));
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private boolean tableExists(Context context, String tableName) throws Exception {
        DatabaseMetaData meta = context.getConnection().getMetaData();
        for (String tbl : new String[]{tableName.toUpperCase(), tableName}) {
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
