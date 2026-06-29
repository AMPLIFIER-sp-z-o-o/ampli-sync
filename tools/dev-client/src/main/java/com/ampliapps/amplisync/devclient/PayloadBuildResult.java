package com.ampliapps.amplisync.devclient;

import java.util.List;

public record PayloadBuildResult (
        PayloadBuilder.PushPayload payload,
        List<ProcessedSqlStatement> recordUpdates,
        List<ProcessedSqlStatement> recordDeletes
){
}
