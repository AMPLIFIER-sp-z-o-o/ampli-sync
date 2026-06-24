package com.ampliapps.amplisync.devclient;

public class PayloadBuilder {
    private final SqliteDatabase database;

    public PayloadBuilder (SqliteDatabase database){
        this.database = database;
    }
/// printing for now
    public void printNewInserts(){
        database.printNewInserts();
    }
}
