package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;
import com.ampliapps.amplisync.SQLiteSyncConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

final class PullChangeEnumerator {
    private final SyncRecordMapper recordMapper = new SyncRecordMapper();

    public PullChangeSet enumerate(StringBuilder queryInserts, StringBuilder queryUpdates, StringBuilder queryDeletes) {
        PullChangeSet changeSet = new PullChangeSet();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        int addedRecords = enumerateInserts(changeSet, root, mapper, queryInserts);

        if (!isPackageSizeReached(addedRecords)) {
            addedRecords = enumerateUpdates(changeSet, root, mapper, queryUpdates, addedRecords);
        }

        if (!isPackageSizeReached(addedRecords)) {
            addedRecords = enumerateDeletes(changeSet, root, mapper, queryDeletes, addedRecords);
        }

        changeSet.RowsCount = addedRecords;
        changeSet.Records = root;
        return changeSet;
    }

    private ObjectNode mapCurrentRecord(CachedRowSet rowSet, ObjectMapper mapper) throws SQLException {
        ObjectNode record = mapper.createObjectNode();
        ResultSetMetaData rsmd = rowSet.getMetaData();

        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            String columnName = rsmd.getColumnName(i);
            String colDataType = rsmd.getColumnTypeName(i);
            String colValue = rowSet.getString(i);
            Boolean wasNull = rowSet.wasNull();

            recordMapper.writeColumn(record, columnName, colDataType, colValue, wasNull);
        }

        return record;
    }

    private int enumerateInserts(PullChangeSet changeSet, ObjectNode root, ObjectMapper mapper, StringBuilder queryInserts) {
        int addedRecords = 0;

        Connection cn = Database.getInstance().GetDBConnection();
        try {
            Statement cmd = cn.createStatement();
            Logs.write(Logs.Level.TRACE, queryInserts.toString());
            boolean hasResults = cmd.execute(queryInserts.toString());
            do {
                if (hasResults) {
                    try (ResultSet rs = cmd.getResultSet()) {
                        changeSet.Inserts = cacheResultSet(rs);

                        ArrayNode inserts = mapper.createArrayNode();
                        while (changeSet.Inserts.next()) {
                            changeSet.HasRows = true;
                            ObjectNode record = mapCurrentRecord(changeSet.Inserts, mapper);

                            inserts.add(record);
                            addedRecords++;

                            if (isPackageSizeReached(addedRecords))
                                break;
                        }

                        root.set("inserts", inserts);
                    } catch (Exception e) {
                        Logs.write(Logs.Level.ERROR, "EnumerateChanges() " + e.getMessage());
                    }
                }

                hasResults = cmd.getMoreResults();
            } while (hasResults || cmd.getUpdateCount() != -1);
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "EnumerateChanges() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }

        return addedRecords;
    }

    private int enumerateUpdates(PullChangeSet changeSet, ObjectNode root, ObjectMapper mapper, StringBuilder queryUpdates, int addedRecords) {
        Connection cn = Database.getInstance().GetDBConnection();

        try {
            Statement cmd = cn.createStatement();
            Logs.write(Logs.Level.TRACE, queryUpdates.toString());
            boolean hasResults = cmd.execute(queryUpdates.toString());
            do {
                if (hasResults) {
                    try (ResultSet rs = cmd.getResultSet()) {
                        changeSet.Updates = cacheResultSet(rs);

                        ArrayNode updates = mapper.createArrayNode();
                        while (changeSet.Updates.next()) {
                            changeSet.HasRows = true;
                            ObjectNode record = mapCurrentRecord(changeSet.Updates, mapper);

                            updates.add(record);
                            addedRecords++;
                        }

                        root.set("updates", updates);
                    } catch (Exception e) {
                        Logs.write(Logs.Level.ERROR, "EnumarateChanges() updates " + e.getMessage());
                    }
                }

                hasResults = cmd.getMoreResults();
            } while (hasResults || cmd.getUpdateCount() != -1);
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "EnumarateChanges() updates " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }

        return addedRecords;
    }

    private int enumerateDeletes(PullChangeSet changeSet, ObjectNode root, ObjectMapper mapper, StringBuilder queryDeletes, int addedRecords) {
        Connection cn = Database.getInstance().GetDBConnection();

        try {
            Statement cmd = cn.createStatement();
            Logs.write(Logs.Level.TRACE, queryDeletes.toString());
            boolean hasResults = cmd.execute(queryDeletes.toString());
            do {
                if (hasResults) {
                    try (ResultSet rs = cmd.getResultSet()) {
                        changeSet.Deletes = cacheResultSet(rs);

                        ArrayNode deletes = mapper.createArrayNode();
                        while (changeSet.Deletes.next()) {
                            changeSet.HasRows = true;
                            deletes.add(mapper.createObjectNode().put("rowid", changeSet.Deletes.getString(1)));
                            addedRecords++;
                        }

                        root.set("deletes", deletes);
                    } catch (Exception e) {
                        Logs.write(Logs.Level.ERROR, "EnumarateChanges() deletes " + e.getMessage());
                    }
                }

                hasResults = cmd.getMoreResults();
            } while (hasResults || cmd.getUpdateCount() != -1);
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "EnumarateChanges() deletes " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }

        return addedRecords;
    }

    private boolean isPackageSizeReached(int addedRecords) {
        return addedRecords == Integer.parseInt(SQLiteSyncConfig.PACKAGE_SIZE);
    }

    private CachedRowSet cacheResultSet(ResultSet rs) throws SQLException {
        CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
        rowSet.populate(rs);
        rowSet.beforeFirst();
        return rowSet;
    }
}
