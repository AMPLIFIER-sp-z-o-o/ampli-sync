package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;

import javax.sql.rowset.CachedRowSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class CommitSyncService {
    private final SQLQueries QUERIES = new SQLQueries();
    private final SyncSessionStore syncSessionStore = new SyncSessionStore();
    private final SyncSessionRepository syncSessionRepository = new SyncSessionRepository();
    private final MergeContentRepository mergeContentRepository = new MergeContentRepository();

    private record CommitSyncSession(String tableName, int subscriberId) {
    }

    void commit(String syncId, String schema) {
        CachedRowSet cachedDataInserts = syncSessionStore.readInserts(schema, syncId);
        CachedRowSet cachedDataUpdates = syncSessionStore.readUpdates(schema, syncId);
        CachedRowSet cachedDataDeletes = syncSessionStore.readDeletes(schema, syncId);

        CommitSyncSession session = readCommitSyncSession(syncId, schema);

        mergeContentRepository.updateInsertedSyncData(
                Integer.parseInt(syncId),
                schema,
                session.tableName(),
                session.subscriberId(),
                cachedDataInserts
        );

        mergeContentRepository.updateUpdatedSyncData(
                schema,
                session.tableName(),
                session.subscriberId(),
                cachedDataUpdates
        );

        mergeContentRepository.updateDeletedSyncData(
                schema,
                session.tableName(),
                session.subscriberId(),
                cachedDataDeletes
        );

        syncSessionRepository.finishSync(syncId, schema);

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

}
