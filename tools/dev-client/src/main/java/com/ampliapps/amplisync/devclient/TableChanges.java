package com.ampliapps.amplisync.devclient;

import java.util.List;
import java.util.Map;

public record TableChanges(
        String table,
        List<Map<String, Object>> inserts,
        List<Map<String, Object>> updates
) {
}
