package com.ampliapps.amplisync.SyncServer.Synchronization;

final class SQLiteClientQueryBuilder {
    public void buildQueries(DataObject tableSync, String tableSchema) {
        String tableNameClear = tableSync.TableName;

        DatabaseTable table = DatabaseTableGuavaCacheUtil.getTableUsingGuava(tableNameClear, tableSchema);

        StringBuilder insertStatement = new StringBuilder();
        StringBuilder updateStatement = new StringBuilder();

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

        updateStatement.append("update " + tableNameClear + " set ");
        for (DatabaseTableColumn col : table.Columns)
            if (!col.Name.equalsIgnoreCase("mergeinsertsource")) {
                if (!col.IsInPrimaryKey) {
                    updateStatement.append("[" + col.Name + "]");
                    updateStatement.append("=?,");
                }
            }

        updateStatement = new StringBuilder(updateStatement.substring(0, updateStatement.toString().length() - 1));

        updateStatement.append(" where ");

        if (table.PrimaryKeyColumns.size() > 0) {
            for (String pk : table.PrimaryKeyColumns) {
                updateStatement.append(pk);
                updateStatement.append("=? and ");
            }

            tableSync.QueryUpdate = updateStatement.substring(0, updateStatement.toString().length() - 5) + ";";
        } else {
            updateStatement.append(SQLQueries.GET_ROWID_COLUMN_NAME() + "=?");
            tableSync.QueryUpdate = updateStatement + ";";
        }

        tableSync.QueryDelete = "delete from " + tableNameClear + " where " + SQLQueries.GET_ROWID_COLUMN_NAME() + "=";
    }
}
