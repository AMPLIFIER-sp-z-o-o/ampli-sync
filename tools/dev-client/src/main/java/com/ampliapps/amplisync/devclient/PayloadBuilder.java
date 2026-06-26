package com.ampliapps.amplisync.devclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PayloadBuilder {
    private final SqliteDatabase database;

    public record PushPayload(
            List<TableChanges> changes,
            List<DeletedRecord> deletes
    ) {
    }

    public PayloadBuilder(SqliteDatabase database) {
        this.database = database;
    }

    public PushPayload buildPushPayload() {
        return new PushPayload(buildChanges(), database.findDeletedRecords());
    }

    public List<TableChanges> buildChanges() {
        List<TableChanges> changes = new ArrayList<>();

        for (String tableName : database.findSynchronizedTables()) {
            List<Map<String, Object>> inserts = database.findRowsWithNullRowId(tableName);
            List<Map<String, Object>> updates = database.findRowsWithMergeUpdate(tableName);

            if (!inserts.isEmpty() || !updates.isEmpty()) {
                changes.add(new TableChanges(tableName, inserts, updates));
            }
        }

        return changes;
    }
}
