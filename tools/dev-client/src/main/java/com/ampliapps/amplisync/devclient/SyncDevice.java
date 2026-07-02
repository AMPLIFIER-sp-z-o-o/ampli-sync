package com.ampliapps.amplisync.devclient;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Represents one local sync device.
 * Each device has its own id, working directory and local SQLite database.
 * This class wraps lower level http and db operations into steps:
 * prepopulate, local changes(insert, update, delete), push and pull.
 */

public class SyncDevice implements AutoCloseable {
    private final SyncDevClient client;
    private final String deviceId;
    private final Path workDirectory;
    private SqliteDatabase database;

    public SyncDevice(SyncDevClient client, String deviceId, Path workDirectory) {
        this.client = client;
        this.deviceId = deviceId;
        this.workDirectory = workDirectory;
    }

    public void prepopulate() {
        Path archivePath = client.downloadPrepopulatedDatabaseArchive(deviceId, workDirectory);
        Path databasePath = client.unpackDatabaseArchive(archivePath, workDirectory);
        database = SqliteDatabase.open(databasePath);
    }

    public void insertRow(String tableName, Map<String, Object> values) {
        requireDatabase();
        database.insertRow(tableName, values);
    }

    public void updateRow(String tableName, Map<String, Object> values, String whereColumn, Object whereValue) {
        requireDatabase();
        database.updateRow(tableName, values, whereColumn, whereValue);
    }

    public void deleteRow(String tableName, String whereColumn, Object whereValue) {
        requireDatabase();
        database.deleteRow(tableName, whereColumn, whereValue);
    }

    public void push() {
        requireDatabase();

        PayloadBuilder payloadBuilder = new PayloadBuilder(database);
        PayloadBuildResult result = payloadBuilder.buildPushPayloadResult();

        client.sendChanges(deviceId, result.payload());
        database.clearProcessedChanges(result);
    }

    /**
     * Pulls changes for one table, applies them locally, and confirms with commitSync on backend.
     */
    public void pullTable(String tableName) {
        requireDatabase();

        List<PullChanges> changes = client.pullChangesForTable(tableName, deviceId);
        database.applyPullChanges(changes);

        for (PullChanges change : changes) {
            client.commitSync(change.syncId());
        }
    }

    public String findFirstValue(String tableName, String columnName, String whereColumn, Object whereValue) {
        requireDatabase();
        return database.findFirstValue(tableName, columnName, whereColumn, whereValue);
    }

    public boolean rowExists(String tableName, String whereColumn, Object whereValue) {
        requireDatabase();
        return database.rowExists(tableName, whereColumn, whereValue);
    }

    public String deviceId() {
        return deviceId;
    }

    private void requireDatabase() {
        if (database == null) {
            throw new IllegalStateException("Device database is not initialized. Call prepopulate() first.");
        }
    }

    @Override
    public void close() {
        if (database != null) {
            database.close();
        }
    }
}
