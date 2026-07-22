package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.SQLiteSyncConfig;

import javax.sql.rowset.CachedRowSet;

final class SyncSessionStore {

    void writeSyncData(String syncId, CachedRowSet inserts, CachedRowSet updates, CachedRowSet deletes) {
        BinaryWriter binaryWriter = binaryWriter();
        binaryWriter.writeToBinary(syncDataFile(syncId), inserts);
        binaryWriter.writeToBinary(syncDataUpdatesFile(syncId), updates);
        binaryWriter.writeToBinary(syncDataDeletesFile(syncId), deletes);
    }

    CachedRowSet readInserts(String syncId) {
        return (CachedRowSet) binaryWriter().readFromBinaryFile(syncDataFile(syncId));
    }

    CachedRowSet readUpdates(String syncId) {
        return (CachedRowSet) binaryWriter().readFromBinaryFile(syncDataUpdatesFile(syncId));
    }

    CachedRowSet readDeletes(String syncId) {
        return (CachedRowSet) binaryWriter().readFromBinaryFile(syncDataDeletesFile(syncId));
    }

    private BinaryWriter binaryWriter() {
        return new BinaryWriter();
    }

    private String syncDataFile(String syncId) {
        return SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + ".dat";
    }

    private String syncDataUpdatesFile(String syncId) {
        return SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + "_updates.dat";
    }

    private String syncDataDeletesFile(String syncId) {
        return SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + "_deletes.dat";
    }
}
