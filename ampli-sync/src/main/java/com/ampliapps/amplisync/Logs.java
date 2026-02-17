package com.ampliapps.amplisync;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class Logs {

    final static Logger logger = LogManager.getLogger(Logs.class);

    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    public static void write(Level level, String msg) {
        if(SQLiteSyncConfig.LOG_LEVEL == 0)
            return;

        if (logger != null && level != null && isEnabledFor(level)) {
            switch (level) {
                case TRACE:
                    if(SQLiteSyncConfig.LOG_LEVEL >= 4)
                        logger.trace(msg);
                    break;
                case DEBUG:
                    if(SQLiteSyncConfig.LOG_LEVEL >= 3)
                        logger.debug(msg);
                    break;
                case INFO:
                    if(SQLiteSyncConfig.LOG_LEVEL >= 2)
                        logger.info(msg);
                    break;
                case WARN:
                    if(SQLiteSyncConfig.LOG_LEVEL >= 1)
                        logger.warn(msg);
                    break;
                case ERROR:
                    logger.error(msg);
                    break;
            }
        }
    }

    public static boolean isEnabledFor(Level level) {
        boolean res = false;
        if (logger != null && level != null) {
            switch (level) {
                case TRACE:
                    res = logger.isTraceEnabled();
                    break;
                case DEBUG:
                    res = logger.isDebugEnabled();
                    break;
                case INFO:
                    res = logger.isInfoEnabled();
                    break;
                case WARN:
                    res = logger.isWarnEnabled();
                    break;
                case ERROR:
                    res = logger.isErrorEnabled();
                    break;
            }
        }
        return res;
    }
}
