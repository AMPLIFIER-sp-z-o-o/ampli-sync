package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;
import com.ampliapps.amplisync.SQLiteSyncConfig;
import com.ampliapps.amplisync.SyncServer.CommonTools;
import com.ampliapps.amplisync.SyncServer.Helpers;
import com.ampliapps.amplisync.SyncServer.SchemaPublish.SchemaGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Stopwatch;
import com.google.common.cache.LoadingCache;
import org.postgresql.util.PGobject;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


public class SyncService {

    private static LoadingCache<String, String> tableSchemaCache = null;
    public Integer syncIdForTestPurpose = -1;
    List<DataObject> dataToSync = new ArrayList<>();
    private final SQLQueries QUERIES = new SQLQueries();
    public CachedRowSet tablesData = null;
    public CachedRowSet tablesDataUpdates = null;
    public CachedRowSet tablesDataDeletes = null;

    public SyncService() {
        SQLiteSyncConfig.Load();
    }

    public String getChangesForTable(String subscriberUUID, String schema, String tableName, String deviceUUID) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        CommonTools common = new CommonTools();
        String subscriberId = common.CheckIfSubscriberExists(subscriberUUID, deviceUUID).toString();

        if (subscriberId.equalsIgnoreCase("-1")) {
            Logs.write(Logs.Level.ERROR, "Error creating new subscriber for UUID " + subscriberUUID);
            return "Error creating new subscriber for UUID " + subscriberUUID;
        }

        Logs.write(Logs.Level.DEBUG, "Getting changes for subscriber " + subscriberId + " and table " + tableName);

        Integer tableId = 0;
        String tableSchema = "";
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            String query = QUERIES.DO_SYNC_GET_TABLE(schema);
            PreparedStatement tableToPublish = cn.prepareStatement(query);

            tableToPublish.setString(1, tableName);
            tableToPublish.setString(2, schema);

            ResultSet reader = tableToPublish.executeQuery();
            if (reader.next()) {
                tableId = reader.getInt("tableid");
                tableSchema = reader.getString("tableschema");
                String tableFilter = reader.getString("tablefilter");
                EnumerateChanges(reader.getString("tableid"), subscriberId, reader.getString("tablename"), tableSchema, tableFilter, subscriberUUID);
            } else
                Logs.write(Logs.Level.INFO, "DoSync(). Table " + schema + "." + tableName + " was not found in MergeTablesToSync.");

            Integer syncId = StartNewSync(subscriberId, tableId, tableSchema);
            this.syncIdForTestPurpose = syncId;
            for (DataObject obj : dataToSync) {
                obj.SyncId = syncId;
                obj.SQLiteSyncVersion = Database.SQLiteSyncVersion;
            }

            if (dataToSync.size() == 0) {
                DataObject emptySync = new DataObject();
                emptySync.SyncId = -1;
                dataToSync.add(emptySync);
                SetSyncFinishMarker(syncId.toString(), tableSchema);
            }

        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "DoSync() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }


        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        StringWriter stringEmp = new StringWriter();
        try {
            objectMapper.writeValue(stringEmp, dataToSync);
        } catch (IOException ex) {
            Logs.write(Logs.Level.ERROR, "DoSync()->JSON Serialization " + ex.getMessage());
        }
        Logs.write(Logs.Level.TRACE, stringEmp.toString());
        stopwatch.stop();
        Long stopwatchMilisecs  = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        if(stopwatchMilisecs > 400)
            if(dataToSync.get(0).RowsCount.isEmpty())
                Logs.write(Logs.Level.WARN, "Getting changes for subscriber ["+deviceUUID+"]/[" + subscriberId + "] and table [" + tableName + "], no changes. Time elapsed: "+ stopwatchMilisecs);
            else
                Logs.write(Logs.Level.WARN, "Getting changes for subscriber ["+deviceUUID+"]/[" + subscriberId + "] and table [" + tableName + "], records count [" + dataToSync.get(0).RowsCount + "/" + dataToSync.get(0).MaxPackageSize + "]. Time elapsed: "+ stopwatchMilisecs);
        return stringEmp.toString();
    }

    public Integer StartNewSync(String subscriberId, Integer tableId, String schema) {

        File theDir = new File(SQLiteSyncConfig.WORKING_DIR + "SyncData");
        if (!theDir.exists()) {
            try {
                theDir.mkdir();
            } catch (SecurityException e) {
                Logs.write(Logs.Level.ERROR, "StartNewSync()->Creating folder SyncData " + e.getMessage());
            }
        }

        Integer syncId = SetSyncStartMarker(subscriberId, tableId, schema);

        BinaryWriter binaryWriter = new BinaryWriter();
        binaryWriter.writeToBinary(SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + ".dat", tablesData);
        binaryWriter.writeToBinary(SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + "_updates.dat", tablesDataUpdates);
        binaryWriter.writeToBinary(SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + "_deletes.dat", tablesDataDeletes);

        return syncId;
    }

    private void EnumerateChanges(String tableId, String subscriberId, String tableName, String tableSchema, String tableFilter, String subscriberUUID) {
        DataObject tableSync = new DataObject();
        boolean hasRows = false;
        tableSync.TableName = tableName;
        tableSync.MaxPackageSize = SQLiteSyncConfig.PACKAGE_SIZE;
        GenerateQueries(tableSync, tableSchema);
        String filterVW = tableSchema + "." + tableName;

        if (tableSchema == null || tableSchema.isEmpty())
            filterVW = tableName;

        String filterVW_CD = " ";

        if (tableFilter != null && tableFilter.trim().length() > 0) {
            filterVW = tableFilter;
            if(filterVW.startsWith("public.fn_") || filterVW.startsWith("fn_")) {
                filterVW = filterVW.replace("@incomming_uniquename", subscriberUUID);
                filterVW_CD = " ";
            }
            else
                filterVW_CD = "and vw.uniquename='" + subscriberUUID + "' ";
            if(tableName.equalsIgnoreCase("mergeidentity"))
                filterVW_CD += " and tb.SubscriberId= " + subscriberId + " ";
            Logs.write(Logs.Level.TRACE, "Using filter [" + tableFilter + "] for table " + tableName);
        }

        StringBuilder queryInserts = BuildMergeQueryInserts(subscriberId, tableSchema, tableName, filterVW, filterVW_CD, subscriberUUID);
        StringBuilder queryUpdates = BuildMergeQueryUpdates(subscriberId, tableSchema, tableName, filterVW, filterVW_CD, subscriberUUID);
        StringBuilder queryDeletes = BuildMergeQueryDeletes(subscriberId, tableSchema, tableName, filterVW, filterVW_CD, subscriberUUID);

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        Connection cn = Database.getInstance().GetDBConnection();
        DatabaseTable table = DatabaseTableGuavaCacheUtil.getTableUsingGuava(tableName, tableSchema);
        if (!tableName.equalsIgnoreCase("mergeidentity")) {
            tableSync.TriggerInsert =  "select 1;";
            tableSync.TriggerInsertDrop = "select 1;";
            tableSync.TriggerUpdate = schemaGenerator.CreateUpdateTrigger(table, schemaGenerator.GenerateUpdateableColumns(table.Columns));
            tableSync.TriggerUpdateDrop = "drop trigger if exists \"trmergeupdate_" + tableName + "\"";
            tableSync.TriggerDelete = schemaGenerator.CreateDeleteTrigger(table);
            tableSync.TriggerDeleteDrop = "drop trigger if exists \"trmergedelete_" + tableName + "\"";
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();


        int addedRecords = 0; //this is equivalent of limit on db
        try {
            Statement cmd = cn.createStatement();
            Logs.write(Logs.Level.TRACE, queryInserts.toString());
            boolean hasResults = cmd.execute(queryInserts.toString());
            do {
                if (hasResults) {
                    try (ResultSet rs = cmd.getResultSet()) {
                        tablesData = RowSetProvider.newFactory().createCachedRowSet();
                        tablesData.populate(rs);
                        tablesData.beforeFirst();
                        ArrayNode inserts = mapper.createArrayNode();
                        while (tablesData.next()) {
                            hasRows = true;
                            ObjectNode record = mapper.createObjectNode();
                            ResultSetMetaData rsmd = tablesData.getMetaData();
                            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                                String columnName = rsmd.getColumnName(i);
                                String colDataType = rsmd.getColumnTypeName(i);
                                String colValue = tablesData.getString(i);
                                Boolean wasNull = tablesData.wasNull();
                                BuildRecord(record, columnName, colDataType, colValue, wasNull);
                            }
                            inserts.add(record);
                            addedRecords++;
                            if(addedRecords == Integer.parseInt(SQLiteSyncConfig.PACKAGE_SIZE))
                                break;
                        }
                        root.set("inserts", inserts);

                    } catch (Exception e) {
                        Logs.write(Logs.Level.ERROR, "EnumarateChanges() " + e.getMessage());
                    }
                }

                hasResults = cmd.getMoreResults();

            } while (hasResults || cmd.getUpdateCount() != -1);

        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "EnumarateChanges() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }

        if(addedRecords != Integer.parseInt(SQLiteSyncConfig.PACKAGE_SIZE)) {
            cn = Database.getInstance().GetDBConnection();
            //updates
            try {

                Statement cmd = cn.createStatement();
                Logs.write(Logs.Level.TRACE, queryUpdates.toString());
                boolean hasResults = cmd.execute(queryUpdates.toString());
                do {
                    if (hasResults) {
                        try (ResultSet rs = cmd.getResultSet()) {
                            tablesDataUpdates = RowSetProvider.newFactory().createCachedRowSet();
                            tablesDataUpdates.populate(rs);
                            tablesDataUpdates.beforeFirst();
                            ArrayNode updates = mapper.createArrayNode();
                            while (tablesDataUpdates.next()) {
                                ObjectNode record = mapper.createObjectNode();
                                ResultSetMetaData rsmd = tablesDataUpdates.getMetaData();
                                hasRows = true;
                                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                                    String columnName = rsmd.getColumnName(i);
                                    String colDataType = rsmd.getColumnTypeName(i);
                                    String colValue = tablesDataUpdates.getString(i);
                                    Boolean wasNull = tablesDataUpdates.wasNull();
                                    BuildRecord(record, columnName, colDataType, colValue, wasNull);
                                }
                                updates.add(record);
                                addedRecords++;
                            }
                            root.set("updates", updates);
                        } catch (Exception e) {
                            Logs.write(Logs.Level.ERROR, "EnumarateChanges() updates " + e.getMessage());
                        }
                    }

                    hasResults = cmd.getMoreResults();

                } while (hasResults || cmd.getUpdateCount() != -1);

            } catch (SQLException e) {
                Logs.write(Logs.Level.ERROR, "EnumarateChanges() updates " + e.getMessage());
            } finally {
                JDBCCloser.close(cn);
            }
        }

        if(addedRecords != Integer.parseInt(SQLiteSyncConfig.PACKAGE_SIZE)) {
            cn = Database.getInstance().GetDBConnection();
            //deletes
            try {
                Statement cmd = cn.createStatement();
                Logs.write(Logs.Level.TRACE, queryDeletes.toString());
                boolean hasResults = cmd.execute(queryDeletes.toString());
                do {
                    if (hasResults) {
                        try (ResultSet rs = cmd.getResultSet()) {
                            tablesDataDeletes = RowSetProvider.newFactory().createCachedRowSet();
                            tablesDataDeletes.populate(rs);
                            tablesDataDeletes.beforeFirst();
                            ArrayNode deletes = mapper.createArrayNode();
                            while (tablesDataDeletes.next()) {
                                hasRows = true;
                                deletes.add(mapper.createObjectNode().put("rowid", tablesDataDeletes.getString(1)));
                                addedRecords++;
                            }
                            root.set("deletes", deletes);
                        } catch (Exception e) {
                            Logs.write(Logs.Level.ERROR, "EnumarateChanges() deletes " + e.getMessage());
                        }
                    }

                    hasResults = cmd.getMoreResults();

                } while (hasResults || cmd.getUpdateCount() != -1);

            } catch (SQLException e) {
                Logs.write(Logs.Level.ERROR, "EnumarateChanges() deletes " + e.getMessage());
            } finally {
                JDBCCloser.close(cn);
            }
        }

        tableSync.RowsCount = Integer.toString(addedRecords);
        tableSync.Records = root;
        if (hasRows)
            dataToSync.add(tableSync);
    }

    private void BuildRecord(ObjectNode record, String columnName, String colDataType, String colValue, Boolean wasNull) {
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
                    if(colValue.trim().length() == 10)
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

    public StringBuilder BuildMergeQueryInserts(String subscriberId, String tableSchema, String tableName, String filterVW, String filterVW_CD, String subscirberUUID) {
        StringBuilder query = new StringBuilder();
        String topLimit = "";
        if (SQLiteSyncConfig.PACKAGE_SIZE != null && !SQLiteSyncConfig.PACKAGE_SIZE.isEmpty())
            topLimit = "LIMIT " + SQLiteSyncConfig.PACKAGE_SIZE;

        query.append("select distinct ");
        query.append("tb.*");
        query.append("from " + tableSchema + "." + tableName + " tb ");
        query.append("join (");
        query.append("select vw.rowid ");
        if(filterVW_CD.trim().length() > 0 || filterVW.startsWith("public.fn_") || filterVW.startsWith("fn_")) {
            query.append("from " + tableSchema + "." + filterVW + " vw ");
        } else {
            query.append("from " + tableSchema + "." + tableName + " vw ");
        }
        query.append("where ");
        query.append("not exists (select 1 from  " + tableSchema + ".MergeContent_" + tableName + " t where vw.rowid=t.rowid and t.SubscriberId=" + subscriberId + ") ");
        if(!filterVW.startsWith(tableSchema + ".fn_") && !filterVW.startsWith("fn_"))
            if(filterVW_CD.trim().length() > 0)
                query.append("and vw.uniquename='"+subscirberUUID+"'");
        query.append(" " + topLimit + "");
        query.append(") inserts on tb.rowid = inserts.rowid ");
        if(tableName.equalsIgnoreCase("mergeidentity"))
            query.append("and tb.subscriberid =" + subscriberId);

        return query;
    }

    public StringBuilder BuildMergeQueryUpdates(String subscriberId, String tableSchema, String tableName, String filterVW, String filterVW_CD, String subscirberUUID) {
        StringBuilder query = new StringBuilder();
        String topLimit = "";
        if (SQLiteSyncConfig.PACKAGE_SIZE != null && !SQLiteSyncConfig.PACKAGE_SIZE.isEmpty())
            topLimit = "limit " + SQLiteSyncConfig.PACKAGE_SIZE;

        query.append("select distinct ");
        query.append("tb.*");
        query.append("from " + tableSchema + "." + tableName + " tb ");
        if(filterVW_CD.trim().length() > 0 || filterVW.startsWith("public.fn_") || filterVW.startsWith("fn_")) {
            query.append("join " + tableSchema + "." + filterVW + " vw on tb.rowid=vw.rowid ");
            query.append("join " + tableSchema + ".mergecontent_" + tableName + " t on vw.rowid=t.rowid ");
        } else {
            query.append("join " + tableSchema + ".mergecontent_" + tableName + " t on tb.rowid=t.rowid ");
        }

        query.append("where t.record_has_changed=true and t.SubscriberId=" + subscriberId + " " + filterVW_CD + " ");
        if(tableName.equalsIgnoreCase("mergeidentity"))
            query.append(" and tb.subscriberid =" + subscriberId);
        query.append(" " + topLimit + ";");

        return query;
    }

    public StringBuilder BuildMergeQueryDeletes(String subscriberId, String tableSchema, String tableName, String filterVW, String filterVW_CD, String subscirberUUID) {
        StringBuilder query = new StringBuilder();
        query.append("select  ");
        query.append("m.rowid ");
        query.append("from " + tableSchema + ".mergecontent_" + tableName + " m ");
        if(filterVW_CD.trim().length() > 0 || filterVW.startsWith(tableSchema + ".fn_") || filterVW.startsWith("fn_")) {
            if(tableName.equalsIgnoreCase("mergeidentity"))
                query.append("left join " + tableSchema + "." + filterVW + " vw on m.rowid=vw.rowid and vw.uniquename='" + subscirberUUID + "' and vw.subscriberid =" + subscriberId + " ");
            else
                query.append("left join " + tableSchema + "." + filterVW + " vw on m.rowid=vw.rowid " + filterVW_CD + " ");
            query.append("where vw.rowid is null  and m.subscriberid=" + subscriberId);

        } else {
            query.append("left join " + tableSchema + "." + tableName + " vw on m.rowid=vw.rowid ");
            query.append("where vw.rowid is null and m.subscriberid=" + subscriberId );
        }

        return query;
    }

    private void GenerateQueries(DataObject tableSync, String tableSchema) {
        String tableNameClear = tableSync.TableName;

        DatabaseTable table = DatabaseTableGuavaCacheUtil.getTableUsingGuava(tableNameClear, tableSchema);

        StringBuilder insertStatement = new StringBuilder();
        StringBuilder updateStatment = new StringBuilder();

        insertStatement.append("insert or replace into " + tableNameClear + " (");
        for (DatabaseTableColumn col : table.Columns)
            if (!col.Name.equalsIgnoreCase("mergeinsertsource")) {
                insertStatement.append("[" + col.Name + "]");
                insertStatement.append(",");
            }

        tableSync.QueryInsert = insertStatement.substring(0, insertStatement.toString().length() - 1) + ") values (";
        insertStatement = new StringBuilder();
        for (DatabaseTableColumn col : table.Columns)
            if (!col.Name.equalsIgnoreCase("mergeinsertsource")) {
                insertStatement.append("?");
                insertStatement.append(",");
            }
        tableSync.QueryInsert += insertStatement.substring(0, insertStatement.toString().length() - 1) + ");";

        updateStatment.append("update " + tableNameClear + " set ");
        for (DatabaseTableColumn col : table.Columns)
            if (!col.Name.equalsIgnoreCase("mergeinsertsource")) {
                if (!col.IsInPrimaryKey) {
                    updateStatment.append("[" + col.Name + "]");
                    updateStatment.append("=?,");
                }
            }

        updateStatment = new StringBuilder(updateStatment.substring(0, updateStatment.toString().length() - 1));

        updateStatment.append(" where ");

        if (table.PrimaryKeyColumns.size() > 0) {
            for (String pk : table.PrimaryKeyColumns) {
                updateStatment.append(pk);
                updateStatment.append("=? and ");
            }

            tableSync.QueryUpdate = updateStatment.substring(0, updateStatment.toString().length() - 5) + ";";
        } else {
            updateStatment.append(SQLQueries.GET_ROWID_COLUMN_NAME() + "=?");
            tableSync.QueryUpdate = updateStatment + ";";
        }

        tableSync.QueryDelete = "delete from " + tableNameClear + " where " + SQLQueries.GET_ROWID_COLUMN_NAME() + "=";
    }

    public void CommitSync(String syncId, String schema) {
        BinaryWriter binaryWriter = new BinaryWriter();
        CachedRowSet cachedDataInserts = (CachedRowSet) binaryWriter.readFromBinaryFile(SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + ".dat");
        CachedRowSet cachedDataUpdates = (CachedRowSet) binaryWriter.readFromBinaryFile(SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + "_updates.dat");
        CachedRowSet cachedDataDeletes = (CachedRowSet) binaryWriter.readFromBinaryFile(SQLiteSyncConfig.WORKING_DIR + "SyncData/" + syncId + "_deletes.dat");

        String tableName = "";
        int subscriberId = 0;
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            PreparedStatement query = cn.prepareStatement(QUERIES.COMMIT_SYNC(schema));
            query.setInt(1, Integer.parseInt(syncId));
            ResultSet reader = query.executeQuery();
            while (reader.next()) {
                tableName = reader.getString("TableName");
                subscriberId = reader.getInt("subscriberId");
            }
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "CommitSync() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }

        UpdateSyncData(Integer.parseInt(syncId), schema, tableName, subscriberId, cachedDataInserts, cachedDataUpdates, cachedDataDeletes);
        SetSyncFinishMarker(syncId, schema);
    }

    private void SetSyncFinishMarker(String syncId, String schema) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            PreparedStatement query = cn.prepareStatement(QUERIES.COMMIT_SYNC_UPDATE(schema));
            query.setInt(1, Integer.parseInt(syncId));
            query.execute();
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "SetSyncFinishMarker() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
    }

    private Integer SetSyncStartMarker(String subscriberId, Integer tableId, String schema) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            Integer id = 0;
            Integer affectedRows = 0;

            PreparedStatement query = cn.prepareStatement(QUERIES.START_NEW_SYNC(schema), Statement.RETURN_GENERATED_KEYS);
            query.setInt(1, Integer.parseInt(subscriberId));
            query.setString(2, "");
            query.setInt(3, tableId);


            affectedRows = query.executeUpdate();

            if (affectedRows == 0) {
                Logs.write(Logs.Level.ERROR, "Creating new sync failed, no ID obtained.");
            }

            try (ResultSet generatedKeys = query.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    id = generatedKeys.getInt(1);
                } else {
                    Logs.write(Logs.Level.ERROR, "Creating new sync failed, no ID obtained.");
                }
            }

            return id;
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "SetSyncStartMarker() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
        return 0;
    }

    private void UpdateSyncData(Integer syncId, String schema, String tableName, Integer subscriberId, CachedRowSet cachedDataInserts, CachedRowSet cachedDataUpdates, CachedRowSet cachedDataDeletes) {

        if (cachedDataInserts != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdI = cn.prepareStatement(QUERIES.INSERT_MERGE_CONTENT(schema, tableName));
                cachedDataInserts.beforeFirst();
                while (cachedDataInserts.next()) {
                    cmdI.setInt(1, subscriberId);
                    cmdI.setString(2, cachedDataInserts.getString("rowid").trim());
                    cmdI.setTimestamp(3, new java.sql.Timestamp(new Date().getTime()));
                    cmdI.setInt(4, 1);
                    cmdI.setInt(5, syncId);
                    cmdI.setBoolean(6, false);
                    cmdI.addBatch();
                }
                cmdI.executeBatch();
            } catch (Exception e) {
                Logs.write(Logs.Level.ERROR, "UpdateSyncData() " + e.getMessage());
            } finally {
                JDBCCloser.close(cn);
            }
        }

        if (cachedDataUpdates != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdU = cn.prepareStatement(QUERIES.UPDATE_SYNC_DATA_UPDATE(schema, tableName));

                cachedDataUpdates.beforeFirst();
                while (cachedDataUpdates.next()) {
                    cmdU.setString(1, cachedDataUpdates.getString("rowid").trim());
                    cmdU.setInt(2, subscriberId);
                    cmdU.addBatch();
                }
                cmdU.executeBatch();

            } catch (Exception e) {
                Logs.write(Logs.Level.ERROR, "UpdateSyncData()->updates " + e.getMessage());
            } finally {
                JDBCCloser.close(cn);
            }
        }

        if (cachedDataDeletes != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdD = cn.prepareStatement(QUERIES.UPDATE_SYNC_DATA_DELETE(schema, tableName));
                cachedDataDeletes.beforeFirst();
                while (cachedDataDeletes.next()) {
                    cmdD.setString(1, cachedDataDeletes.getString(1));
                    cmdD.setInt(2, subscriberId);
                    cmdD.addBatch();
                }
                cmdD.executeBatch();

            } catch (Exception e) {
                Logs.write(Logs.Level.ERROR, "UpdateSyncData()->deletes " + e.getMessage());
            } finally {
                JDBCCloser.close(cn);
            }
        }
    }

    public void ReceiveData(ObjectNode receivedData, String schema, String subscriberUUID, String deviceUniqueId) {
        Logs.write(Logs.Level.INFO, "Receiving data from subscriber " + subscriberUUID);

        CommonTools common = new CommonTools();
        String subscriberId = common.CheckIfSubscriberExists(subscriberUUID, deviceUniqueId).toString();

        if (subscriberId.equalsIgnoreCase("-1")) {
            Logs.write(Logs.Level.ERROR, "Error creating new subscriber for UUID " + subscriberUUID);
            return;
        }

        Integer syncId = StartNewReception(subscriberId, receivedData, schema);

        CommitChangesToDb(receivedData, schema, subscriberId);
        SetSyncFinishMarker(syncId.toString(), schema);
        Logs.write(Logs.Level.INFO, "Finished receiving data from subscriber " + subscriberUUID);
    }

    private Integer StartNewReception(String subscriberId, ObjectNode data, String schema) {
        Integer syncId = SetSyncStartMarker(subscriberId, -1, schema);
        BufferedWriter writer = null;

        File theDir = new File(SQLiteSyncConfig.WORKING_DIR + "ReceivedData");
        if (!theDir.exists()) {
            try {
                theDir.mkdir();
            } catch (SecurityException se) {
                Logs.write(Logs.Level.ERROR, "StartNewReception()->Creating folder ReceivedData " + se.getMessage());
            }
        }

        try {
            File recieveDataFile = new File(SQLiteSyncConfig.WORKING_DIR + "ReceivedData/" + syncId.toString() + ".dat");
            writer = new BufferedWriter(new FileWriter(recieveDataFile));
            writer.write(data.toPrettyString());
        } catch (Exception e) {
            Logs.write(Logs.Level.ERROR, "StartNewReception() " + e.getMessage());
        } finally {
            try {
                writer.close();
            } catch (Exception e) {
                Logs.write(Logs.Level.ERROR, "StartNewReception() " + e.getMessage());
            }
        }

        return syncId;
    }

    private void CommitChangesToDb(ObjectNode data, String schema, String subscriberId) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Started collecting deleted records.");
            JsonNode deletes = data.path("deletes");
            PushDeletedRecords(deletes, schema);
            Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Finished collecting deleted records.");

            // collecting inserts
            JsonNode changes = data.path("changes");
            if (changes.isArray()) {
                PreparedStatement tablesOrder = cn.prepareStatement(QUERIES.INSERTATION_TABLES_ORDER(schema));
                ResultSet reader = tablesOrder.executeQuery();
                List<String> orderedTableList = new ArrayList<>();
                while (reader.next()) {
                    String tableName = reader.getString("table_name").toLowerCase().split("\\.")[1];
                    orderedTableList.add(tableName);
                }
                for (JsonNode change : changes)
                    if(!orderedTableList.contains(change.path("table").asText()))
                        orderedTableList.add(change.path("table").asText());
                for (String tableName : orderedTableList)
                    for (JsonNode change : changes) {
                        if (tableName.equalsIgnoreCase(change.path("table").asText())) {
                            Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Started collecting inserts for table " + tableName);
                            JsonNode inserts = change.path("inserts");
                            PushInsertRecords(inserts, subscriberId, tableName, schema);
                            Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Finished collecting inserts for table " + tableName);
                            // collecting updates
                            JsonNode updates = change.path("updates");
                            Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Started collecting updates.");
                            PushUpdateRecords(updates, tableName, schema);
                            Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Finished collecting updates.");
                        }
                    }

            }
        } catch (Exception e) {
            Logs.write(Logs.Level.ERROR, "CommitChangesToDb() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
    }

    private DatabaseTableParameter GetParamForDbField(String colName, List<DatabaseTableParameter> paramList) {
        for (DatabaseTableParameter p : paramList)
            if (p.ParameterName.equalsIgnoreCase(colName))
                return p;
        return null;
    }

    private void PushInsertRecords(JsonNode inserts, String subscriber, String currentTable, String tableSchema) {
        SchemaGenerator schemaGen = new SchemaGenerator();
        String insertSQLQuery = schemaGen.CreateInsertStatementWithParams(currentTable, tableSchema);
        List<DatabaseTableParameter> paramList = schemaGen.GetStatmentParams(currentTable, true, tableSchema, 1);
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            cn.setAutoCommit(true);
            PreparedStatement insertStatement = cn.prepareStatement(insertSQLQuery);
            PreparedStatement mergeContent = cn.prepareStatement(QUERIES.INSERT_MERGE_CONTENT(tableSchema, currentTable));
            SetDefaultsForParams(insertStatement, currentTable, paramList);

            if (inserts.isArray()) {
                for (JsonNode node : inserts) {
                    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        String fieldName = entry.getKey();
                        JsonNode value = entry.getValue();
                        if (!fieldName.equalsIgnoreCase(SQLQueries.GET_ROWID_COLUMN_NAME())) {
                            DatabaseTableParameter param = GetParamForDbField(fieldName, paramList);
                            if (param != null)
                                ParseStatementParameter(insertStatement, param.ParameterOrder, fieldName, value.asText(), param);
                        }
                    }
                    try {
                        String rowIdValue = UUID.randomUUID().toString();
                        DatabaseTableParameter param = GetParamForDbField(SQLQueries.GET_ROWID_COLUMN_NAME(), paramList);
                        insertStatement.setString(param.ParameterOrder, rowIdValue);
                        insertStatement.execute();

                        mergeContent.setInt(1, Integer.parseInt(subscriber));
                        mergeContent.setString(2, rowIdValue);
                        mergeContent.setTimestamp(3, new java.sql.Timestamp(new Date().getTime()));
                        mergeContent.setInt(4, 1);//action
                        mergeContent.setInt(5, 0);//syncId
                        mergeContent.setBoolean(6, true);//record_has_changed
                        mergeContent.execute();
                    } catch (SQLException e) {
                        Logs.write(Logs.Level.ERROR, "PushInsertRecords()->Execute insert: " + e.getMessage() + "; " + insertStatement);
                    }
                }
            }
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "PushInsertRecords() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
    }

    private void PushUpdateRecords(JsonNode updates, String currentTable, String tableSchema) {
        SchemaGenerator schemaGen = new SchemaGenerator();
        String updateSQLQuery = schemaGen.CreateUpdateStatmentWithParams(currentTable, tableSchema);
        List<DatabaseTableParameter> paramList = schemaGen.GetStatmentParams(currentTable, false, tableSchema, 2);
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            PreparedStatement updateStatement = cn.prepareStatement(updateSQLQuery);
            if (updates.isArray()) {
                for (JsonNode node : updates) {
                    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        String fieldName = entry.getKey();
                        JsonNode value = entry.getValue();
                        DatabaseTableParameter param = GetParamForDbField(fieldName, paramList);
                        if (param != null)
                            ParseStatementParameter(updateStatement, param.ParameterOrder, fieldName, value.asText(), param);
                    }
                    updateStatement.addBatch();
                }

                if (!updates.isEmpty()) {
                    updateStatement.executeBatch();
                }
            }
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "PushUpdateRecords() " + currentTable + "," + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
    }

    private void PushDeletedRecords(JsonNode deletes, String schema) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            if (deletes.isArray()) {
                for (JsonNode node : deletes) {
                    String rowid = node.path("rowid").asText();
                    String tableId = node.path("table").asText();
                    String deleteQuery = "delete from " + schema + "." + tableId + " where rowid='" + rowid + "'";
                    try {
                        Statement deleteStatment = cn.createStatement();
                        deleteStatment.executeUpdate(deleteQuery);
                    } catch (SQLException e) {
                        Logs.write(Logs.Level.ERROR, "PushDeletedRecords() " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Logs.write(Logs.Level.ERROR, "PushDeletedRecords() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
    }

    private void ParseStatementParameter(PreparedStatement insertStatement, Integer colNumber, String colName, String colValue, Object paramDef) {

        if (colValue == null || colValue.equalsIgnoreCase("null"))
            colValue = "";

        DatabaseTableParameter colParamDef = ((DatabaseTableParameter) paramDef);
        DateFormat format = new SimpleDateFormat(SQLiteSyncConfig.DATE_FORMAT);
        DateFormat formatTimestamp = new SimpleDateFormat(SQLiteSyncConfig.TIMESTAMP_FORMAT);
        String colDbType = colParamDef.DbType.toLowerCase();

        try {
            switch (colDbType) {
                case "blob":
                case "longblob":
                case "mediumblob":
                case "varbinary":
                case "binary":
                case "varbinarymax":
                case "image":
                case "picture":
                case "byte[]":
                    byte[] byteData = colValue.getBytes(StandardCharsets.UTF_8);
                    Connection cn = Database.getInstance().GetDBConnection();
                    try {
                        Blob blobData = cn.createBlob();
                        blobData.setBytes(1, byteData);
                        insertStatement.setBlob(colNumber, blobData);
                    } finally {
                        JDBCCloser.close(cn);
                    }
                    break;
                case "bytea":
                    byte[] byteaData = colValue.getBytes(StandardCharsets.UTF_8);
                    insertStatement.setBytes(colNumber, byteaData);
                    break;
                case "longtext":
                case "varchar":
                case "varchar2":
                case "varcharmax":
                case "nvarchar":
                case "enum":
                case "mediumtext":
                case "text":
                case "char":
                case "string":
                case "geography":
                case "geometry":
                case "hierarchyid":
                case "nchar":
                case "ntext":
                case "nvarcharmax":
                case "userdefineddatatype":
                case "userdefinedtabletype":
                case "userdefinedtype":
                case "variant":
                case "xml":
                case "tinytext":
                    if (colValue == null || colValue.isEmpty() || colValue.trim() == "") {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.OTHER);
                        else
                            insertStatement.setString(colNumber, "");
                    } else
                        insertStatement.setString(colNumber, colValue);
                    break;
                case "boolean":
                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.BOOLEAN);
                        else
                            insertStatement.setBoolean(colNumber, false);
                    } else {
                        insertStatement.setBoolean(colNumber, colValue.equalsIgnoreCase("1") || colValue.equalsIgnoreCase("true"));
                    }
                    break;
                case "byte":
                case "tinyint":
                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.TINYINT);
                        else
                            insertStatement.setShort(colNumber, Short.parseShort("0"));
                    } else
                        insertStatement.setShort(colNumber, Short.parseShort(colValue));

                    break;
                case "smallint":
                case "bit":
                case "year":
                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.SMALLINT);
                        else
                            insertStatement.setInt(colNumber, Integer.parseInt("0"));
                    } else
                        insertStatement.setInt(colNumber, Integer.parseInt(colValue));
                    break;
                case "bigint":
                case "long":
                case "int64":
                case "serial":
                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.BIGINT);
                        else
                            insertStatement.setLong(colNumber, Long.parseLong("0"));
                    } else
                        insertStatement.setLong(colNumber, Long.parseLong(colValue));
                    break;
                case "mediumint":
                case "int":
                case "int16":
                case "int32":
                case "smalldatetime":
                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.INTEGER);
                        else
                            insertStatement.setInt(colNumber, Integer.parseInt("0"));
                    } else
                        insertStatement.setInt(colNumber, Integer.parseInt(colValue));
                    break;
                case "double":
                case "double precision":
                case "numeric":
                case "decimal":
                case "smallmoney":
                case "money":
                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.DOUBLE);
                        else
                            insertStatement.setDouble(colNumber, Double.parseDouble("0"));
                    } else
                        insertStatement.setDouble(colNumber, Double.parseDouble(colValue));
                    break;
                case "float":
                case "real":
                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.REAL);
                        else
                            insertStatement.setFloat(colNumber, Float.parseFloat("0"));
                    } else
                        insertStatement.setFloat(colNumber, Float.parseFloat(colValue));
                    break;
                case "time":
                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.TIME);
                        else
                            insertStatement.setTime(colNumber, Time.valueOf("00:00:00"));
                    } else
                        insertStatement.setTime(colNumber, Time.valueOf(colValue));
                    break;

                case "datetimeoffset":
                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.TIMESTAMP);
                        else
                            insertStatement.setTimestamp(colNumber, new Timestamp(System.currentTimeMillis()), cal);
                    } else {
                        OffsetDateTime offDt = OffsetDateTime.parse(colValue.split(Pattern.quote("+"))[0].trim().replace(" ", "T") + "+" + colValue.split(Pattern.quote("+"))[1], DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        insertStatement.setTimestamp(colNumber, Timestamp.valueOf(offDt.toLocalDateTime()));
                    }

                    break;
                case "datetime":
                case "datetime2":
                case "timestamp without time zone":
                case "timestamp with time zone":

                    if (colValue.length() == 10)
                        colValue = colValue + " 00:00:00";

                    Timestamp timestamp = new Timestamp(System.currentTimeMillis());

                    if (colValue != null && !colValue.isEmpty())
                        timestamp = new Timestamp(formatTimestamp.parse(colValue).getTime());

                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.DATE);
                        else
                            insertStatement.setTimestamp(colNumber, new java.sql.Timestamp(timestamp.getTime()));
                    } else {
                        insertStatement.setTimestamp(colNumber, new java.sql.Timestamp(timestamp.getTime()));
                    }
                    break;
                case "date":
                    Date date = new Date();
                    if (colValue != null && !colValue.isEmpty())
                        date = format.parse(colValue);

                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.DATE);
                        else
                            insertStatement.setDate(colNumber, new java.sql.Date(date.getTime()));
                    } else {
                        insertStatement.setDate(colNumber, new java.sql.Date(date.getTime()));
                    }
                    break;
                case "uuid":
                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.OTHER);
                        else
                            insertStatement.setObject(colNumber, UUID.randomUUID().toString(), Types.OTHER);
                    } else
                        insertStatement.setObject(colNumber, colValue, Types.OTHER);
                    break;
                case "uniqueidentifier":
                    if (colValue == null || colValue.isEmpty()) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.OTHER);
                        else
                            insertStatement.setString(colNumber, UUID.randomUUID().toString());
                    } else
                        insertStatement.setString(colNumber, colValue);
                    break;
                case "jsonb":
                    if (colValue == null || colValue.isEmpty() || !Helpers.isJSONValid(colValue)) {
                        if (colParamDef.IsNullable)
                            insertStatement.setNull(colNumber, Types.OTHER);
                        else
                            insertStatement.setObject(colNumber, null);
                    } else {
                        PGobject jsonObject = new PGobject();
                        jsonObject.setType("jsonb");
                        jsonObject.setValue(colValue);
                        insertStatement.setObject(colNumber, jsonObject);
                    }
                    break;
                default:
                    insertStatement.setString(colNumber, colValue);
                    break;
            }
        } catch (SQLException | ParseException e) {
            String recieveDataStatmentDesc = insertStatement.toString();
            Logs.write(Logs.Level.INFO, "ParseStatmentParameter()->" +colName +", ["+recieveDataStatmentDesc+"] ," + e.getMessage());
        }
    }

    private void SetDefaultsForParams(PreparedStatement insertStatement, String currentTable, List<DatabaseTableParameter> paramList) {
        for(DatabaseTableParameter parameter: paramList)
        {
            DatabaseTableParameter param = GetParamForDbField(parameter.ParameterName, paramList);
            ParseStatementParameter(insertStatement, param.ParameterOrder,  parameter.ParameterName, null, param);
        }
    }
}
