package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sql.rowset.CachedRowSet;

public class PullChangeSet {
    public ObjectNode Records;
    public Integer RowsCount = 0;
    public Boolean HasRows = false;
    public CachedRowSet Inserts;
    public CachedRowSet Updates;
    public CachedRowSet Deletes;
}
