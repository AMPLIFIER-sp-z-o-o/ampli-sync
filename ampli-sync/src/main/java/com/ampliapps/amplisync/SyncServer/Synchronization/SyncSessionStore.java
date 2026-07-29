package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.SQLiteSyncConfig;
import java.io.File;


import javax.sql.rowset.CachedRowSet;

final class SyncSessionStore {

    void writeSyncData(String schema, String syncId, CachedRowSet inserts, CachedRowSet updates, CachedRowSet deletes) {

        ensureSyncDataDirectoryExists(schema);

        BinaryWriter binaryWriter = binaryWriter();
        binaryWriter.writeToBinary(syncDataFile(schema, syncId), inserts);
        binaryWriter.writeToBinary(syncDataUpdatesFile(schema, syncId), updates);
        binaryWriter.writeToBinary(syncDataDeletesFile(schema, syncId), deletes);

    }

    CachedRowSet readInserts(String schema, String syncId) {
        return (CachedRowSet) binaryWriter().readFromBinaryFile(syncDataFile(schema, syncId));
    }

    CachedRowSet readUpdates(String schema, String syncId) {
        return (CachedRowSet) binaryWriter().readFromBinaryFile(syncDataUpdatesFile(schema, syncId));
    }

    CachedRowSet readDeletes(String schema, String syncId) {
        return (CachedRowSet) binaryWriter().readFromBinaryFile(syncDataDeletesFile(schema, syncId));
    }

    private BinaryWriter binaryWriter() {
        return new BinaryWriter();
    }

    private void ensureSyncDataDirectoryExists(String schema) {
        File directory = new File(SQLiteSyncConfig.WORKING_DIR + "SyncData/" + schema);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    private String syncDataFile(String schema, String syncId) {
        return SQLiteSyncConfig.WORKING_DIR + "SyncData/" + schema + "/" + syncId + ".dat";
    }

    private String syncDataUpdatesFile(String schema, String syncId) {
        return SQLiteSyncConfig.WORKING_DIR + "SyncData/" + schema + "/" + syncId + "_updates.dat";
    }

    private String syncDataDeletesFile(String schema, String syncId) {
        return SQLiteSyncConfig.WORKING_DIR + "SyncData/" + schema + "/" + syncId + "_deletes.dat";
    }

}
