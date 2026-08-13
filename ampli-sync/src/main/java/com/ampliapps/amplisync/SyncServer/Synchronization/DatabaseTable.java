package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;
import com.ampliapps.amplisync.SyncServer.CommonTools;
import com.ampliapps.amplisync.SyncServer.Helpers;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseTable {

    private SQLQueries QUERIES = new SQLQueries();
    public String Name = null;
    public String Schema = null;
    public Boolean ReadOnly = false;
    public List<DatabaseTableColumn> Columns = new ArrayList<DatabaseTableColumn>();
    public List<DatabaseTableIndex> Indexes = new ArrayList<DatabaseTableIndex>();
    public List<String> PrimaryKeyColumns = new ArrayList<String>();

    public DatabaseTable(String _tableName, String _schema) {
        Name = _tableName;
        Schema = _schema;
        if (Name != null && !Name.isEmpty()) {
            BuildTableFromPostgreSQL();
        }
    }

    private void BuildTableFromPostgreSQL() {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            if (cn == null)
                return;

            Statement getColumns = cn.createStatement();
            String getColumnsQuery = "select * from information_schema.columns where LOWER(table_name)='" + this.Name.toLowerCase() + "' and table_schema ='" + this.Schema.toLowerCase() + "' order by table_name,ordinal_position ";

            ResultSet reader = getColumns.executeQuery(getColumnsQuery);

            while (reader.next()) {
                DatabaseTableColumn column = new DatabaseTableColumn();
                column.Name = reader.getString("column_name");
                if (reader.getString("is_nullable").equals("YES"))
                    column.AllowDBNull = true;
                else
                    column.AllowDBNull = false;

                if(column.Name.equalsIgnoreCase("rowid"))
                    column.AllowDBNull = true;

                String data_type = reader.getString("data_type");
                if (data_type.equalsIgnoreCase("numeric") && reader.getString("numeric_precision") == null && reader.getString("numeric_scale") == null)
                    data_type = "integer";
                column.DataTypeName = data_type;
                column.DefaultValue = reader.getString("column_default");

                if(column.DefaultValue != null && column.DefaultValue.equalsIgnoreCase("CURRENT_DATE"))
                    column.DefaultValue = "datetime('now', 'localtime')";

                if (column.DefaultValue != null && column.DefaultValue.equalsIgnoreCase("CURRENT_TIMESTAMP"))
                    column.DefaultValue = "datetime('now', 'localtime')";


                if(column.DefaultValue != null && column.DefaultValue.equalsIgnoreCase("uuid_generate_v4()"))
                    column.DefaultValue = "";

                if(column.DefaultValue != null && column.DefaultValue.isEmpty() && column.DefaultValue.equalsIgnoreCase("numeric"))
                    column.DefaultValue = "0";

                if (column.Name.equalsIgnoreCase("id") && column.DataTypeName.equalsIgnoreCase("uuid")) {
                    column.IsAutoIncrement = false;
                    column.IsInPrimaryKey = true;
                }
                else {
                    column.IsAutoIncrement = false;
                    column.IsInPrimaryKey = false;
                }

                if (column.IsInPrimaryKey)
                    this.PrimaryKeyColumns.add(column.Name);

                if (this.Name.toLowerCase().equalsIgnoreCase("mergeidentity") && column.Name.toLowerCase().equalsIgnoreCase("id"))
                    column.DefaultValue = "";

                column.SqlDataTypeName = Helpers.GetSqlDbType(reader.getString("data_type"));
                if (reader.getString("data_type").equalsIgnoreCase("numeric") && reader.getString("numeric_precision") == null && reader.getString("numeric_scale") == null)
                    column.SqlDataTypeName = "Int32";
                this.Columns.add(column);
            }

            reader.close();

            Statement getIndexes = cn.createStatement();
            String indexesQuery = "select\n" +
                    "    t.relname as table_name,\n" +
                    "    i.relname as index_name,\n" +
                    "    array_to_string(array_agg(a.attname), ', ') as column_names\n" +
                    "from\n" +
                    "    pg_class t,\n" +
                    "    pg_class i,\n" +
                    "    pg_index ix,\n" +
                    "    pg_attribute a\n" +
                    "where\n" +
                    "    t.oid = ix.indrelid\n" +
                    "    and i.oid = ix.indexrelid\n" +
                    "    and a.attrelid = t.oid\n" +
                    "    and a.attnum = ANY(ix.indkey)\n" +
                    "    and t.relkind = 'r'\n" +
                    "\tand i.relname not like '%_pkey'\n" +
                    "    and t.relname like '" +this.Name +
                    "'\n" +
                    "group by\n" +
                    "    t.relname,\n" +
                    "    i.relname\n" +
                    "order by\n" +
                    "    t.relname,\n" +
                    "    i.relname;";

            reader = getIndexes.executeQuery(indexesQuery);

            while (reader.next()) {
                DatabaseTableIndex index = new DatabaseTableIndex();
                index.IsUnique = false;
                index.Name = reader.getString("index_name");
                String[] column_names = reader.getString("column_names").split(",");
                for (String column : column_names) {
                    index.Columns.add(column);
                }
                this.Indexes.add(index);
            }
            reader.close();

            CheckReadOnlyOption(this.Schema);
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "BuildTableFromPostgreSQL() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
    }

    private void CheckReadOnlyOption(String schema) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {

            CommonTools commonTools = new CommonTools();

            if (commonTools.IsMergeTablesToSyncExists(schema)) {

                PreparedStatement readOnlyPS = cn.prepareStatement(QUERIES.DO_SYNC_GET_TABLE(schema));

                readOnlyPS.setString(1, Name);
                readOnlyPS.setString(2, Schema);

                ResultSet readOnlyRS = readOnlyPS.executeQuery();
                while (readOnlyRS.next()) {
                    Short readOnly = readOnlyRS.getShort("ReadOnly");
                    if (readOnly == 1)
                        ReadOnly = true;
                }
                readOnlyRS.close();
            }

        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "CheckReadOnlyOption() " + e.getMessage());
        }
        finally {
            JDBCCloser.close(cn);
        }
    }

}
