package com.ampliapps.amplisync;
import java.sql.Connection;
import java.sql.SQLException;


public class JDBCCloser {
    public static void close(Connection... connections) {

        if (connections == null)
            return;

        for (Connection connection : connections)
            if (connection != null)
                try {
                    connection.close();
                } catch (SQLException e) {
                    Logs.write(Logs.Level.ERROR, "JDBCCloser->close() " + e.getMessage());
                }
    }
}
