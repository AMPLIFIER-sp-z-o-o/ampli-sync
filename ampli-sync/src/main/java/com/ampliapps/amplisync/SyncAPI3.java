package com.ampliapps.amplisync;

import com.ampliapps.amplisync.SyncServer.CommonTools;

import com.ampliapps.amplisync.SyncServer.Synchronization.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.*;
import java.util.zip.GZIPOutputStream;

@Path("/ampli-sync")
public class SyncAPI3 {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String Test(){

        CommonTools commonTools = new CommonTools();
        Boolean isConnectionOK = commonTools.CheckIfDBConnectionIsOK();

        String connectionStatus = "Database connected!";
        if(!isConnectionOK)
            connectionStatus = "Error creating database connection.";

        return "API[" + commonTools.GetVersionOfSQLiteSyncCOM() + "] OK! " + connectionStatus;
    }


    @GET
    @Path("/sync-compressed/{tableName}/{deviceUniqueId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response SyncChangesGZip(
            @PathParam("tableName") String tableName,
            @PathParam("deviceUniqueId") String deviceUniqueId,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String token,
            @HeaderParam("Dev-User-Id") String devUserId){

        try {
            String subscriberUUID = getSubscriberUUID(token, devUserId);
            String schema = UserSchemaGuavaCacheUtil.getUserSchemaUsingGuava(subscriberUUID);

            SyncService sync = new SyncService();
            String syncData = sync.getChangesForTable(subscriberUUID, schema, tableName, deviceUniqueId);
            ByteArrayOutputStream baostream = new ByteArrayOutputStream();
            OutputStream outStream = new GZIPOutputStream(baostream);
            outStream.write(syncData.getBytes("UTF-8"));
            outStream.close();
            byte[] compressedBytes = baostream.toByteArray();
            return Response.ok(compressedBytes, MediaType.APPLICATION_OCTET_STREAM).build();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GET
    @Path("/commit-sync/{syncId}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response SyncGetChanges(
            @PathParam("syncId") String syncId,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String token,
            @HeaderParam("Dev-User-Id") String devUserId) {

        if (syncId == null || syncId.isEmpty())
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Sync session id is required")
                    .build();

        String subscriberUUID = getSubscriberUUID(token, devUserId);
        String schema = UserSchemaGuavaCacheUtil.getUserSchemaUsingGuava(subscriberUUID);
        SyncService sync = new SyncService();

        try {
            sync.CommitSync(syncId, schema);
            return Response.ok().build();
        } catch (InvalidCommitSyncSessionException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/receive-changes/{deviceUniqueId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ReceiveChanges(
            ObjectNode receivedData,
            @PathParam("deviceUniqueId") String deviceUniqueId,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String token,
            @HeaderParam("Dev-User-Id") String devUserId) {
        String subscriberUUID = getSubscriberUUID(token, devUserId);
        SyncService sync = new SyncService();

        try {
            sync.ReceiveData(
                    receivedData,
                    UserSchemaGuavaCacheUtil.getUserSchemaUsingGuava(subscriberUUID),
                    subscriberUUID,
                    deviceUniqueId
            );
            return Response.ok().build();
        } catch (InvalidReceivePayloadException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/prepopulate-db/{deviceUniqueId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response SQLitePrepopulate(
            @PathParam("deviceUniqueId") String deviceUniqueId,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String token,
            @HeaderParam("Dev-User-Id") String devUserId) {

        String subscriberUUID = getSubscriberUUID(token, devUserId);
        SQLitePrepopulate sqLitePrepopulate = new SQLitePrepopulate();
        File file = new File(sqLitePrepopulate.PrepopulateDatabase(subscriberUUID, deviceUniqueId));
        return Response.ok(file, MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"")
                .build();
    }

    /// Helpers
    private String getSubscriberUUID(String token, String devUserId) {
        JwtTokenValidator jwtTokenValidator = new JwtTokenValidator();
        String subscriberUUID = jwtTokenValidator.getUserId(token);

        if ("true".equalsIgnoreCase(System.getenv("AUTH_DISABLED"))) {
            if (devUserId != null && !devUserId.isBlank()) {
                return devUserId;
            }
            if ((subscriberUUID == null || subscriberUUID.isBlank())) {
                return System.getenv().getOrDefault("DEV_USER_ID", "1");
            }
        }
        return subscriberUUID;
    }
}

