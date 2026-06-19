package com.ampliapps.amplisync.SyncServer;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;
import com.ampliapps.amplisync.SyncServer.Synchronization.*;
import com.ampliapps.amplisync.SyncServer.Synchronization.Database;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;


public class CommonTools {

    private SQLQueries QUERIES = new SQLQueries();

    public CommonTools() {

    }

    public List<String> GetTableList(String schema) {
        List<String> tablesList = new ArrayList<>();
        Connection cn = Database.getInstance().GetDBConnection();
        try {

            Statement tableToPublish = cn.createStatement();
            ResultSet reader = tableToPublish.executeQuery(QUERIES.TABLES_LIST(schema));
            while (reader.next()) {
                String tableName = reader.getString("TABLE_NAME");
                if (!reader.wasNull() && !tableName.isEmpty()) {
                    tablesList.add(tableName);
                }
            }
            reader.close();

            return tablesList;
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "GetTableList() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
            return tablesList;
        }
    }

    public Boolean IsMergeTablesToSyncExists(String schema) {
        List<String> tablesList = GetTableList(schema);
        for (String tb : tablesList)
            if (tb.equalsIgnoreCase("MergeTablesToSync"))
                return true;

        return false;
    }

    public Boolean IsTableAddedToSynchronization(String schema, String tableName) {
        if (!IsMergeTablesToSyncExists(schema))
            return false;

        Connection cn = Database.getInstance().GetDBConnection();
        try {
            String query = QUERIES.DO_SYNC_GET_TABLE(schema);

            PreparedStatement tableToPublish = cn.prepareStatement(query);

            String[] tmp = tableName.split(Pattern.quote("."));
            String name = tableName;
            if (tmp.length > 1) {
                schema = tableName.replace("." + tmp[tmp.length - 1], "");
                name = tmp[tmp.length - 1];
            }

            tableToPublish.setString(1, name);
            tableToPublish.setString(2, schema);

            ResultSet reader = tableToPublish.executeQuery();
            Boolean tableAlreadyAddedToSynchronization = false;
            if (reader.next())
                tableAlreadyAddedToSynchronization = true;

            reader.close();
            return tableAlreadyAddedToSynchronization;

        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "IsTableAddedToSynchronization() " + e.getMessage());
            return false;
        }
        finally {
            JDBCCloser.close(cn);
        }
    }

    public void CreateSQLiteSynDatabaseObjects(String schema) {
        Boolean sqlitesyncObjectsAreThere = false;
        Connection cn = Database.getInstance().GetDBConnection();
        try {

            Statement tableToPublish = cn.createStatement();
            ResultSet reader = tableToPublish.executeQuery(QUERIES.TABLES_LIST(schema));
            while (reader.next())
                if (reader.getString("TABLE_NAME").endsWith("MergeTablesToSync"))
                    sqlitesyncObjectsAreThere = true;

            reader.close();

            if (!sqlitesyncObjectsAreThere) {
                String query = "";
                String queryResource = "";
                queryResource = "/POSTGRESQL_CREATE_SCRIPT.sql";
                InputStream in = getClass().getResourceAsStream(queryResource);
                BufferedReader readerResources = new BufferedReader(new InputStreamReader(in));
                query = org.apache.commons.io.IOUtils.toString(readerResources);
                query = query.replace('\n',' ');
                query = query.replace("{$table_schema}", schema);
                this.ImportSQL(query);
            }
        } catch (IOException | SQLException e) {
            Logs.write(Logs.Level.ERROR, "CreateSQLiteSynDatabaseObjects() " + e.getMessage());
        }
        finally {
            JDBCCloser.close(cn);
        }
    }

    public void DropSQLiteSynDatabaseObjects() {
        Connection cn = Database.getInstance().GetDBConnection();
        try {

            String query = "";
            String queryResource = "";

            queryResource = "/POSTGRESQL_DROP_SCRIPT.sql";

            InputStream in = getClass().getResourceAsStream(queryResource);
            BufferedReader readerResources = new BufferedReader(new InputStreamReader(in));
            query = org.apache.commons.io.IOUtils.toString(readerResources);
            this.ImportSQL(query);
        } catch (IOException | SQLException e) {
            Logs.write(Logs.Level.ERROR, "DropSQLiteSynDatabaseObjects() " + e.getMessage());
        }
        finally {
            JDBCCloser.close(cn);
        }
    }

    public Integer CheckIfSubscriberExists(String subscriberUUID, String deviceUUID) {
        Integer subscriberId = GetSubscriber(subscriberUUID, deviceUUID.toLowerCase());
        if (subscriberId == -1) {
            String schema = UserSchemaGuavaCacheUtil.getUserSchemaUsingGuava(subscriberUUID);
            SaveSubscriber(null, deviceUUID.toLowerCase(), subscriberUUID, schema);
            subscriberId = GetSubscriber(subscriberUUID, deviceUUID.toLowerCase());
        }

        return subscriberId;
    }

    public int GetSynchronizedTablesCount(String userSchema) {
        int count = 0;
        if (IsMergeTablesToSyncExists(userSchema)) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {

                Statement tableToPublish = cn.createStatement();
                ResultSet reader = tableToPublish.executeQuery(QUERIES.GET_MERGE_TABLES_TO_SYNC(userSchema));
                while (reader.next())
                    if (!reader.getString("TableName").equalsIgnoreCase("MergeIdentity"))
                        count++;

                reader.close();

            } catch (SQLException e) {
                Logs.write(Logs.Level.ERROR, "GetSynchronizedTablesCount() " + e.getMessage());
            }
            finally {
                JDBCCloser.close(cn);
            }
        }
        return count;
    }

    public Map<Integer, String> GetSynchronizedTables(String schema) {
        Map<Integer, String> tables = new HashMap<>();
        Connection cn = Database.getInstance().GetDBConnection();
        try {

            Statement tableToPublish = cn.createStatement();
            ResultSet reader = tableToPublish.executeQuery(QUERIES.GET_MERGE_TABLES_TO_SYNC(schema));
            while (reader.next()) {
                String tableNameWithSchema = reader.getString("TableName");
                String tableSchema = reader.getString("TableSchema");
                if (!reader.wasNull() && !tableSchema.isEmpty())
                    tableNameWithSchema = tableSchema + "." + reader.getString("TableName");
                tables.put(reader.getInt("TableId"), tableNameWithSchema);
            }
            reader.close();
            tableToPublish.close();
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "GetSynchronizedTables() " + e.getMessage());
        }
        finally {
            JDBCCloser.close(cn);
        }

        return tables;
    }

    public void AddTableToSynchronization(String schema, String tableName) {

        if (IsTableAddedToSynchronization(schema, tableName)) {
            Logs.write(Logs.Level.INFO, "AddTableToSynchronization() table " + tableName + " already added.");
            return;
        }

        if (tableName.trim().length() > 0) {
            String[] tmp = tableName.split(Pattern.quote("."));
            String name = tableName;
            if (tmp.length > 1) {
                schema = tableName.replace("." + tmp[tmp.length - 1], "");
                name = tmp[tmp.length - 1];
            }
            String primaryKeyColumnName = "";
            DatabaseTable table = DatabaseTableGuavaCacheUtil.getTableUsingGuava(name, schema);

            for (DatabaseTableColumn col : table.Columns)
                if (col.IsInPrimaryKey)
                    primaryKeyColumnName = col.Name;

            String queryResource = "/POSTGRESQL_ADD_TABLE.sql";
            if(primaryKeyColumnName.isEmpty())
                primaryKeyColumnName = SQLQueries.GET_ROWID_COLUMN_NAME();
            GenerateAndExecuteSchemaChanges(schema, name, primaryKeyColumnName, queryResource);
        }
    }

    private void GenerateAndExecuteSchemaChanges(String schema, String name, String primaryKeyColumnName, String queryResource) {
        String query = "";
        String queries = "";
        InputStream in = getClass().getResourceAsStream(queryResource);
        BufferedReader readerResources = new BufferedReader(new InputStreamReader(in));

        try {
            query = org.apache.commons.io.IOUtils.toString(readerResources);
        } catch (IOException e) {
            Logs.write(Logs.Level.ERROR, "GenerateAndExecuteSchemaChanges() " + e.getMessage());
        }

        queries = query.replace("{$table_schema}", schema).replace("{$table_name}", name).replace("{$table_pk}", primaryKeyColumnName);//.replace('\n', ' ');

        Connection cn = Database.getInstance().GetDBConnection();
        try {
            this.ImportSQL(queries);
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "GenerateAndExecuteSchemaChanges() " + e.getMessage());
        }
        finally {
            JDBCCloser.close(cn);
        }

    }

    public Integer GetSubscriber(String subscriberUUID, String deviceUUID) {
        Integer subscriberId = -1;
        String schema = UserSchemaGuavaCacheUtil.getUserSchemaUsingGuava(subscriberUUID);
        if (IsMergeTablesToSyncExists(schema)) {
            Connection cn = Database.getInstance().GetDBConnection();
            try {
                String query = "select * from " + schema + ".mergesubscribers where uniquename=? and deviceuuid=?";
                PreparedStatement cmd = cn.prepareStatement(query);
                cmd.setString(1, subscriberUUID);
                cmd.setString(2, deviceUUID);
                ResultSet reader = cmd.executeQuery();

                while (reader.next()) {
                    subscriberId = reader.getInt("SubscriberId");
                }
                reader.close();
            } catch (SQLException e) {
                Logs.write(Logs.Level.ERROR, "GetSubscribers() " + e.getMessage());
            }
            finally {
                JDBCCloser.close(cn);
            }
        }

        return subscriberId;
    }

    public void SaveSubscriber(String SubscriberId, String deviceUUID, String name, String schema) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            String query = "";

            if (SubscriberId == null || SubscriberId.isEmpty())
                query = QUERIES.INSERT_SUBSCRIBER(schema);
            else
                query = QUERIES.UPDATE_SUBSCRIBER(schema);

            PreparedStatement cmd = cn.prepareStatement(query);

            cmd.setString(1, name);
            cmd.setString(2, name);
            cmd.setString(3, deviceUUID);
            if (SubscriberId !=  null && !SubscriberId.isEmpty())
                cmd.setInt(3, Integer.parseInt(SubscriberId));

            cmd.execute();
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "SaveSubscriber() " + e.getMessage());
        }
        finally {
            JDBCCloser.close(cn);
        }
    }

    private void ImportSQL(String in) throws SQLException {
        Connection conn = Database.getInstance().GetDBConnection();
        try {
            String[] scanner = in.split("kol_sc");
            Statement currentStatement = null;
            for (String query : scanner) {
                Logs.write(Logs.Level.DEBUG, query.trim() + ";");
                String rawStatement = query;
                try {
                    currentStatement = conn.createStatement();
                    currentStatement.execute(rawStatement);
                } catch (SQLException e) {
                    Logs.write(Logs.Level.ERROR, "ImportSQL() " + e.getMessage());
                } finally {
                    if (currentStatement != null) {
                        try {
                            currentStatement.close();
                        } catch (SQLException e) {
                            Logs.write(Logs.Level.ERROR, "ImportSQL()->currentStatement.close()" + e.getMessage());
                        }
                    }
                    currentStatement = null;
                }
            }
        }
        finally {
            JDBCCloser.close(conn);
        }
    }

    public static void DeleteFoldersOlderThanNdays(long days, String dirPath) {

        File folder = new File(dirPath);

        if (folder.exists()) {
            File[] listFiles = folder.listFiles();
            long eligibleForDeletion = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000);
            for (File listFile : listFiles) {
                if (listFile.isDirectory() &&
                        listFile.lastModified() < eligibleForDeletion) {
                    try {
                        FileUtils.deleteDirectory(listFile);
                    } catch (Exception ex){
                        Logs.write(Logs.Level.ERROR, "DeleteFoldersOlderThanNdays: Sorry Unable to Delete Files. " + ex.getMessage());
                    }
                }
            }
        }
    }

    public Boolean CheckIfDBConnectionIsOK(){
        Connection cn = Database.getInstance().GetDBConnection();
        try{
            if(cn == null)
                return false;
            else
                return true;
        }
        finally {
            JDBCCloser.close(cn);
        }
    }

    public String GetVersionOfSQLiteSyncCOM() {
        return Database.SQLiteSyncVersion;
    }

    public void InitSync(String schema){
        AddTableToSynchronization(schema, "document_headers");
        AddTableToSynchronization(schema, "demo_customers");
        AddTableToSynchronization(schema, "demo_products");
        AddTableToSynchronization(schema, "demo_orders");
        AddTableToSynchronization(schema, "demo_order_items");
    }


    public void AddFileToZip(File zipFile, File fileToAdd) {
        try {
            OutputStream archiveStream = new FileOutputStream(zipFile);
            ArchiveOutputStream archive = new ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.ZIP, archiveStream);
            ZipArchiveEntry entry = new ZipArchiveEntry(fileToAdd.getName());
            archive.putArchiveEntry(entry);
            BufferedInputStream input = new BufferedInputStream(new FileInputStream(fileToAdd));
            IOUtils.copy(input, archive);
            archive.closeArchiveEntry();
            archive.finish();
        } catch (Exception e) {
            Logs.write(Logs.Level.ERROR, "addFilesToZip: " + e.getMessage());
        }
    }
}
