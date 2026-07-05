package com.ampliapps.amplisync.devclient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SyncDeviceAssertions {
    static void assertRowValues(
            SyncDevice device,
            String tableName,
            String id,
            Map<String, Object> expectedValues
    ) {
        Map<String, Object> row = device.findRow(tableName, "id", id);

        for(Map.Entry<String, Object> expectedValue: expectedValues.entrySet()) {
            String columnName = expectedValue.getKey();
            assertTrue(row.containsKey(columnName), "Missing column: " + columnName);
            assertEquals(expectedValue.getValue(), row.get(columnName));
        }
    }

    static void assertRowDoesNotExist(SyncDevice device, String tableName, String id) {
        assertFalse(device.rowExists(tableName, "id", id));
        assertEquals(0, device.countRows(tableName, "id", id));
    }
}
