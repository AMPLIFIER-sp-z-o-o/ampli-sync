package com.ampliapps.amplisync.SyncServer.Synchronization;;

import javax.sql.rowset.CachedRowSet;

final class CommitSyncService {
    private final SQLQueries QUERIES = new SQLQueries();
    private final SyncSessionStore syncSessionStore = new SyncSessionStore();
    private final SyncSessionRepository syncSessionRepository = new SyncSessionRepository();
    private final MergeContentRepository mergeContentRepository = new MergeContentRepository();

    void commit(String syncId, String schema) {
        CachedRowSet cachedDataInserts = syncSessionStore.readInserts(schema, syncId);
        CachedRowSet cachedDataUpdates = syncSessionStore.readUpdates(schema, syncId);
        CachedRowSet cachedDataDeletes = syncSessionStore.readDeletes(schema, syncId);

        CommitSyncSession session = syncSessionRepository.readCommitSyncSession(syncId, schema);

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


}
