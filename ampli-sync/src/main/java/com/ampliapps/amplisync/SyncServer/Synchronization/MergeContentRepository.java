package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;

import javax.sql.rowset.CachedRowSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;

final class MergeContentRepository {
    private final SQLQueries QUERIES = new SQLQueries();

    void updateInsertedSyncData(
            Integer syncId,
            String schema,
            String tableName,
            int subscriberId,
            CachedRowSet cachedDataInserts
    ) {
        if (cachedDataInserts != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdI = cn.prepareStatement(QUERIES.INSERT_MERGE_CONTENT(schema, tableName));
                cachedDataInserts.beforeFirst();
                while (cachedDataInserts.next()) {
                    cmdI.setInt(1, subscriberId);
                    cmdI.setString(2, cachedDataInserts.getString("rowid").trim());
                    cmdI.setTimestamp(3, new java.sql.Timestamp(new Date().getTime()));
                    cmdI.setInt(4, 1);
                    cmdI.setInt(5, syncId);
                    cmdI.setBoolean(6, false);
                    cmdI.addBatch();
                }
                cmdI.executeBatch();
            } catch (Exception e) {
                Logs.write(Logs.Level.ERROR, "UpdateSyncData() " + e.getMessage());
            } finally {
                JDBCCloser.close(cn);
            }
        }
    }

    void updateUpdatedSyncData(
            String schema,
            String tableName,
            int subscriberId,
            CachedRowSet cachedDataUpdates
    ) {
        if (cachedDataUpdates != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdU = cn.prepareStatement(QUERIES.UPDATE_SYNC_DATA_UPDATE(schema, tableName));

                cachedDataUpdates.beforeFirst();
                while (cachedDataUpdates.next()) {
                    cmdU.setString(1, cachedDataUpdates.getString("rowid").trim());
                    cmdU.setInt(2, subscriberId);
                    cmdU.addBatch();
                }
                cmdU.executeBatch();

            } catch (Exception e) {
                Logs.write(Logs.Level.ERROR, "UpdateSyncData()->updates " + e.getMessage());
            } finally {
                JDBCCloser.close(cn);
            }
        }
    }

    void updateDeletedSyncData(
            String schema,
            String tableName,
            int subscriberId,
            CachedRowSet cachedDataDeletes
    ) {
        if (cachedDataDeletes != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdD = cn.prepareStatement(QUERIES.UPDATE_SYNC_DATA_DELETE(schema, tableName));
                cachedDataDeletes.beforeFirst();
                while (cachedDataDeletes.next()) {
                    cmdD.setString(1, cachedDataDeletes.getString(1));
                    cmdD.setInt(2, subscriberId);
                    cmdD.addBatch();
                }
                cmdD.executeBatch();

            } catch (Exception e) {
                Logs.write(Logs.Level.ERROR, "UpdateSyncData()->deletes " + e.getMessage());
            } finally {
                JDBCCloser.close(cn);
            }
        }
    }
}
