package com.ampliapps.amplisync.SyncServer.Synchronization;

public class SQLQueries {


    public String GET_MERGE_TABLES_TO_SYNC(String schema) {
        return  "select * from "+schema+".mergetablestosync";
    }

    public String DO_SYNC_GET_TABLE(String schema) {
        return "select * from " + schema + ".mergetablestosync where LOWER(TableName)=LOWER(?) and LOWER(TableSchema)=LOWER(?)";

    }

    public String START_NEW_SYNC(String schema) {
        return  "insert into " + schema + ".mergesync (SubscriberId, SyncObject, TableId) values (?,?,?);";// SELECT LAST_INSERT_ID();;
    }
    public String COMMIT_SYNC(String schema) {
        return "select s.tableid, s.subscriberid, t.tablename  from " + schema + ".mergesync s " +
                "join " + schema + ".mergetablestosync t on s.tableid=t.tableid " +
                "where s.syncid=?";
    }

    public String COMMIT_SYNC_UPDATE(String schema) {
        return "update " + schema + ".mergesync set SyncEnd=CURRENT_TIMESTAMP where SyncId=?";
    }

    public String UPDATE_SYNC_DATA_UPDATE(String schema,String tableName) {
        return "update " + schema + ".MergeContent_" + tableName + " set record_has_changed=false, Action=2, ChangeDate=CURRENT_TIMESTAMP where RowId=? and SubscriberId=?";
    }

    public String UPDATE_SYNC_DATA_DELETE(String schema, String tableName) {
        return "delete from " + schema + ".MergeContent_" + tableName + " where RowId=? and SubscriberId=?";
    }
    public String INSERT_MERGE_CONTENT(String schema, String tableName) {
        return "insert into " + schema + ".mergecontent_" + tableName + " (subscriberid,rowid,changedate,action,syncid,record_has_changed) values (?,?,?,?,?,?)";
    }
    public String TABLES_LIST(String schema) {
        return "SELECT * FROM information_schema.tables where table_schema='" + schema + "' and table_type='BASE TABLE' ORDER BY table_schema,table_name";
    }

    public String INSERT_SUBSCRIBER(String schema) {
        return "INSERT INTO " + schema + ".mergesubscribers(Name,UniqueName,deviceuuid,isenabled)VALUES(?,?,?,1)";
    }

    public String UPDATE_SUBSCRIBER(String schema) {
        return "update " + schema + ".mergesubscribers set Name=?,UniqueName=? where subscriberId=?";
    }

    public String CLEAR_MERGE_CONTENT_BY_SUBSCRIBER(String schema, String tableName) {
        return "delete from " + schema + ".mergecontent_" + tableName + " where SubscriberId=?";
    }

    public static String GET_ROWID_COLUMN_NAME() {
        return "rowid";
    }

    public String GET_SCHEMA_CHANGES() {
        String val = "select * from mergetablesschemaupdates\n" +
                        "where id not in (select schemaupdateid from mergetablesschemaupdateresults where subscriberid=?)\n" +
                        "and (\n" +
                        "\tsubscriberid = -1 or\n" +
                        "\t(subscriberid > 0 and subscriberid=? )\n" +
                        ") and enabled=true";
        return val;
    }

    public String INSERTATION_TABLES_ORDER(String schema) {
        String val = "WITH RECURSIVE fkeys AS (\n" +
                "   /* source and target tables for all foreign keys */\n" +
                "   SELECT conrelid AS source,\n" +
                "          confrelid AS target\n" +
                "   FROM pg_constraint\n" +
                "   WHERE contype = 'f'\n" +
                "),\n" +
                "tables AS (\n" +
                "      (   /* all tables ... */\n" +
                "          SELECT oid AS table_name,\n" +
                "                 1 AS level,\n" +
                "                 ARRAY[oid] AS trail,\n" +
                "                 FALSE AS circular\n" +
                "          FROM pg_class\n" +
                "          WHERE relkind = 'r'\n" +
                "            AND NOT relnamespace::regnamespace::text LIKE ANY\n" +
                "                    (ARRAY['pg_catalog', 'information_schema', 'pg_temp_%'])\n" +
                "       EXCEPT\n" +
                "          /* ... except the ones that have a foreign key */\n" +
                "          SELECT source,\n" +
                "                 1,\n" +
                "                 ARRAY[ source ],\n" +
                "                 FALSE\n" +
                "          FROM fkeys\n" +
                "      )\n" +
                "   UNION ALL\n" +
                "      /* all tables with a foreign key pointing a table in the working set */\n" +
                "      SELECT fkeys.source,\n" +
                "             tables.level + 1,\n" +
                "             tables.trail || fkeys.source,\n" +
                "             tables.trail @> ARRAY[fkeys.source]\n" +
                "      FROM fkeys\n" +
                "         JOIN tables ON tables.table_name = fkeys.target\n" +
                "      /*\n" +
                "       * Stop when a table appears in the trail the third time.\n" +
                "       * This way, we get the table once with \"circular = TRUE\".\n" +
                "       */\n" +
                "      WHERE cardinality(array_positions(tables.trail, fkeys.source)) < 2\n" +
                "),\n" +
                "ordered_tables AS (\n" +
                "   /* get the highest level per table */\n" +
                "   SELECT DISTINCT ON (table_name)\n" +
                "          table_name,\n" +
                "          level,\n" +
                "          circular\n" +
                "   FROM tables\n" +
                "   ORDER BY table_name, level DESC\n" +
                ")\n" +
                "SELECT table_name::regclass,\n" +
                "       level\n" +
                "FROM ordered_tables\n" +
                "WHERE NOT circular and table_name::regclass::varchar(150) like '" + schema + "%' and table_name::regclass::varchar(150) not like '%merge%' and table_name::regclass::varchar(150) not like '%django%'\n" +
                "ORDER BY level, table_name;";
        return val;
    }
}
