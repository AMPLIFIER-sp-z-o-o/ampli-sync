package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.sql.*;
import java.util.concurrent.TimeUnit;


public class UserSchemaGuavaCacheUtil {
    private static LoadingCache<String, String> userSchemasCache;
    static {
        userSchemasCache = CacheBuilder.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .build(
                        new CacheLoader<String, String>() {
                            @Override
                            public String load(String key) throws Exception {
                                String query = "select schema_name from public.users_customuser u join tenants_tenant t on u.default_tenant_id = t.id where u.id=?";
                                String schemaName = null;
                                Connection cn = Database.getInstance().GetDBConnection();
                                try {
                                    PreparedStatement getSchema = cn.prepareStatement(query);
                                    getSchema.setLong(1, Long.parseLong(key));
                                    ResultSet reader = getSchema.executeQuery();
                                    if (reader.next()) {
                                        schemaName = reader.getString("schema_name");
                                    }
                                    reader.close();
                                }
                                finally {
                                    JDBCCloser.close(cn);
                                }
                                return schemaName;
                            }
                        }
                );
    }
    private static LoadingCache<String, String> getLoadingCache() {
        return userSchemasCache;
    }

    public static String getUserSchemaUsingGuava(String userId) {
        try {
            LoadingCache<String, String> schemasCache = UserSchemaGuavaCacheUtil.getLoadingCache();
            return schemasCache.get(userId);
        } catch (Exception e){
            Logs.write(Logs.Level.ERROR, "getUserSchemaUsingGuava() " + e.getMessage());
            return null;
        }
    }

    public static void clearCache(){
        userSchemasCache.invalidateAll();
    }
}
