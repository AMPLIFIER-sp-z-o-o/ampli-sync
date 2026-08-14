package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;

import javax.sql.rowset.CachedRowSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

final class CommitSyncService {
    private final SQLQueries QUERIES = new SQLQueries();
    private final SyncSessionStore syncSessionStore = new SyncSessionStore();
    private final SyncSessionRepository syncSessionRepository = new SyncSessionRepository();

    void commit(String syncId, String schema) {
        CachedRowSet cachedDataInserts = syncSessionStore.readInserts(schema, syncId);
        CachedRowSet cachedDataUpdates = syncSessionStore.readUpdates(schema, syncId);
        CachedRowSet cachedDataDeletes = syncSessionStore.readDeletes(schema, syncId);

        CommitSyncSession session = readCommitSyncSession(syncId, schema);

        updateSyncData(Integer.parseInt(syncId), schema, session, cachedDataInserts, cachedDataUpdates, cachedDataDeletes);

        syncSessionRepository.finishSync(syncId, schema);
    }

    private record CommitSyncSession(String tableName, int subscriberId) {
    }

    private CommitSyncSession readCommitSyncSession(String syncId, String schema) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            PreparedStatement query = cn.prepareStatement(QUERIES.COMMIT_SYNC(schema));
            query.setInt(1, Integer.parseInt(syncId));
            ResultSet reader = query.executeQuery();

            if (reader.next()) {
                return new CommitSyncSession(
                        reader.getString("TableName"),
                        reader.getInt("subscriberId")
                );
            }
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "CommitSync() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }

        throw new InvalidCommitSyncSessionException("Sync session was not found: " + syncId);
    }

    private void updateSyncData(
            Integer syncId,
            String schema,
            CommitSyncSession session,
            CachedRowSet cachedDataInserts,
            CachedRowSet cachedDataUpdates,
            CachedRowSet cachedDataDeletes
    ) {
        updateInsertedSyncData(syncId, schema, session, cachedDataInserts);
        updateUpdatedSyncData(schema, session, cachedDataUpdates);
        updateDeletedSyncData(schema, session, cachedDataDeletes);
    }

    private void updateInsertedSyncData(Integer syncId, String schema, CommitSyncSession session, CachedRowSet cachedDataInserts) {
        if (cachedDataInserts != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdI = cn.prepareStatement(QUERIES.INSERT_MERGE_CONTENT(schema, session.tableName()));
                cachedDataInserts.beforeFirst();
                while (cachedDataInserts.next()) {
                    cmdI.setInt(1, session.subscriberId());
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

    private void updateUpdatedSyncData(String schema, CommitSyncSession session, CachedRowSet cachedDataUpdates) {
        if (cachedDataUpdates != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdU = cn.prepareStatement(QUERIES.UPDATE_SYNC_DATA_UPDATE(schema, session.tableName()));

                cachedDataUpdates.beforeFirst();
                while (cachedDataUpdates.next()) {
                    cmdU.setString(1, cachedDataUpdates.getString("rowid").trim());
                    cmdU.setInt(2, session.subscriberId());
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

    private void updateDeletedSyncData(String schema, CommitSyncSession session, CachedRowSet cachedDataDeletes) {
        if (cachedDataDeletes != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdD = cn.prepareStatement(QUERIES.UPDATE_SYNC_DATA_DELETE(schema, session.tableName()));
                cachedDataDeletes.beforeFirst();
                while (cachedDataDeletes.next()) {
                    cmdD.setString(1, cachedDataDeletes.getString(1));
                    cmdD.setInt(2, session.subscriberId());
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
