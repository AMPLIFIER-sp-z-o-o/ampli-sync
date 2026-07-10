package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;
import com.ampliapps.amplisync.SQLiteSyncConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sql.rowset.RowSetProvider;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.rowset.CachedRowSet;


public class PullChangeEnumerator {
    private final SyncRecordMapper recordMapper = new SyncRecordMapper();

    public PullChangeSet enumerate(StringBuilder queryInserts, StringBuilder queryUpdates, StringBuilder queryDeletes) {
        PullChangeSet changeSet = new PullChangeSet();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        int addedRecords = 0;

        Connection cn = Database.getInstance().GetDBConnection();
        try {
            Statement cmd = cn.createStatement();
            Logs.write(Logs.Level.TRACE, queryInserts.toString());
            boolean hasResults = cmd.execute(queryInserts.toString());
            do {
                if (hasResults) {
                    try (ResultSet rs = cmd.getResultSet()) {
                        changeSet.Inserts = RowSetProvider.newFactory().createCachedRowSet();
                        changeSet.Inserts.populate(rs);
                        changeSet.Inserts.beforeFirst();

                        ArrayNode inserts = mapper.createArrayNode();
                        while (changeSet.Inserts.next()) {
                            changeSet.HasRows = true;
                            ObjectNode record = mapCurrentRecord(changeSet.Inserts, mapper);

                            inserts.add(record);
                            addedRecords++;

                            if (addedRecords == Integer.parseInt(SQLiteSyncConfig.PACKAGE_SIZE))
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

        if (addedRecords != Integer.parseInt(SQLiteSyncConfig.PACKAGE_SIZE)) {
            cn = Database.getInstance().GetDBConnection();
            try {
                Statement cmd = cn.createStatement();
                Logs.write(Logs.Level.TRACE, queryUpdates.toString());
                boolean hasResults = cmd.execute(queryUpdates.toString());
                do {
                    if (hasResults) {
                        try (ResultSet rs = cmd.getResultSet()) {
                            changeSet.Updates = RowSetProvider.newFactory().createCachedRowSet();
                            changeSet.Updates.populate(rs);
                            changeSet.Updates.beforeFirst();

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
        }

        if (addedRecords != Integer.parseInt(SQLiteSyncConfig.PACKAGE_SIZE)) {
            cn = Database.getInstance().GetDBConnection();
            try {
                Statement cmd = cn.createStatement();
                Logs.write(Logs.Level.TRACE, queryDeletes.toString());
                boolean hasResults = cmd.execute(queryDeletes.toString());
                do {
                    if (hasResults) {
                        try (ResultSet rs = cmd.getResultSet()) {
                            changeSet.Deletes = RowSetProvider.newFactory().createCachedRowSet();
                            changeSet.Deletes.populate(rs);
                            changeSet.Deletes.beforeFirst();

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

}
