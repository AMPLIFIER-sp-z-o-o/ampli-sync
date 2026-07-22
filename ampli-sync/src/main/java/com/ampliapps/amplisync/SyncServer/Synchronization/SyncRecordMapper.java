package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.Logs;
import com.ampliapps.amplisync.SQLiteSyncConfig;
import com.ampliapps.amplisync.SyncServer.Helpers;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

final class SyncRecordMapper {

    void writeColumn(ObjectNode record, String columnName, String colDataType, String colValue, Boolean wasNull) {
        record.put(columnName, 1);
        if (colDataType.equalsIgnoreCase("Boolean") || colDataType.equalsIgnoreCase("bool") || colDataType.equalsIgnoreCase("bit")) {
            if (colValue == null || colValue.isEmpty() || colValue.equalsIgnoreCase("False"))
                record.put(columnName, 0);
            else
                record.put(columnName, 1);
        } else if (Helpers.TypeConvertionTableIsBLOBType(colDataType)) {
            if (!wasNull) {
                byte[] bytesEncoded = Base64.getEncoder().encodeToString(colValue.getBytes()).getBytes();
                record.put(columnName, new String(bytesEncoded));
            }
        } else if (colDataType.equalsIgnoreCase("datetime") || colDataType.equalsIgnoreCase("date")) {
            DateFormat format = new SimpleDateFormat(SQLiteSyncConfig.DATE_FORMAT);
            if (colValue != null && !colValue.isEmpty()) {
                try {
                    if (colValue.trim().length() == 10)
                        colValue += " 00:00:00";
                    Date date = format.parse(colValue);
                    record.put(columnName, format.format(date));
                } catch (ParseException e) {
                    Logs.write(Logs.Level.ERROR, "BuildRecord() " + e.getMessage());
                }
            }
        } else
            record.put(columnName, colValue);
    }
}
