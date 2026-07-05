package com.ampliapps.amplisync.devclient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SyncDeviceAssertions {
    static void assertRowValues(
            SyncDevice device,
            String tableName,
            String whereColumn,
            Object whereValue,
            Map<String, Object> expectedValues
    ) {
        Map<String, Object> row = device.findRow(tableName, whereColumn, whereValue);

        for (Map.Entry<String, Object> expectedValue : expectedValues.entrySet()) {
            String columnName = expectedValue.getKey();
            assertTrue(row.containsKey(columnName), "Missing column: " + columnName);
            assertEquals(
                    expectedValue.getValue(),
                    row.get(columnName),
                    "Wrong value for column: " + columnName
            );
        }
    }

    static void assertRowDoesNotExist(
            SyncDevice device,
            String tableName,
            String whereColumn,
            Object whereValue
    ) {
        assertFalse(device.rowExists(tableName, whereColumn, whereValue));
        assertEquals(0, device.countRows(tableName, whereColumn, whereValue));
    }


    static void assertRowsCount(SyncDevice device, String tableName, int expectedCount) {
        assertEquals(expectedCount, device.countRows(tableName));
    }

    static void assertNoLocalChanges(SyncDevice device) {
        assertFalse(device.hasLocalChanges(), "Device has local pending sync changes");
    }

    static void assertNoPendingUpdateOrDeleteMarkers(SyncDevice device) {
        assertFalse(
                device.hasPendingUpdateOrDeleteMarkers(),
                "Device has pending update/delete sync markers"
        );
    }

}
