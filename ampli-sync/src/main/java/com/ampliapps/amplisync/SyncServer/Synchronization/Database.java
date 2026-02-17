package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.Logs;
import com.ampliapps.amplisync.SQLiteSyncConfig;
import com.mchange.v2.c3p0.ComboPooledDataSource;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class Database {
    public static String SQLiteSyncVersion = "";

    private static Database datasource;
    private ComboPooledDataSource cpds;

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

        try {
            cpds = new ComboPooledDataSource();
            cpds.setIdentityToken("ampli-sync");
            cpds.setDriverClass(SQLiteSyncConfig.DBDRIVER); //loads the jdbc driver
            cpds.setJdbcUrl(SQLiteSyncConfig.DBURL);
            cpds.setUser(SQLiteSyncConfig.DBUSER);
            cpds.setPassword(SQLiteSyncConfig.DBPASS);

            // the settings below are optional -- c3p0 can work with defaults
            cpds.setMaxPoolSize(40);
            cpds.setAcquireIncrement(2);
            cpds.setInitialPoolSize(2);
            cpds.setMinPoolSize(2);
            cpds.setAcquireRetryAttempts(0);
            cpds.setAcquireRetryDelay(3000);
            cpds.setBreakAfterAcquireFailure(false);
            cpds.setMaxConnectionAge(60);
            cpds.setMaxIdleTime(30);
            cpds.setMaxIdleTimeExcessConnections(10);
            cpds.setIdleConnectionTestPeriod(30); //changed from 15->30
            cpds.setTestConnectionOnCheckout(false);
            cpds.setDebugUnreturnedConnectionStackTraces(true);
            cpds.setAutoCommitOnClose(true);
            cpds.setMaxStatements(0);
            cpds.setMaxStatementsPerConnection(20);

        } catch (PropertyVetoException e){
            Logs.write(Logs.Level.ERROR,"Database constructor: " + e.getMessage());
        }
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
            return this.cpds.getConnection();
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
