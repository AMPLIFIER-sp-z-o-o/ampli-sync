package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.Logs;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


public class DatabaseTableGuavaCacheUtil {
    private static LoadingCache<String, DatabaseTable> databaseTablesCache;
    static {
        databaseTablesCache = CacheBuilder.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .build(
                        new CacheLoader<String, DatabaseTable>() {
                            @Override
                            public DatabaseTable load(String key) throws Exception {
                                //key == schema.table_name
                                String tableName = key;
                                String schema = "";
                                if(key.contains("__s__")) {
                                    String[] tmp = key.split("__s__");
                                    schema = tmp[0];
                                    tableName = tmp[1];
                                }
                                return new DatabaseTable(tableName, schema);
                            }
                        }
                );
    }
    private static LoadingCache<String, DatabaseTable> getLoadingCache() {
        return databaseTablesCache;
    }

    public static DatabaseTable getTableUsingGuava(String tableName, String schema) {
        try {
            LoadingCache<String, DatabaseTable> tablesCache = DatabaseTableGuavaCacheUtil.getLoadingCache();
            String key = tableName;
            if (schema != null && !schema.isEmpty())
                key = schema + "__s__" + tableName;
            return tablesCache.get(key);
        } catch (ExecutionException e){
            Logs.write(Logs.Level.ERROR, "getTableUsingGuava() " + e.getMessage());
            return new DatabaseTable(tableName, schema);
        }
    }

    public static void clearCache(){
        databaseTablesCache.invalidateAll();
    }
}
