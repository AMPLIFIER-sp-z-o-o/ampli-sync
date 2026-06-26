package com.ampliapps.amplisync.devclient;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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

    public List<String> findSynchronizedTables() {
        String sql = """
              select tbl_name
              from sqlite_master
              where type = 'table'
                and sql like '%rowid%'
                and tbl_name != 'mergedelete'
              """;

        List<String> tableNames = new ArrayList<>();

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("tbl_name"));
            }

            return tableNames;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find synchronized tables", e);
        }
    }

    public List<Map<String, Object>> findRowsWithNullRowId(String tableName) {
        String sql = "select * from " + tableName + " where rowid is null";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return toRows(resultSet);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find inserts for table: " + tableName, e);
        }
    }

    public List<Map<String, Object>> findRowsWithMergeUpdate(String tableName) {
        String sql = "select * from " + tableName + " where mergeupdate > 0 and rowid is not null";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return toRows(resultSet);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find updates for table: " + tableName, e);
        }
    }

    private static List<Map<String, Object>> toRows(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();

            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                String columnName = metaData.getColumnName(columnIndex);

                if ("mergeupdate".equals(columnName)) {
                    continue;
                }

                row.put(columnName, resultSet.getObject(columnIndex));
            }

            rows.add(row);
        }

        return rows;
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

    public void printNewInserts() {
        String sql = """
            select id, name, email, city
            from demo_customers
            where rowid is null
            """;

        try (Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(sql)){
                while (resultSet.next()){
                    System.out.println(
                            resultSet.getString("id") + " | " + resultSet.getString("name") + " | " + resultSet.getString("email") + " | " + resultSet.getString("city")
                    );
                }
        }catch (SQLException e) {
                throw new IllegalStateException("Failed to print new inserts with rowid = null");
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

    public void updateDemoCustomerCity(String customerId, String city) {
        String sql = """
              update demo_customers
              set city = ?
              where id = ?
              """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, city);
            statement.setString(2, customerId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update demo customer", e);
        }
    }

    public void deleteDemoCustomer(String customerId) {
        String sql = """
              delete from demo_customers
              where id = ?
              """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, customerId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete demo customer", e);
        }
    }

    public String findFirstExistingCustomer() {
        String sql = """
              select id
              from demo_customers
              where rowid is not null
              limit 1
              """;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new IllegalStateException("No existing demo customer found");
            }

            return resultSet.getString("id");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find existing demo customer", e);
        }
    }

    public List<DeletedRecord> findDeletedRecords() {
        String sql = """
                        select tableid, rowid 
                        from mergedelete
                        where rowid is not null
                        """;

        List<DeletedRecord> deletes = new ArrayList<>();

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                deletes.add(new DeletedRecord(
                        resultSet.getString("tableid"),
                        resultSet.getString("rowid")
                ));
            }

            return deletes;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find deleted records", e);
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
