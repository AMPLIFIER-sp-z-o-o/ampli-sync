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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Stopwatch;
import org.postgresql.util.PGobject;

import javax.sql.rowset.CachedRowSet;
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

    public Integer syncIdForTestPurpose = -1;
    private final SQLQueries QUERIES = new SQLQueries();
    private final PullQueryBuilder pullQueryBuilder = new PullQueryBuilder();
    private final SyncSessionStore syncSessionStore = new SyncSessionStore();
    private final SyncSessionRepository syncSessionRepository = new SyncSessionRepository();
    private final SQLiteClientQueryBuilder sqLiteClientQueryBuilder = new SQLiteClientQueryBuilder();
    private final PullChangeEnumerator pullChangeEnumerator = new PullChangeEnumerator();

    public SyncService() {
        SQLiteSyncConfig.Load();
    }

    public String getChangesForTable(String subscriberUUID, String schema, String tableName, String deviceUUID) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<DataObject> dataToSync = new ArrayList<>();
        EnumeratedTableChanges changes = null;
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
                changes = enumerateChanges(reader.getString("tableid"), subscriberId, reader.getString("tablename"), tableSchema, tableFilter, subscriberUUID);
                if (changes.pullChangeSet().HasRows)
                    dataToSync.add(changes.tableSync());

            } else
                Logs.write(Logs.Level.INFO, "DoSync(). Table " + schema + "." + tableName + " was not found in MergeTablesToSync.");

            PullChangeSet changeSet = changes != null ? changes.pullChangeSet() : null;

            Integer syncId = StartNewSync(
                    subscriberId,
                    tableId,
                    tableSchema,
                    changeSet != null ? changeSet.Inserts : null,
                    changeSet != null ? changeSet.Updates : null,
                    changeSet != null ? changeSet.Deletes : null
            );

            this.syncIdForTestPurpose = syncId;
            for (DataObject obj : dataToSync) {
                obj.SyncId = syncId;
                obj.SQLiteSyncVersion = Database.SQLiteSyncVersion;
            }

            if (dataToSync.size() == 0) {
                DataObject emptySync = new DataObject();
                emptySync.SyncId = -1;
                dataToSync.add(emptySync);
                syncSessionRepository.finishSync(syncId.toString(), tableSchema);
            }

        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "DoSync() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }

        String syncResponse = serializeSyncResponse(dataToSync);
        Logs.write(Logs.Level.TRACE, syncResponse);

        stopwatch.stop();
        Long stopwatchMilisecs  = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        if (stopwatchMilisecs > 400)
            if (dataToSync.get(0).RowsCount.isEmpty())
                Logs.write(Logs.Level.WARN, "Getting changes for subscriber ["+deviceUUID+"]/[" + subscriberId + "] and table [" + tableName + "], no changes. Time elapsed: "+ stopwatchMilisecs);
            else
                Logs.write(Logs.Level.WARN, "Getting changes for subscriber ["+deviceUUID+"]/[" + subscriberId + "] and table [" + tableName + "], records count [" + dataToSync.get(0).RowsCount + "/" + dataToSync.get(0).MaxPackageSize + "]. Time elapsed: "+ stopwatchMilisecs);
        return syncResponse;
    }

    private String serializeSyncResponse(List<DataObject> dataToSync) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        StringWriter stringEmp = new StringWriter();

        try {
            objectMapper.writeValue(stringEmp, dataToSync);
        } catch (IOException ex) {
            Logs.write(Logs.Level.ERROR, "DoSync()->JSON Serialization " + ex.getMessage());
        }

        return stringEmp.toString();
    }


    public Integer StartNewSync(
            String subscriberId,
            Integer tableId,
            String schema,
            CachedRowSet inserts,
            CachedRowSet updates,
            CachedRowSet deletes
    ) {

        File theDir = new File(SQLiteSyncConfig.WORKING_DIR + "SyncData");
        if (!theDir.exists()) {
            try {
                theDir.mkdir();
            } catch (SecurityException e) {
                Logs.write(Logs.Level.ERROR, "StartNewSync()->Creating folder SyncData " + e.getMessage());
            }
        }

        Integer syncId = syncSessionRepository.startSync(subscriberId, tableId, schema);

        syncSessionStore.writeSyncData(syncId.toString(), inserts, updates, deletes);

        return syncId;
    }

    private void addSyncTriggers(DataObject tableSync, String tableName, String tableSchema) {
        if (tableName.equalsIgnoreCase("mergeidentity")) {
            return;
        }

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        DatabaseTable table = DatabaseTableGuavaCacheUtil.getTableUsingGuava(tableName, tableSchema);

        tableSync.TriggerInsert = "select 1;";
        tableSync.TriggerInsertDrop = "select 1;";
        tableSync.TriggerUpdate = schemaGenerator.CreateUpdateTrigger(table, schemaGenerator.GenerateUpdateableColumns(table.Columns));
        tableSync.TriggerUpdateDrop = "drop trigger if exists \"trmergeupdate_" + tableName + "\"";
        tableSync.TriggerDelete = schemaGenerator.CreateDeleteTrigger(table);
        tableSync.TriggerDeleteDrop = "drop trigger if exists \"trmergedelete_" + tableName + "\"";
    }

    private record PullFilter(String view, String changeDetectionCondition) {
    }

    private PullFilter buildPullFilter(String tableSchema, String tableName, String tableFilter, String subscriberUUID, String subscriberId) {
        String view = tableSchema + "." + tableName;

        if (tableSchema == null || tableSchema.isEmpty())
            view = tableName;

        String changeDetectionCondition = " ";

        if (tableFilter != null && tableFilter.trim().length() > 0) {
            view = tableFilter;

            if (view.startsWith("public.fn_") || view.startsWith("fn_")) {
                view = view.replace("@incomming_uniquename", subscriberUUID);
                changeDetectionCondition = " ";
            } else {
                changeDetectionCondition = "and vw.uniquename='" + subscriberUUID + "' ";
            }

            if (tableName.equalsIgnoreCase("mergeidentity"))
                changeDetectionCondition += " and tb.SubscriberId= " + subscriberId + " ";

            Logs.write(Logs.Level.TRACE, "Using filter [" + tableFilter + "] for table " + tableName);
        }

        return new PullFilter(view, changeDetectionCondition);
    }

    private record EnumeratedTableChanges(DataObject tableSync, PullChangeSet pullChangeSet) {
    }

    private EnumeratedTableChanges enumerateChanges(String tableId, String subscriberId, String tableName, String tableSchema, String tableFilter, String subscriberUUID) {
        DataObject tableSync = new DataObject();
        tableSync.TableName = tableName;
        tableSync.MaxPackageSize = SQLiteSyncConfig.PACKAGE_SIZE;

        sqLiteClientQueryBuilder.buildQueries(tableSync, tableSchema);

        PullFilter pullFilter = buildPullFilter(tableSchema, tableName, tableFilter, subscriberUUID, subscriberId);

        StringBuilder queryInserts = pullQueryBuilder.buildInsertChangesQuery(subscriberId, tableSchema, tableName, pullFilter.view(), pullFilter.changeDetectionCondition(), subscriberUUID);
        StringBuilder queryUpdates = pullQueryBuilder.buildUpdateChangesQuery(subscriberId, tableSchema, tableName, pullFilter.view(), pullFilter.changeDetectionCondition());
        StringBuilder queryDeletes = pullQueryBuilder.buildDeleteChangesQuery(subscriberId, tableSchema, tableName, pullFilter.view(), pullFilter.changeDetectionCondition(), subscriberUUID);

        addSyncTriggers(tableSync, tableName, tableSchema);

        PullChangeSet changeSet = pullChangeEnumerator.enumerate(queryInserts, queryUpdates, queryDeletes);

        tableSync.RowsCount = changeSet.RowsCount.toString();
        tableSync.Records = changeSet.Records;

        return new EnumeratedTableChanges(tableSync, changeSet);

    }

    public void CommitSync(String syncId, String schema) {
        CachedRowSet cachedDataInserts = syncSessionStore.readInserts(syncId);
        CachedRowSet cachedDataUpdates = syncSessionStore.readUpdates(syncId);
        CachedRowSet cachedDataDeletes = syncSessionStore.readDeletes(syncId);

        CommitSyncSession session = readCommitSyncSession(syncId, schema);

        updateSyncData(Integer.parseInt(syncId), schema, session, cachedDataInserts, cachedDataUpdates, cachedDataDeletes);

        syncSessionRepository.finishSync(syncId, schema);
    }

    private record CommitSyncSession(String tableName, int subscriberId) {
    }

    private CommitSyncSession readCommitSyncSession(String syncId, String schema) {
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

        return new CommitSyncSession(tableName, subscriberId);
    }

    private void updateSyncData(
            Integer syncId,
            String schema,
            CommitSyncSession session,
            CachedRowSet cachedDataInserts,
            CachedRowSet cachedDataUpdates,
            CachedRowSet cachedDataDeletes
    ) {
        updateInsertedSyncData(syncId, schema, session, cachedDataInserts);
        updateUpdatedSyncData(schema, session, cachedDataUpdates);
        updateDeletedSyncData(schema, session, cachedDataDeletes);
    }

    private void updateInsertedSyncData(Integer syncId, String schema, CommitSyncSession session, CachedRowSet cachedDataInserts) {
        if (cachedDataInserts != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdI = cn.prepareStatement(QUERIES.INSERT_MERGE_CONTENT(schema, session.tableName()));
                cachedDataInserts.beforeFirst();
                while (cachedDataInserts.next()) {
                    cmdI.setInt(1, session.subscriberId());
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

    }

    private void updateUpdatedSyncData(String schema, CommitSyncSession session, CachedRowSet cachedDataUpdates) {
        if (cachedDataUpdates != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdU = cn.prepareStatement(QUERIES.UPDATE_SYNC_DATA_UPDATE(schema, session.tableName()));

                cachedDataUpdates.beforeFirst();
                while (cachedDataUpdates.next()) {
                    cmdU.setString(1, cachedDataUpdates.getString("rowid").trim());
                    cmdU.setInt(2, session.subscriberId());
                    cmdU.addBatch();
                }
                cmdU.executeBatch();

            } catch (Exception e) {
                Logs.write(Logs.Level.ERROR, "UpdateSyncData()->updates " + e.getMessage());
            } finally {
                JDBCCloser.close(cn);
            }
        }
    }

    private void updateDeletedSyncData(String schema, CommitSyncSession session, CachedRowSet cachedDataDeletes) {
        if (cachedDataDeletes != null) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                PreparedStatement cmdD = cn.prepareStatement(QUERIES.UPDATE_SYNC_DATA_DELETE(schema, session.tableName()));
                cachedDataDeletes.beforeFirst();
                while (cachedDataDeletes.next()) {
                    cmdD.setString(1, cachedDataDeletes.getString(1));
                    cmdD.setInt(2, session.subscriberId());
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

        Integer syncId = startNewReception(subscriberId, receivedData, schema);

        commitChangesToDb(receivedData, schema, subscriberId);
        syncSessionRepository.finishSync(syncId.toString(), schema);
        Logs.write(Logs.Level.INFO, "Finished receiving data from subscriber " + subscriberUUID);
    }

    private Integer startNewReception(String subscriberId, ObjectNode data, String schema) {
        Integer syncId = syncSessionRepository.startSync(subscriberId, -1, schema);
        writeReceivedData(syncId, data);
        return syncId;
    }

    private void writeReceivedData(Integer syncId, ObjectNode data) {
        ensureReceivedDataDirectoryExists();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(receivedDataFile(syncId)))) {
            writer.write(data.toPrettyString());
        } catch (Exception e) {
            Logs.write(Logs.Level.ERROR, "StartNewReception() " + e.getMessage());
        }
    }

    private void ensureReceivedDataDirectoryExists() {
        File theDir = new File(SQLiteSyncConfig.WORKING_DIR + "ReceivedData");
        if (!theDir.exists()) {
            try {
                theDir.mkdir();
            } catch (SecurityException se) {
                Logs.write(Logs.Level.ERROR, "StartNewReception()->Creating folder ReceivedData " + se.getMessage());
            }
        }
    }

    private File receivedDataFile(Integer syncId) {
        return new File(SQLiteSyncConfig.WORKING_DIR + "ReceivedData/" + syncId + ".dat");
    }


    private void commitChangesToDb(ObjectNode data, String schema, String subscriberId) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Started collecting deleted records.");
            JsonNode deletes = data.path("deletes");
            pushDeletedRecords(deletes, schema);
            Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Finished collecting deleted records.");

            // collecting inserts
            JsonNode changes = data.path("changes");
            if (changes.isArray()) {
                List<String> orderedTableList = getOrderedTablesForInsert(cn, schema, changes);
                for (String tableName : orderedTableList)
                    commitTableChanges(changes, tableName, schema, subscriberId);
            }
        } catch (Exception e) {
            Logs.write(Logs.Level.ERROR, "CommitChangesToDb() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
    }

    private void commitTableChanges(JsonNode changes, String tableName, String schema, String subscriberId) {
        for (JsonNode change : changes) {
            if (tableName.equalsIgnoreCase(change.path("table").asText())) {
                Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Started collecting inserts for table " + tableName);
                JsonNode inserts = change.path("inserts");
                pushInsertRecords(inserts, subscriberId, tableName, schema);
                Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Finished collecting inserts for table " + tableName);
                // collecting updates
                JsonNode updates = change.path("updates");
                Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Started collecting updates.");
                pushUpdateRecords(updates, tableName, schema);
                Logs.write(Logs.Level.DEBUG, "CommitChangesToDb(). Finished collecting updates.");
            }
        }
    }

    private List<String> getOrderedTablesForInsert(Connection cn, String schema,
                                                   JsonNode changes) throws SQLException {
        PreparedStatement tablesOrder = cn.prepareStatement(QUERIES.INSERTATION_TABLES_ORDER(schema));
        ResultSet reader = tablesOrder.executeQuery();
        List<String> orderedTableList = new ArrayList<>();

        while (reader.next()) {
            String tableName =
                    reader.getString("table_name").toLowerCase().split("\\.")[1];
            orderedTableList.add(tableName);
        }

        for (JsonNode change : changes) {
            if (!orderedTableList.contains(change.path("table").asText()))
                orderedTableList.add(change.path("table").asText());
        }

        return orderedTableList;
    }

    private DatabaseTableParameter getParamForDbField(String colName, List<DatabaseTableParameter> paramList) {
        for (DatabaseTableParameter p : paramList)
            if (p.ParameterName.equalsIgnoreCase(colName))
                return p;
        return null;
    }

    private void pushInsertRecords(JsonNode inserts, String subscriber, String currentTable, String tableSchema) {
        SchemaGenerator schemaGen = new SchemaGenerator();
        String insertSQLQuery = schemaGen.CreateInsertStatementWithParams(currentTable, tableSchema);
        List<DatabaseTableParameter> paramList = schemaGen.GetStatmentParams(currentTable, true, tableSchema, 1);
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            cn.setAutoCommit(true);
            PreparedStatement insertStatement = cn.prepareStatement(insertSQLQuery);
            PreparedStatement mergeContent = cn.prepareStatement(QUERIES.INSERT_MERGE_CONTENT(tableSchema, currentTable));
            setDefaultsForParams(insertStatement, currentTable, paramList);

            if (inserts.isArray()) {
                for (JsonNode node : inserts) {
                    pushInsertRecord(node, subscriber, insertStatement, mergeContent,
                            paramList);
                }
            }
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "PushInsertRecords() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
    }

    private void pushInsertRecord(
            JsonNode node,
            String subscriber,
            PreparedStatement insertStatement,
            PreparedStatement mergeContent,
            List<DatabaseTableParameter> paramList
    ) {
        bindJsonFieldsToStatement(node, insertStatement, paramList, true);

        try {
            String rowIdValue = UUID.randomUUID().toString();
            DatabaseTableParameter param =
                    getParamForDbField(SQLQueries.GET_ROWID_COLUMN_NAME(), paramList);
            insertStatement.setString(param.ParameterOrder, rowIdValue);
            insertStatement.execute();

            mergeContent.setInt(1, Integer.parseInt(subscriber));
            mergeContent.setString(2, rowIdValue);
            mergeContent.setTimestamp(3, new java.sql.Timestamp(new Date().getTime()));
            mergeContent.setInt(4, 1);
            mergeContent.setInt(5, 0);
            mergeContent.setBoolean(6, true);
            mergeContent.execute();
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "PushInsertRecords()->Execute insert: " +
                    e.getMessage() + "; " + insertStatement);
        }
    }

    private void pushUpdateRecords(JsonNode updates, String currentTable, String tableSchema) {
        SchemaGenerator schemaGen = new SchemaGenerator();
        String updateSQLQuery = schemaGen.CreateUpdateStatmentWithParams(currentTable, tableSchema);
        List<DatabaseTableParameter> paramList = schemaGen.GetStatmentParams(currentTable, false, tableSchema, 2);
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            PreparedStatement updateStatement = cn.prepareStatement(updateSQLQuery);
            if (updates.isArray()) {
                for (JsonNode node : updates) {
                    bindJsonFieldsToStatement(node, updateStatement, paramList, false);
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


    private void bindJsonFieldsToStatement(
            JsonNode node,
            PreparedStatement statement,
            List<DatabaseTableParameter> paramList,
            boolean skipRowId
    ) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode value = entry.getValue();

            if (!skipRowId || !fieldName.equalsIgnoreCase(SQLQueries.GET_ROWID_COLUMN_NAME())) {
                DatabaseTableParameter param = getParamForDbField(fieldName, paramList);
                if (param != null)
                    parseStatementParameter(statement, param.ParameterOrder,
                            fieldName, value.asText(), param);
            }
        }
    }

    private void pushDeletedRecords(JsonNode deletes, String schema) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            if (deletes.isArray()) {
                for (JsonNode node : deletes) {
                    pushDeletedRecord(cn, node, schema);
                }
            }
        } catch (Exception e) {
            Logs.write(Logs.Level.ERROR, "PushDeletedRecords() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
    }

    private void pushDeletedRecord(Connection cn, JsonNode node, String schema) {
        String rowid = node.path("rowid").asText();
        String tableId = node.path("table").asText();
        String deleteQuery = "delete from " + schema + "." + tableId + " where rowid='" + rowid + "'";

        try {
            Statement deleteStatement = cn.createStatement();
            deleteStatement.executeUpdate(deleteQuery);
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "PushDeletedRecords() " + e.getMessage());
        }
    }

    private void parseStatementParameter(PreparedStatement insertStatement, Integer colNumber, String colName, String colValue, Object paramDef) {

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
            String receivedDataStatementDesc = insertStatement.toString();
            Logs.write(Logs.Level.INFO, "parseStatementParameter()->" + colName + ", [" + receivedDataStatementDesc + "] ," + e.getMessage());
        }
    }

    private void setDefaultsForParams(PreparedStatement insertStatement, String currentTable, List<DatabaseTableParameter> paramList) {
        for (DatabaseTableParameter parameter : paramList) {
            DatabaseTableParameter param = getParamForDbField(parameter.ParameterName, paramList);
            parseStatementParameter(insertStatement, param.ParameterOrder, parameter.ParameterName, null, param);
        }
    }
}
