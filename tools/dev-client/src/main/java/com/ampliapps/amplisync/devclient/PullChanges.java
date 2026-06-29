package com.ampliapps.amplisync.devclient;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record PullChanges(
        @JsonProperty("TableName")
        String tableName,

        @JsonProperty("Records")
        PullRecords records,

        @JsonProperty("QueryInsert")
        String queryInsert,

        @JsonProperty("QueryUpdate")
        String queryUpdate,

        @JsonProperty("QueryDelete")
        String queryDelete,

        @JsonProperty("TriggerInsert")
        String triggerInsert,

        @JsonProperty("TriggerUpdate")
        String triggerUpdate,

        @JsonProperty("TriggerDelete")
        String triggerDelete,

        @JsonProperty("TriggerInsertDrop")
        String triggerInsertDrop,

        @JsonProperty("TriggerUpdateDrop")
        String triggerUpdateDrop,

        @JsonProperty("TriggerDeleteDrop")
        String triggerDeleteDrop,

        @JsonProperty("SyncId")
        int syncId
) {
    public record PullRecords(
            List<Map<String, Object>> inserts,
            List<Map<String, Object>> updates,
            List<Map<String, Object>> deletes
    ) {
    }
}
