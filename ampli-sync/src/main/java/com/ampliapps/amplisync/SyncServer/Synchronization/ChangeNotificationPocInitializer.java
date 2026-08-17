package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;


public class ChangeNotificationPocInitializer {
    public void initialize(String schema) {
        executeResource(schema, null, "/POSTGRESQL_CHANGE_NOTIFICATION_POC.sql");

        addChangeLogTrigger(schema, "poc_customers");
        addChangeLogTrigger(schema, "poc_orders");
        addChangeLogTrigger(schema, "poc_order_items");
    }

    private void addChangeLogTrigger(String schema, String tableName) {
        executeResource(schema, tableName, "/POSTGRESQL_ADD_CHANGE_LOG_TRIGGER_POC.sql");
    }

    private void executeResource(String schema, String tableName, String resourcePath) {
        try {
            String sql = readResource(resourcePath).replace("{$table_schema}", schema);

            if (tableName != null)
                sql = sql.replace("{$table_name}", tableName);

            executeStatements(sql);
        } catch (IOException | SQLException e) {
            Logs.write(Logs.Level.ERROR, "ChangeNotificationPocInitializer() " + e.getMessage());
        }
    }

    private String readResource(String resourcePath) throws IOException {
        InputStream input = getClass().getResourceAsStream(resourcePath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        return org.apache.commons.io.IOUtils.toString(reader);
    }

    private void executeStatements(String sql) throws SQLException {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            for (String statementSql : sql.split("kol_sc")) {
                Statement statement = cn.createStatement();
                try {
                    statement.execute(statementSql);
                } finally {
                    statement.close();
                }
            }
        } finally {
            JDBCCloser.close(cn);
        }
    }
}
