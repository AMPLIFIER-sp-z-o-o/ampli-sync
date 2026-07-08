package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.SQLiteSyncConfig;

import javax.sql.rowset.CachedRowSet;

public class SyncSessionStore {
    public void writeSyncData(Integer syncId, CachedRowSet inserts, CachedRowSet updates, CachedRowSet deletes) {
        BinaryWriter binaryWriter = new BinaryWriter();
        binaryWriter.writeToBinary(syncDataFile(syncId), inserts);
        binaryWriter.writeToBinary(syncDataUpdatesFile(syncId), updates);
        binaryWriter.writeToBinary(syncDataDeletesFile(syncId), deletes);
    }

    private String syncDataFile(Integer syncId) {
        return SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + ".dat";
    }

    private String syncDataUpdatesFile(Integer syncId) {
        return SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + "_updates.dat";
    }

    private String syncDataDeletesFile(Integer syncId) {
        return SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + "_deletes.dat";
    }
}
