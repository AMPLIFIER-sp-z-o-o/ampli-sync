package com.ampliapps.amplisync.SyncServer.Synchronization;

import com.fasterxml.jackson.databind.node.ObjectNode;


public class DataObject {

    public String TableName = "";
    public ObjectNode Records = null;
    public String QueryInsert = "";
    public String QueryUpdate = "";
    public String QueryDelete = "";
    public String TriggerInsert = "";
    public String TriggerUpdate = "";
    public String TriggerDelete = "";
    public String TriggerInsertDrop = "";
    public String TriggerUpdateDrop = "";
    public String TriggerDeleteDrop = "";
    public Integer SyncId = 0;
    public String SQLiteSyncVersion = "";
    public String MaxPackageSize = "";
    public String RowsCount = "";
    
}
