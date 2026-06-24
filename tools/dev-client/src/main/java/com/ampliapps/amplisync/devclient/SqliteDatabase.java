package com.ampliapps.amplisync.devclient;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

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

    public void printDemoCustomers() {
        String sql = "select id, name, email, city from demo_customers";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                System.out.println(
                        resultSet.getString("id") + " | " + resultSet.getString("name") + " | " + resultSet.getString("email") + " | " + resultSet.getString("city")
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to print demo customers", e);
        }
    }

    public String insertDemoCustomer(String name, String email, String city) {
        String id = UUID.randomUUID().toString();
        String sql = """
              insert into demo_customers (id, name, email, city)
              values (?, ?, ?, ?)
              """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, name);
            statement.setString(3, email);
            statement.setString(4, city);
            statement.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert demo customer", e);
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
