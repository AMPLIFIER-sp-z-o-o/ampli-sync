package com.ampliapps.amplisync.devclient;

public record DeletedRecord(
        String table,
        String rowid
) {
}