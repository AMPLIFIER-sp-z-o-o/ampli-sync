package com.ampliapps.amplisync.SyncServer.Synchronization;

public class SQLiteClientQueryBuilder {
    public void buildQueries(DataObject tableSync, String tableSchema) {
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
}
