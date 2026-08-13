package com.ampliapps.amplisync.SyncServer.SchemaPublish;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;
import com.ampliapps.amplisync.SyncServer.CommonTools;
import com.ampliapps.amplisync.SyncServer.Helpers;
import com.ampliapps.amplisync.SyncServer.Synchronization.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.*;
import java.util.*;

import static com.ampliapps.amplisync.SyncServer.Synchronization.Database.SQLiteSyncVersion;

public class SchemaGenerator {

    private SQLQueries QUERIES = new SQLQueries();
    Map<String, String> schema = new HashMap<>();
    String _subscriber;
    String _tableId;

    private int schemaOrder = 0;

    public SchemaGenerator(){

    }

    private String GetSchemaOrder() {
        String schemaOrderString = String.valueOf(schemaOrder);

        for (int i = schemaOrderString.length(); i < 5; i++)
            schemaOrderString = "0" + schemaOrderString;

        schemaOrder++;
        return schemaOrderString + " ";
    }

    public String GetFullSchematScript(String subscriberUUID, String deviceUUID) {
        CommonTools common = new CommonTools();
        String userSchema = UserSchemaGuavaCacheUtil.getUserSchemaUsingGuava(subscriberUUID);
        Logs.write(Logs.Level.INFO, "GetFullSchematScript subscriberUUID " + subscriberUUID + ", deviceUUID " + deviceUUID);
        String subscriber = common.CheckIfSubscriberExists(subscriberUUID, deviceUUID).toString();

        if(subscriber.equalsIgnoreCase("-1")){
            Logs.write(Logs.Level.ERROR, "Error creating new subscriber for UUID " + subscriberUUID);
            return "Error creating new subscriber for UUID " + subscriberUUID;
        }

        Logs.write(Logs.Level.INFO, "Reinitializing subscriber " + subscriber);
        schema.put(this.GetSchemaOrder() + "SQLiteSync.com version", SQLiteSyncVersion);
        _subscriber = subscriber;
        addMainObjects();
        GenerateTableSchema("mergeidentity", userSchema);
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            Statement tableToPublish = cn.createStatement();
            ResultSet reader = tableToPublish.executeQuery(QUERIES.GET_MERGE_TABLES_TO_SYNC(userSchema));
            while (reader.next())
                if (!reader.getString("TableName").equalsIgnoreCase("mergeidentity")) {
                    _tableId = (reader.getString("TableId"));
                    String tableSchema = reader.getString("TableSchema");
                    String tableName = reader.getString("TableName");
                    GenerateTableSchema(tableName.toLowerCase(), tableSchema);
                    DeleteOldRecordsFromMergeContent(subscriber, tableName, tableSchema);
                }

            DeleteOldRecordsFromMergeContent(subscriber, "mergeidentity", userSchema);
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "GetFullSchematScript() " + e.getMessage());
        }
        finally {
            JDBCCloser.close(cn);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        StringWriter stringEmp = new StringWriter();
        try {
            objectMapper.writeValue(stringEmp, schema);
        } catch (IOException ex) {
            Logs.write(Logs.Level.ERROR, "GetFullSchematScript()->JSON serialize " + ex.getMessage());
        }

        Logs.write(Logs.Level.INFO, "Finished reinitializing subscriber " + subscriber);
        Logs.write(Logs.Level.TRACE, "Reinitialization script " + stringEmp.toString());
        return stringEmp.toString();
    }

    private void DeleteOldRecordsFromMergeContent(String subscriber, String tableName, String schema) {
        Connection cnAct = Database.getInstance().GetDBConnection();
        try {
            PreparedStatement clearMergeContent = cnAct.prepareStatement(QUERIES.CLEAR_MERGE_CONTENT_BY_SUBSCRIBER(schema, tableName));
            clearMergeContent.setInt(1, Integer.parseInt(subscriber));
            clearMergeContent.execute();
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "DeleteOldRecordsFromMergeContent() " + e.getMessage());
        }
        finally {
            JDBCCloser.close(cnAct);
        }
    }

    private void addMainObjects() {
        schema.put(this.GetSchemaOrder() + "mergedelete drop", "drop table if exists mergedelete;");
        schema.put(this.GetSchemaOrder() + "mergedelete create", "create table \"mergedelete\" ( \"tableid\" text, \"rowid\" text );");
    }

    private void GenerateTableSchema(String tableName, String tableSchema) {
        StringBuilder schemaTmp = new StringBuilder();
        DatabaseTable table = DatabaseTableGuavaCacheUtil.getTableUsingGuava(tableName, tableSchema);

        schema.put(this.GetSchemaOrder() + tableName + " drop", "drop table if exists \"" + tableName + "\"");

        schemaTmp.append("create table \"" + tableName + "\" (");
        StringBuilder columns = new StringBuilder();
        StringBuilder triggerInsert = new StringBuilder();
        StringBuilder triggerUpdate = new StringBuilder();
        StringBuilder triggerDelete = new StringBuilder();

        if(!tableName.equalsIgnoreCase("mergeidentity")) {
            triggerUpdate.append(CreateUpdateTrigger(table, GenerateUpdateableColumns(table.Columns)));
            triggerDelete.append(CreateDeleteTrigger(table));
        }

        for (DatabaseTableColumn column : table.Columns)
            if (!column.Name.equalsIgnoreCase("mergeinsertsource")) {
                columns.append("[" + column.Name.toLowerCase() + "] ");
                columns.append(Helpers.TypeConvertionTable(column.DataTypeName) + " ");

                if (!column.AllowDBNull && !column.IsInPrimaryKey)
                    columns.append("not null ");

                if (column.DefaultValue != null && !column.DefaultValue.isEmpty())
                    columns.append(" " + BuildDefaultValue(column) + " ");

                columns.append(",");
            }

        columns.append("\"mergeupdate\" ");
        columns.append(" integer ");
        columns.append("not null ");
        columns.append("default (0) ");
        columns.append(",");

        schemaTmp.append(columns.toString().substring(0, columns.toString().length() - 1));
        schemaTmp.append(");");
        schema.put(this.GetSchemaOrder() + tableName, schemaTmp.toString());
        if (triggerInsert.toString().trim().length() > 0)
            schema.put(this.GetSchemaOrder() + tableName + "_mergeinsert", triggerInsert.toString());
        if (triggerUpdate.toString().trim().length() > 0)
            schema.put(this.GetSchemaOrder() + tableName + "_mergeupdate", triggerUpdate.toString());
        if (triggerDelete.toString().trim().length() > 0)
            schema.put(this.GetSchemaOrder() + tableName + "_mergedelete", triggerDelete.toString());

        //indexes
        if (!tableName.equalsIgnoreCase("mergeidentity")) {
            for (DatabaseTableIndex idx : table.Indexes) {
                schemaTmp = new StringBuilder();
                schemaTmp.append("create ");
                if (idx.IsUnique)
                    schemaTmp.append("unique ");

                schemaTmp.append("index ");
                if (idx.Name.equalsIgnoreCase("primary"))
                    schemaTmp.append("\"pk_" + tableName + "_" + Helpers.RemoveSpecialChars(idx.Name) + "\" on ");
                else
                    schemaTmp.append("\"" + Helpers.RemoveSpecialChars(idx.Name) + "\" on ");
                schemaTmp.append("\"" + tableName + "\" (");

                StringBuilder indexedColumns = new StringBuilder();

                for (String col : idx.Columns) {
                    indexedColumns.append("[" + col.trim().toLowerCase() + "] asc,");
                }

                schemaTmp.append(indexedColumns.toString().substring(0, indexedColumns.toString().length() - 1));
                schemaTmp.append(");");

                schema.put(this.GetSchemaOrder() + tableSchema + "_" + tableName + "_" + Helpers.RemoveSpecialChars(idx.Name), schemaTmp.toString());
            }

            schema.put(this.GetSchemaOrder() + tableName + "_mergeupdate_index", "create index \"" + tableName + "_mergeupdateindex\" on \"" + tableName + "\" (mergeupdate asc);");
        } else {
            schema.put(this.GetSchemaOrder() + "mergeidentity_index", "create index \"mergeidentity_pk_index\" on \"" + tableName + "\" (tableid asc, subscriberid asc, identitycurrent asc, rev asc)");
        }

        for (DatabaseTableColumn column : table.Columns)
            if(column.IsInPrimaryKey)
                schema.put(this.GetSchemaOrder() + "pk index", "create unique index if not exists \""+tableName+"_pk_index\" on \"" + tableName + "\" ("+column.Name.toLowerCase() +")");

    }

    private String BuildDefaultValue(DatabaseTableColumn column) {
        String defaultValue = "";
        if(column.DataTypeName.equalsIgnoreCase("uniqueidentifier"))
            return "";
        switch(Helpers.TypeConvertionTable(column.DataTypeName)){
            case "BLOB":
                break;
            case "TEXT":
                defaultValue = "DEFAULT ('" + column.DefaultValue + "')";
                break;
            case "INTEGER":
                defaultValue = "DEFAULT (" + column.DefaultValue + ")";
                break;
            case "REAL":
                defaultValue = "DEFAULT (" + column.DefaultValue + ")";
                break;
            case "DATE":
            case "DATETIME":
                defaultValue = "DEFAULT (" + column.DefaultValue + ")";
                break;

        }
        return defaultValue;
    }

    public String CreateDeleteTrigger(DatabaseTable table) {
        StringBuilder trigger = new StringBuilder();

        trigger.append(" create trigger if not exists \"trmergedelete_" + table.Name.toLowerCase() + "\" ");
        trigger.append("    after delete ");
        trigger.append("    on \"" + table.Name.toLowerCase() + "\"  ");
        trigger.append(" begin 	 ");

        if (table.ReadOnly)
            trigger.append(" 	select raise(abort, 'table " + table.Name.toLowerCase() + " is readonly.'); ");
        else
            trigger.append(" 	insert into mergedelete values ('" + table.Name.toLowerCase() + "',  old.rowid); ");

        trigger.append(" end; ");

        return trigger.toString();
    }

    public String CreateUpdateTrigger(DatabaseTable table, String updateableColumns) {
        StringBuilder trigger = new StringBuilder();

        trigger.append(" create trigger if not exists \"trmergeupdate_" + table.Name.toLowerCase() + "\" ");
        trigger.append("    after update of ");
        trigger.append(updateableColumns);
        trigger.append("    on \"" + table.Name.toLowerCase() + "\" ");
        trigger.append(" begin ");

        if (table.ReadOnly)
            trigger.append(" 	select raise(abort, 'table " + table.Name.toLowerCase() + " is readonly.'); ");
        else
            trigger.append(" 	update \"" + table.Name.toLowerCase() + "\" set mergeupdate = mergeupdate + 1 where rowid = old." + SQLQueries.GET_ROWID_COLUMN_NAME() + "; ");

        trigger.append(" end; ");

        return trigger.toString();
    }

    public String CreateInsertStatementWithParams(String tableName, String tableSchema) {
        DatabaseTable table = DatabaseTableGuavaCacheUtil.getTableUsingGuava(tableName, tableSchema);

        StringBuilder insert = new StringBuilder();

        insert.append("INSERT INTO " + tableSchema + "." + table.Name + " ");

        insert.append(" (");
        for (DatabaseTableColumn col : table.Columns) {
            insert.append("\"" + col.Name + "\",");
        }
        String tmp = insert.toString().substring(0, insert.toString().length() - 1);
        insert = new StringBuilder();
        insert.append(tmp);
        insert.append(") VALUES (");

        for (DatabaseTableColumn col : table.Columns) {
            insert.append("?,");
        }
        tmp = insert.toString().substring(0, insert.toString().length() - 1);
        insert = new StringBuilder();
        insert.append(tmp);
        insert.append(") ");
        insert.append(" ON CONFLICT (id) DO UPDATE ");

        insert.append(" SET ");
        for (DatabaseTableColumn col : table.Columns)
            if(!col.Name.equalsIgnoreCase("rowid") && !col.Name.equalsIgnoreCase("id")) {
                insert.append("\"" + col.Name + "\"=EXCLUDED.\"" + col.Name + "\",");
            }
        tmp = insert.toString().substring(0, insert.toString().length() - 1);
        insert = new StringBuilder();
        insert.append(tmp);
        insert.append(";");
        return insert.toString().toLowerCase();
    }

    public String CreateUpdateStatmentWithParams(String tableName, String tableSchema) {
        DatabaseTable table = DatabaseTableGuavaCacheUtil.getTableUsingGuava(tableName, tableSchema);
        StringBuilder update = new StringBuilder();
        update.append("UPDATE " + tableSchema + "." + table.Name + " SET ");

        for (DatabaseTableColumn col : table.Columns)
            if(!col.IsInPrimaryKey && !col.Name.equalsIgnoreCase(SQLQueries.GET_ROWID_COLUMN_NAME()) && !col.Name.equalsIgnoreCase("MergeInsertSource")) {
                update.append("\"" + col.Name + "\"=?,");
            }

        String tmp = update.toString().substring(0, update.toString().length() - 1);
        update = new StringBuilder();
        update.append(tmp);
        update.append(" WHERE "+ SQLQueries.GET_ROWID_COLUMN_NAME() +"=?");

        return update.toString().toLowerCase();
    }

    public List<DatabaseTableParameter> GetStatmentParams(String tableName, Boolean withIdentity, String tableSchema, Integer action) {
        DatabaseTable table = DatabaseTableGuavaCacheUtil.getTableUsingGuava(tableName, tableSchema);
        List<DatabaseTableParameter> paramsList = new ArrayList<>();
        Integer order = 0;
        if(action == 2) {
            for (Integer i = 0; i < table.Columns.size(); i++) {
                if (!table.Columns.get(i).Name.equalsIgnoreCase("RowId"))
                    if ((!table.Columns.get(i).IsAutoIncrement && !table.Columns.get(i).IsInPrimaryKey) || withIdentity) {
                        DatabaseTableParameter param = new DatabaseTableParameter();
                        param.IsNullable = table.Columns.get(i).AllowDBNull;
                        param.ParameterName = table.Columns.get(i).Name;
                        param.ParameterOrder = order + 1;
                        order++;
                        param.DbType = table.Columns.get(i).SqlDataTypeName.toLowerCase();//(DbType) Enum.Parse(typeof(DbType), Helpers.GetSqlDbType(table.Columns[i].SqlDataTypeName.toLowerCase()));
                        paramsList.add(param);
                    }
            }
            for (Integer i = 0; i < table.Columns.size(); i++)
                if (table.Columns.get(i).Name.equalsIgnoreCase("RowId")) {
                    DatabaseTableParameter param = new DatabaseTableParameter();
                    param.IsNullable = table.Columns.get(i).AllowDBNull;
                    param.ParameterName = table.Columns.get(i).Name;
                    param.ParameterOrder = order + 1;
                    order++;
                    param.DbType = table.Columns.get(i).SqlDataTypeName.toLowerCase();//(DbType) Enum.Parse(typeof(DbType), Helpers.GetSqlDbType(table.Columns[i].SqlDataTypeName.toLowerCase()));
                    paramsList.add(param);

                }
        } else {
            for (Integer i = 0; i < table.Columns.size(); i++) {
                if ((!table.Columns.get(i).IsAutoIncrement && !table.Columns.get(i).IsInPrimaryKey) || withIdentity) {
                    DatabaseTableParameter param = new DatabaseTableParameter();
                    param.IsNullable = table.Columns.get(i).AllowDBNull;
                    param.ParameterName = table.Columns.get(i).Name;
                    param.ParameterOrder = order + 1;
                    order++;
                    param.DbType = table.Columns.get(i).SqlDataTypeName.toLowerCase();//(DbType) Enum.Parse(typeof(DbType), Helpers.GetSqlDbType(table.Columns[i].SqlDataTypeName.toLowerCase()));
                    paramsList.add(param);
                }
            }
        }

        return paramsList;
    }

    public String GenerateUpdateableColumns(List<DatabaseTableColumn> columns) {
        String updateableColumns = "";
        for (DatabaseTableColumn column : columns)
            if (!column.IsInPrimaryKey && !column.Name.equalsIgnoreCase("mergeupdate") && !column.Name.equalsIgnoreCase(SQLQueries.GET_ROWID_COLUMN_NAME()))
                updateableColumns += "[" + column.Name + "],";
        if (updateableColumns.endsWith(","))
            updateableColumns = updateableColumns.substring(0, updateableColumns.length() - 1);

        return updateableColumns.toLowerCase();
    }
}
