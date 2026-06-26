package com.ampliapps.amplisync.devclient;

import java.util.List;

public record ProcessedSqlStatement (
    String sql,
    List<Object> args
) {
}
