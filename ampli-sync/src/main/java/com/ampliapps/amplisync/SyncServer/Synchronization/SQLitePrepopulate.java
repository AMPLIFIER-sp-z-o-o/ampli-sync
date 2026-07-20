package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;
import com.ampliapps.amplisync.SQLiteSyncConfig;
import com.ampliapps.amplisync.SyncServer.CommonTools;
import com.ampliapps.amplisync.SyncServer.SchemaPublish.SchemaGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.rowset.RowSetProvider;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.regex.Pattern;
import javax.sql.rowset.CachedRowSet;

public class SQLitePrepopulate {

    private SQLQueries QUERIES = new SQLQueries();
    String dbTempFolderName = UUID.randomUUID().toString();
    private CachedRowSet tablesData = null;
    Connection connSqliteLocal = null;
    private Integer tablePackageCount = 1;

    private Connection SQLiteConnection(String deviceUUID) {

        Connection conn = null;
        try {
            Class.forName("org.sqlite.JDBC").newInstance();
            String connString = String.format("jdbc:sqlite::memory:");
            conn = DriverManager.getConnection(connString);
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | SQLException ex) {
            Logs.write(Logs.Level.ERROR, "SQLiteConnection() " + ex.getMessage());
        }

        return conn;

    }

    public String PrepopulateDatabase(String subscriberUUID, String deviceUUID){
        Logs.write(Logs.Level.INFO, "PrepopulateDatabase subscriberUUID " + subscriberUUID + ", deviceUUID " + deviceUUID);
        String path = SQLiteSyncConfig.WORKING_DIR + "sqlite-databases";
        String userSchema = UserSchemaGuavaCacheUtil.getUserSchemaUsingGuava(subscriberUUID);
        CommonTools.DeleteFoldersOlderThanNdays(SQLiteSyncConfig.HISTORY_DAYS, path);
        File theDir = new File(path);
        if (!theDir.exists()) {
            try{
                theDir.mkdir();
            }
            catch(SecurityException se){
                Logs.write(Logs.Level.ERROR, "PrepopulateDatabase()->Creating folder sqlite-databases " + se.getMessage());
            }
        }

        File theTempDir = new File(SQLiteSyncConfig.WORKING_DIR + "sqlite-databases/" + dbTempFolderName);
        if (!theTempDir.exists()) {
            try{
                theTempDir.mkdir();
            }
            catch(SecurityException se){
                Logs.write(Logs.Level.ERROR, "PrepopulateDatabase()->Creating temp in folder sqlite-databases " + se.getMessage());
            }
        }

        CommonTools commonTools = new CommonTools();
        if (commonTools.GetSynchronizedTablesCount(userSchema) == 0) {
            commonTools.CreateSQLiteSynDatabaseObjects(userSchema);
        }
        commonTools.InitSync(userSchema);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        String dbSchema = schemaGenerator.GetFullSchematScript(subscriberUUID, deviceUUID);
        CreateEmptyDatabase(deviceUUID, dbSchema);
        Map<Integer, String> tablesList = commonTools.GetSynchronizedTables(userSchema);
        for(Map.Entry<Integer, String> entry : tablesList.entrySet())
            PopulateTable(subscriberUUID, entry.getValue(), deviceUUID);

        Statement stmt = null;
        String dbFilePath = String.format("%1$ssqlite-databases/%2$s/amperflow.db", SQLiteSyncConfig.WORKING_DIR, dbTempFolderName);
        try {
            stmt = connSqliteLocal.createStatement();
            stmt.executeUpdate("backup to " + dbFilePath);
            stmt.close();
        } catch (Exception e) {
            Logs.write(Logs.Level.ERROR, "PrepopulateDatabase()->backup to " + e.getMessage());
        }

        if(connSqliteLocal != null)
            JDBCCloser.close(connSqliteLocal);

        File sqliteDb = new File(dbFilePath);
        commonTools.AddFileToZip(new File(dbFilePath + ".zip"), sqliteDb);
        sqliteDb.delete();
        return dbFilePath + ".zip";
    }

    private void CreateEmptyDatabase(String deviceUUID, String json) {
        Map<String, String> schema = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            schema = mapper.readValue(json, Map.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            mapper = null;
        }

        connSqliteLocal = SQLiteConnection(deviceUUID);
        try
        {
            Statement statement = connSqliteLocal.createStatement();

            SortedSet<String> keys = new TreeSet<>(schema.keySet());
            for (String key : keys) {
                if(!key.startsWith("00000")) {
                    String value = schema.get(key);
                    statement.execute(value);
                }
            }
        }
        catch(SQLException e)
        {
            Logs.write(Logs.Level.ERROR, "CreateEmptyDatabase() " + e.getMessage());
        }
    }

    public void PopulateTable(String subscriberUUID, String table, String deviceUUID) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            CommonTools common = new CommonTools();
            String subscriberId = common.CheckIfSubscriberExists(subscriberUUID, deviceUUID).toString();
            String userSchema = UserSchemaGuavaCacheUtil.getUserSchemaUsingGuava(subscriberUUID);

            if(subscriberId.equalsIgnoreCase("-1")){
                Logs.write(Logs.Level.ERROR, "Error creating new subscriber for UUID " + subscriberUUID);
            }

            String tableSchema = "";

            String[] tmp = table.split(Pattern.quote("."));
            String name = table;
            if (tmp.length > 1) {
                name = tmp[tmp.length - 1];
            }
            String query = QUERIES.DO_SYNC_GET_TABLE(userSchema);
            PreparedStatement tableToPublish = cn.prepareStatement(query);
            tableToPublish.setString(1, name);
            tableToPublish.setString(2, userSchema);

            ResultSet reader = tableToPublish.executeQuery();
            if (reader.next()) {
                tableSchema = reader.getString("TableSchema");
                Logs.write(Logs.Level.INFO, "PrepopulateDatabase->table "+ subscriberUUID +"/" + reader.getString("TableName"));
                tablePackageCount = 1;
                String tableFilter = reader.getString("TableFilter");
                EnumerateChanges(reader.getString("TableId"), subscriberId, reader.getString("TableName"), tableSchema, tableFilter, subscriberUUID);
                Logs.write(Logs.Level.INFO, "PrepopulateDatabase->table " + subscriberUUID + "/" + reader.getString("TableName") + " done");
            } else
                Logs.write(Logs.Level.TRACE, "DoSync(). Table " + userSchema + "." + table + " was not found in MergeTablesToSync.");

        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "DoSync() " + e.getMessage());
        }
        finally {
            JDBCCloser.close(cn);
        }
    }

    private record PrepopulateFilter(String view, String changeDetectionCondition) {
    }

    private void EnumerateChanges(String tableId, String subscriberId, String tableName, String tableSchema, String tableFilter, String subscriberUUID) {
        SyncService syncService = new SyncService();

        PrepopulateFilter filter = buildPrepopulateFilter(
                tableSchema,
                tableName,
                tableFilter,
                subscriberUUID,
                subscriberId
        );

        StringBuilder query = BuildMergeQuery(
                tableId,
                subscriberId,
                tableName,
                tableSchema,
                filter.view(),
                filter.changeDetectionCondition()
        );

        SchemaGenerator schemaGenerator = new SchemaGenerator();

        Connection cn = Database.getInstance().GetDBConnection();
        try {
            Statement cmd = cn.createStatement();

            DatabaseTable table = DatabaseTableGuavaCacheUtil.getTableUsingGuava(tableName, tableSchema);

            dropPrepopulateTriggers(tableName);

            String queryInsert = buildPrepopulateInsertQuery(table, tableName);

            PreparedStatement insert = connSqliteLocal.prepareStatement(queryInsert);
            Integer batchCount = 0;
            boolean hasResults = cmd.execute(query.toString());

            do {
                if (hasResults) {
                    try (ResultSet rs = cmd.getResultSet()) {
                        tablesData = RowSetProvider.newFactory().createCachedRowSet();
                        tablesData.populate(rs);
                        tablesData.beforeFirst();
                        while (tablesData.next()) {
                            Integer MergeContent_Action = tablesData.getInt("mergecontent_action");
                            if (MergeContent_Action != 3) {
                                Date d = tablesData.getDate("mergecontent_changedate");
                                if (MergeContent_Action == -1 && tablesData.wasNull())
                                    MergeContent_Action = 1;
                                else
                                    MergeContent_Action = 2;
                            }

                            switch (MergeContent_Action) {
                                case 1://insert
                                    Integer param = 1;
                                    for (DatabaseTableColumn column : table.Columns) {
                                        String columnName = column.Name;
                                        String value = tablesData.getString(columnName);

                                        if (!columnName.equalsIgnoreCase("mergeinsertsource") && !columnName.toLowerCase().contains("mergecontent_".toLowerCase())) {
                                            switch (column.DataTypeName)
                                            {
                                                case "timestamp":
                                                case "datetime2":
                                                case "datetime":
                                                case "date":
                                                    if(value != null && value.length() > 10) {
                                                        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
                                                        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                                                        Date parsed = parser.parse(value);
                                                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                                        value = formatter.format(parsed);
                                                    }
                                                    break;
                                                case "boolean":
                                                    if(tablesData.getBoolean(columnName))
                                                        value = "1";
                                                    else
                                                        value = "0";
                                                    break;
                                            }
                                            insert.setString(param, value);
                                            param++;
                                        }
                                    }
                                    insert.addBatch();
                                    batchCount++;
                                    if(batchCount == 100){
                                        batchCount = 0;
                                        insert.executeBatch();
                                    }
                                    break;
                            }
                        }

                    } catch (Exception e) {
                        Logs.write(Logs.Level.ERROR, "EnumerateChanges()->record iterate: "+ tableName+". " + e.getMessage());
                    }
                }

                hasResults = cmd.getMoreResults();

            } while (hasResults || cmd.getUpdateCount() != -1);


            if(batchCount > 0 && batchCount < 100) {
                insert.executeBatch();
            }

            createPrepopulateTriggers(schemaGenerator, table, tableName);

            Integer syncId = syncService.StartNewSync(
                    subscriberId,
                    Integer.parseInt(tableId),
                    tableSchema,
                    tablesData,
                    null,
                    null
            );
            syncService.CommitSync(syncId.toString(), tableSchema);
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "EnumerateChanges(): " + tableName + ". " + e.getMessage());
        }
        finally {
            JDBCCloser.close(cn);
            if(tablesData.size() > 0 && tablesData.size() == 5000 && tablePackageCount < 13) {
                EnumerateChanges(tableId, subscriberId, tableName, tableSchema, tableFilter, subscriberUUID);
            }
        }
    }

    private void dropPrepopulateTriggers(String tableName) throws SQLException {
        Statement trDelete = connSqliteLocal.createStatement();
        if (!tableName.equalsIgnoreCase("mergeidentity")) {
            trDelete.addBatch("drop trigger if exists \"trMergeInsert_" + tableName + "\"");
            trDelete.addBatch("drop trigger if exists \"trMergeUpdate_" + tableName + "\"");
            trDelete.addBatch("drop trigger if exists \"trMergeDelete_" + tableName + "\"");
            trDelete.executeBatch();
        }
    }
    private void createPrepopulateTriggers(SchemaGenerator schemaGenerator, DatabaseTable table, String tableName) throws SQLException {
        Statement trCreate = connSqliteLocal.createStatement();
        if (!tableName.equalsIgnoreCase("MergeIdentity")) {
            String q = schemaGenerator.CreateUpdateTrigger(table, schemaGenerator.GenerateUpdateableColumns(table.Columns));
            if(q.trim().length() > 0)
                trCreate.addBatch(q);

            q = schemaGenerator.CreateDeleteTrigger(table);
            if(q.trim().length() > 0)
                trCreate.addBatch(q);

            trCreate.executeBatch();
        }
    }

    private PrepopulateFilter buildPrepopulateFilter(
            String tableSchema,
            String tableName,
            String tableFilter,
            String subscriberUUID,
            String subscriberId
    ) {
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
                changeDetectionCondition += " and vw.SubscriberId= " + subscriberId + " ";

            Logs.write(Logs.Level.TRACE, "Using filter [" + tableFilter + "] for table " + tableName);
        }

        return new PrepopulateFilter(view, changeDetectionCondition);
    }

    private String buildPrepopulateInsertQuery(DatabaseTable table, String tableName) {
        StringBuilder columns = new StringBuilder();

        columns.append("insert or ignore into " + tableName + " (");
        for (DatabaseTableColumn col : table.Columns)
            if (!col.Name.equalsIgnoreCase("mergeinsertsource")) {
                columns.append("[" + col.Name + "]");
                columns.append(",");
            }

        String queryInsert = columns.toString().substring(0, columns.toString().length() - 1) + ") values (";

        StringBuilder values = new StringBuilder();
        for (DatabaseTableColumn col : table.Columns)
            if (!col.Name.equalsIgnoreCase("mergeinsertsource")) {
                values.append("?");
                values.append(",");
            }

        queryInsert += values.toString().substring(0, values.toString().length() - 1) + ");";
        return queryInsert;
    }

    public StringBuilder BuildMergeQuery(String tableId, String subscriberId, String tableName, String tableSchema, String filterVW, String filterVW_CD) {
        StringBuilder query = new StringBuilder();
        String topLimit = "LIMIT 5000";

        query.append("with inserts as ( ");
        query.append("select vw.rowid ");
        if(filterVW_CD.trim().length() > 0 || filterVW.startsWith("public.fn_") || filterVW.startsWith("fn_")) {
            query.append("from " + tableSchema + "." + filterVW + " vw ");
        } else {
            query.append("from " + tableSchema + "." + tableName + " vw ");
        }
        query.append("where ");
        query.append("not exists (select 1 from  " + tableSchema + ".mergecontent_" + tableName + " t where vw.rowid=t.rowid and t.subscriberId=" + subscriberId + ") ");
        if(filterVW_CD.trim().length() > 0)
            query.append(" " + filterVW_CD);
        query.append(" " + topLimit + "");
        query.append(") ");
        query.append("select distinct ");
        query.append("tb.*," + tableId + " as MergeContent_TableId,   ");
        query.append(subscriberId + " as MergeContent_SubscriberId,  ");
        query.append("tb.rowid as MergeContent_rowid,  ");
        query.append("null as MergeContent_ChangeDate, ");
        query.append("-1 as MergeContent_Action,   ");
        query.append("null as MergeContent_SyncId   ");
        query.append("from " + tableSchema + "." + tableName + " tb ");
        query.append("join inserts on tb.rowid = inserts.rowid ");
        if(tableName.equalsIgnoreCase("mergeidentity"))
            query.append("and tb.subscriberid =" + subscriberId);

        return query;
    }
}
