package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.Logs;
import com.ampliapps.amplisync.SQLiteSyncConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class Database {
    public static String SQLiteSyncVersion = "";

    private static Database datasource;
    private HikariDataSource hikariDataSource;

    public Database() {

    }

    private void createPool(){
        String version = "";
        try {
            String resourceName = "project.properties";
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Properties props = new Properties();
            try(InputStream resourceStream = loader.getResourceAsStream(resourceName)) {
                props.load(resourceStream);
            }
            version = props.getProperty("version");
        } catch (IOException ex){
            Logs.write(Logs.Level.ERROR,"GetVersionOfSQLiteSyncCOM: " + ex.getMessage());
        }
        SQLiteSyncVersion = version;

        SQLiteSyncConfig.Load();

        HikariConfig config = new HikariConfig();
        config.setPoolName("ampli-sync");
        config.setDriverClassName(SQLiteSyncConfig.DBDRIVER);
        config.setJdbcUrl(SQLiteSyncConfig.DBURL);
        config.setUsername(SQLiteSyncConfig.DBUSER);
        config.setPassword(SQLiteSyncConfig.DBPASS);

        config.setMaximumPoolSize(40);
        config.setMinimumIdle(2);
        config.setMaxLifetime(60000);
        config.setIdleTimeout(30000);
        config.setKeepaliveTime(30000);
        config.setAutoCommit(true);
        config.setInitializationFailTimeout(-1);

        hikariDataSource = new HikariDataSource(config);
    }

    public static Database getInstance() {
        if (datasource == null) {
            datasource = new Database();
            datasource.createPool();
            return datasource;
        } else {
            return datasource;
        }
    }

    public Connection GetDBConnection() {
        try {
            return this.hikariDataSource.getConnection();
        } catch (SQLException e){
            Logs.write(Logs.Level.ERROR, "GetDBConnection() " + e.getMessage());
            Connection conn = null;
            try {
                Class.forName(SQLiteSyncConfig.DBDRIVER).newInstance();
                conn = DriverManager.getConnection(SQLiteSyncConfig.DBURL, SQLiteSyncConfig.DBUSER, SQLiteSyncConfig.DBPASS);
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | SQLException ex) {
                Logs.write(Logs.Level.ERROR, "GetDBConnection() " + ex.getMessage());
            }

            return conn;
        }
    }
}
