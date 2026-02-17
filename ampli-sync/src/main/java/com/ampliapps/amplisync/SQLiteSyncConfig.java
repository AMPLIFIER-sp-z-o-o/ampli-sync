package com.ampliapps.amplisync;

import org.apache.logging.log4j.LogManager;
import org.flywaydb.core.Flyway;

import java.util.*;

import static com.ampliapps.amplisync.Logs.logger;


public class SQLiteSyncConfig {

    public static Boolean IsConfigLoaded = false;
    public static String DBURL = "";
    public static String DBUSER = "";
    public static String DBPASS = "";
    public static String DBDRIVER = "org.postgresql.Driver";
    public static String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static Integer HISTORY_DAYS = 1;
    public static String WORKING_DIR = "../working-dir/";
    public static Integer LOG_LEVEL = 5;
    public static String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static String PACKAGE_SIZE = "5000";
    private static String DB_URL_FORMAT = "jdbc:postgresql://%s:%d/%s?reWriteBatchedInserts=true";

    public static void Load() {
        if(SQLiteSyncConfig.IsConfigLoaded)
            return;

        try{

            Logs.write(Logs.Level.INFO, "Reading configuration from environment");
            Map<String, String> env = System.getenv();

            WORKING_DIR = env.get("WORKING_DIR");

            System.setProperty("log4j.SQLiteWorkingDir", WORKING_DIR);
            org.apache.logging.log4j.core.LoggerContext ctx = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
            ctx.reconfigure();
            System.setProperty("com.mchange.v2.log.MLog", "com.mchange.v2.log.FallbackMLog");
            System.setProperty("com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL", "WARNING");

            if(env.get("KEYCLOAK_CLIENT_ID") != null)
                DBUSER = env.get("KEYCLOAK_CLIENT_ID");
            if(env.get("DBUSER") != null)
                DBUSER = env.get("DBUSER");
            if(env.get("DBPASS") != null)
                DBPASS = env.get("DBPASS");
            if(env.get("DBDRIVER") != null)
                DBDRIVER = env.get("DBDRIVER");
            String dbHost = env.get("DBHOST");
            int dbPort = Integer.parseInt(env.get("DBPORT"));
            String dbName = env.get("DBNAME");


            DBURL = String.format(DB_URL_FORMAT, dbHost, dbPort, dbName);
            if(env.get("DATE_FORMAT") != null)
                DATE_FORMAT = env.get("DATE_FORMAT");
            if(env.get("TIMESTAMP_FORMAT") != null)
                TIMESTAMP_FORMAT = env.get("TIMESTAMP_FORMAT");
            if(env.get("HISTORY_DAYS") != null)
                HISTORY_DAYS = Integer.parseInt(env.get("HISTORY_DAYS"));
            if(env.get("LOG_LEVEL") != null)
                LOG_LEVEL = Integer.parseInt(env.get("LOG_LEVEL"));
            PACKAGE_SIZE = env.get("PACKAGE_SIZE");
            if(PACKAGE_SIZE == null)
                PACKAGE_SIZE = "5000";

            //migration naming convention
            //https://flywaydb.org/documentation/concepts/migrations.html#naming

            Flyway flyway = Flyway.configure().dataSource(DBURL, DBUSER, DBPASS).baselineOnMigrate(true).load();
            flyway.repair();
            logger.info( "Running db migrations scripts.");
            flyway.migrate();


            SQLiteSyncConfig.IsConfigLoaded = true;

            Logs.write(Logs.Level.INFO, "Working dir is set to " + WORKING_DIR);

        } catch (Exception ex ) {
            logger.error( "Error setting up configuration. " + ex.getMessage());
        }
    }
}
