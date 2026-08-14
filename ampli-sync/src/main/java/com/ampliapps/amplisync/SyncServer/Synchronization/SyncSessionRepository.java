package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.ampliapps.amplisync.JDBCCloser;
import com.ampliapps.amplisync.Logs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class SyncSessionRepository {
    private final SQLQueries QUERIES = new SQLQueries();

    Integer startSync(String subscriberId, Integer tableId, String schema) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            Integer id = 0;
            Integer affectedRows = 0;

            PreparedStatement query = cn.prepareStatement(QUERIES.START_NEW_SYNC(schema), Statement.RETURN_GENERATED_KEYS);
            query.setInt(1, Integer.parseInt(subscriberId));
            query.setString(2, "");
            query.setInt(3, tableId);

            affectedRows = query.executeUpdate();

            if (affectedRows == 0) {
                Logs.write(Logs.Level.ERROR, "Creating new sync failed, no ID obtained.");
            }

            try (ResultSet generatedKeys = query.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    id = generatedKeys.getInt(1);
                } else {
                    Logs.write(Logs.Level.ERROR, "Creating new sync failed, no ID obtained.");
                }
            }

            return id;
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "SetSyncStartMarker() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
        return 0;
    }

    void finishSync(String syncId, String schema) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            PreparedStatement query = cn.prepareStatement(QUERIES.COMMIT_SYNC_UPDATE(schema));
            query.setInt(1, Integer.parseInt(syncId));
            query.execute();
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "SetSyncFinishMarker() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }
    }

    CommitSyncSession readCommitSyncSession(String syncId, String schema) {
        Connection cn = Database.getInstance().GetDBConnection();
        try {
            PreparedStatement query = cn.prepareStatement(QUERIES.COMMIT_SYNC(schema));
            query.setInt(1, Integer.parseInt(syncId));
            ResultSet reader = query.executeQuery();

            if (reader.next()) {
                return new CommitSyncSession(
                        reader.getString("TableName"),
                        reader.getInt("subscriberId")
                );
            }
        } catch (SQLException e) {
            Logs.write(Logs.Level.ERROR, "CommitSync() " + e.getMessage());
        } finally {
            JDBCCloser.close(cn);
        }

        throw new InvalidCommitSyncSessionException("Sync session was not found: " + syncId);
    }
}