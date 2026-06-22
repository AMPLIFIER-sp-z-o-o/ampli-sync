package com.ampliapps.amplisync.devclient;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SqliteDatabase implements AutoCloseable {
    private final Connection connection;

    private SqliteDatabase(Connection connection) {
        this.connection = connection;
    }

    public static SqliteDatabase open(Path databasePath) {
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            return new SqliteDatabase(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to open SQLite database: " + databasePath, e);
        }
    }

    public boolean tableExists(String tableName) {
        String sql = """
                  select 1
                  from sqlite_master
                  where type = 'table'
                    and name = ?
                  """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check SQLite table: " + tableName, e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to close SQLite database", e);
        }
    }
}
