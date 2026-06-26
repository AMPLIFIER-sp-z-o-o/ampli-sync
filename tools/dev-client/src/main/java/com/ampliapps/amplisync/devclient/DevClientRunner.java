package com.ampliapps.amplisync.devclient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class DevClientRunner {
    public static void main(String[] args) throws Exception {
        String deviceId = "dev-client-device-1";

        SyncDevClient client = new SyncDevClient("http://localhost:8080/ampli-sync/");
        ObjectMapper objectMapper = new ObjectMapper();

        System.out.println(client.healthCheck());

        Path outputDirectory = Path.of("/tmp/ampli-sync-dev-client");
        Path archivePath = client.downloadPrepopulatedDatabaseArchive(deviceId, outputDirectory);
        Path databasePath = client.unpackDatabaseArchive(archivePath, outputDirectory);

        try (SqliteDatabase database = SqliteDatabase.open(databasePath)) {
            PayloadBuilder payloadBuilder = new PayloadBuilder(database);

            String customerId = UUID.randomUUID().toString();

            Map<String, Object> customer = Map.of(
                    "id", customerId,
                    "name", "Dev Client Customer",
                    "email", "client@gmail.com",
                    "city", "Warsaw"
            );


            database.insertRow("demo_customers", customer);
            System.out.println("Inserted demo customer: " + customerId);

            String existingCustomerId = database.findFirstValue(
                    "demo_customers",
                    "id",
                    "rowid is not null"
            );

            database.updateRow(
                    "demo_customers",
                    Map.of("city", "Lodz"),
                    "id",
                    existingCustomerId
            );

            System.out.println("Updated existing demo customer: " + existingCustomerId);

            database.deleteRow("demo_customers", "id", customerId);
            System.out.println("Deleted freshly inserted demo customer: " + customerId);

            PayloadBuildResult result = payloadBuilder.buildPushPayloadResult();

            System.out.println("Push payload JSON:");
            System.out.println(objectMapper.writeValueAsString(result.payload()));

            client.sendChanges(deviceId, result.payload());
            System.out.println("Push payload sent to backend.");

            database.clearProcessedChanges(result);
            System.out.println("Local processed changes cleared.");

            System.out.println("Payload after cleanup:");
            System.out.println(objectMapper.writeValueAsString(payloadBuilder.buildPushPayload()));
        }
    }
}

