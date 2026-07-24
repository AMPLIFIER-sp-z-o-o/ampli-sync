package com.ampliapps.amplisync.SyncServer.Synchronization;

public class InvalidReceivePayloadException extends RuntimeException {
    public InvalidReceivePayloadException(String message) {
        super(message);
    }
}
