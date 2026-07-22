package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.SQLiteSyncConfig;

final class PullQueryBuilder {
    public StringBuilder buildInsertChangesQuery(String subscriberId, String tableSchema, String tableName, String filterVW, String filterVW_CD, String subscriberUUID) {
        StringBuilder query = new StringBuilder();
        String topLimit = "";
        if (SQLiteSyncConfig.PACKAGE_SIZE != null && !SQLiteSyncConfig.PACKAGE_SIZE.isEmpty())
            topLimit = "LIMIT " + SQLiteSyncConfig.PACKAGE_SIZE;

        query.append("select distinct ");
        query.append("tb.*");
        query.append("from " + tableSchema + "." + tableName + " tb ");
        query.append("join (");
        query.append("select vw.rowid ");
        if (filterVW_CD.trim().length() > 0 || filterVW.startsWith("public.fn_") || filterVW.startsWith("fn_")) {
            query.append("from " + tableSchema + "." + filterVW + " vw ");
        } else {
            query.append("from " + tableSchema + "." + tableName + " vw ");
        }
        query.append("where ");
        query.append("not exists (select 1 from  " + tableSchema + ".MergeContent_" + tableName + " t where vw.rowid=t.rowid and t.SubscriberId=" + subscriberId + ") ");
        if (!filterVW.startsWith(tableSchema + ".fn_") && !filterVW.startsWith("fn_"))
            if (filterVW_CD.trim().length() > 0)
                query.append("and vw.uniquename='"+subscriberUUID+"'");
        query.append(" " + topLimit + "");
        query.append(") inserts on tb.rowid = inserts.rowid ");
        if (tableName.equalsIgnoreCase("mergeidentity"))
            query.append("and tb.subscriberid =" + subscriberId);

        return query;
    }

    public StringBuilder buildUpdateChangesQuery(String subscriberId, String tableSchema, String tableName, String filterVW, String filterVW_CD) {
        StringBuilder query = new StringBuilder();
        String topLimit = "";
        if (SQLiteSyncConfig.PACKAGE_SIZE != null && !SQLiteSyncConfig.PACKAGE_SIZE.isEmpty())
            topLimit = "limit " + SQLiteSyncConfig.PACKAGE_SIZE;

        query.append("select distinct ");
        query.append("tb.*");
        query.append("from " + tableSchema + "." + tableName + " tb ");
        if (filterVW_CD.trim().length() > 0 || filterVW.startsWith("public.fn_") || filterVW.startsWith("fn_")) {
            query.append("join " + tableSchema + "." + filterVW + " vw on tb.rowid=vw.rowid ");
            query.append("join " + tableSchema + ".mergecontent_" + tableName + " t on vw.rowid=t.rowid ");
        } else {
            query.append("join " + tableSchema + ".mergecontent_" + tableName + " t on tb.rowid=t.rowid ");
        }

        query.append("where t.record_has_changed=true and t.SubscriberId=" + subscriberId + " " + filterVW_CD + " ");
        if (tableName.equalsIgnoreCase("mergeidentity"))
            query.append(" and tb.subscriberid =" + subscriberId);
        query.append(" " + topLimit + ";");

        return query;
    }

    public StringBuilder buildDeleteChangesQuery(String subscriberId, String tableSchema, String tableName, String filterVW, String filterVW_CD, String subscriberUUID) {
        StringBuilder query = new StringBuilder();
        query.append("select  ");
        query.append("m.rowid ");
        query.append("from " + tableSchema + ".mergecontent_" + tableName + " m ");
        if (filterVW_CD.trim().length() > 0 || filterVW.startsWith(tableSchema + ".fn_") || filterVW.startsWith("fn_")) {
            if (tableName.equalsIgnoreCase("mergeidentity"))
                query.append("left join " + tableSchema + "." + filterVW + " vw on m.rowid=vw.rowid and vw.uniquename='" + subscriberUUID + "' and vw.subscriberid =" + subscriberId + " ");
            else
                query.append("left join " + tableSchema + "." + filterVW + " vw on m.rowid=vw.rowid " + filterVW_CD + " ");
            query.append("where vw.rowid is null  and m.subscriberid=" + subscriberId);

        } else {
            query.append("left join " + tableSchema + "." + tableName + " vw on m.rowid=vw.rowid ");
            query.append("where vw.rowid is null and m.subscriberid=" + subscriberId );
        }

        return query;
    }
}
