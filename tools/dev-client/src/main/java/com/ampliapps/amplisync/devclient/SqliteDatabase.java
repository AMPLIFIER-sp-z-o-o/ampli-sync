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

    public List<ProcessedSqlStatement> buildUpdateCleanupStatements(String tableName) {
        String sql = """
                select rowid, mergeupdate
                from %s
                where mergeupdate > 0 and rowid is not null
                """.formatted(tableName);

        List<ProcessedSqlStatement> statements = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                statements.add(new ProcessedSqlStatement(
                        "update " + tableName + " set mergeupdate = 0 where rowid = ? and mergeupdate = ?",
                        List.of(
                                resultSet.getString("rowid"),
                                resultSet.getObject("mergeupdate")
                        )
                ));
            }

            return statements;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to build update cleanup statements for table: " + tableName, e);
        }
    }

    public List<ProcessedSqlStatement> buildDeleteCleanupStatements() {
        String sql = """
              select tableid, rowid
              from mergedelete
              where rowid is not null
              """;

        List<ProcessedSqlStatement> statements = new ArrayList<>();

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                statements.add(new ProcessedSqlStatement(
                        "delete from mergedelete where tableid = ? and rowid = ?",
                        List.of(
                                resultSet.getString("tableid"),
                                resultSet.getString("rowid")
                        )
                ));
            }

            return statements;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to build delete cleanup statements", e);
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

    public void insertRow(String tableName, Map<String, Object> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Insert values cannot be empty");
        }

        String columns = String.join(", ", values.keySet());
        String placeholders = buildPlaceholders(values.size());

        String sql = "insert into " + tableName + " (" + columns + ") values (" + placeholders + ")";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setStatementArgs(statement, new ArrayList<>(values.values()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert row into table: " + tableName, e);
        }
    }

    public void updateRow(String tableName, Map<String, Object> values, String whereColumn, Object whereValue) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Update values cannot be empty");
        }

        List<String> assignments = new ArrayList<>();

        for (String columnName : values.keySet()) {
            assignments.add(columnName + " = ?");
        }

        String sql = "update " + tableName + " set " + String.join(", ", assignments) + " where " + whereColumn + " = ?";

        List<Object> args = new ArrayList<>(values.values());
        args.add(whereValue);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setStatementArgs(statement, args);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update row in table: " + tableName, e);
        }
    }

    public void deleteRow(String tableName, String whereColumn, Object whereValue) {
        String sql = "delete from " + tableName + " where " + whereColumn + " = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, whereValue);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete row from table: " + tableName, e);
        }
    }

    public void executeSql(String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to execute SQL: " + sql, e);
        }
    }

    public void executeSql(String sql, List<Object> args) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setStatementArgs(statement, args);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to execute SQL: " + sql, e);
        }
    }

    public void applyPullChanges(List<PullChanges> changes) {
        for (PullChanges change : changes) {
            applyPullChange(change);
        }
    }

    private void applyPullChange(PullChanges change) {
        executeSql(change.triggerInsertDrop());
        executeSql(change.triggerUpdateDrop());
        executeSql(change.triggerDeleteDrop());

        applyPullInserts(change);
        applyPullUpdates(change);
        applyPullDeletes(change);

        executeSql(change.triggerInsert());
        executeSql(change.triggerUpdate());
        executeSql(change.triggerDelete());
    }

    private void applyPullInserts(PullChanges change) {
        throw new UnsupportedOperationException("todo");
    }

    private void applyPullUpdates(PullChanges change) {
        throw new UnsupportedOperationException("todo");
    }

    private void applyPullDeletes(PullChanges change) {
        throw new UnsupportedOperationException("todo");
    }

    private static String buildPlaceholders(int count) {
        List<String> placeholders = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            placeholders.add("?");
        }

        return String.join(", ", placeholders);
    }

    private static void setStatementArgs(PreparedStatement statement, List<Object> args) throws SQLException {
        for (int index = 0; index < args.size(); index++) {
            statement.setObject(index + 1, args.get(index));
        }
    }


    public String findFirstValue(String tableName, String columnName, String whereClause) {
        String sql = "select " + columnName + " from " + tableName + " where " + whereClause + " limit 1";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new IllegalStateException("No row found in table: " + tableName);
            }

            return resultSet.getString(columnName);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find first value in table: " + tableName, e);
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

    public void executeProcessedStatements(List<ProcessedSqlStatement> statements) {
        for (ProcessedSqlStatement processedStatement : statements) {
            try (PreparedStatement statement = connection.prepareStatement(processedStatement.sql())) {
                List<Object> args = processedStatement.args();

                for (int index = 0; index < args.size(); index++) {
                    statement.setObject(index + 1, args.get(index));
                }

                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to execute processed SQL statement: " + processedStatement.sql(), e);
            }
        }
    }

    public void clearProcessedChanges(PayloadBuildResult result) {
        executeProcessedStatements(result.recordUpdates());
        executeProcessedStatements(result.recordDeletes());
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
