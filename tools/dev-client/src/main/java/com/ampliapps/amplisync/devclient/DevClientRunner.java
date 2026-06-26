package com.ampliapps.amplisync.devclient;

import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DevClientRunner {
    public static void main(String[] args) throws Exception {
        SyncDevClient client = new SyncDevClient("http://localhost:8080/ampli-sync/");
        ObjectMapper objectMapper = new ObjectMapper();

        System.out.println(client.healthCheck());

        Path outputDirectory = Path.of("/tmp/ampli-sync-dev-client");
        Path archivePath = client.downloadPrepopulatedDatabaseArchive("dev-client-device-1", outputDirectory);
        Path databasePath = client.unpackDatabaseArchive(archivePath, outputDirectory);

        try (SqliteDatabase database = SqliteDatabase.open(databasePath)) {
            System.out.println("demo_customers exists: " + database.tableExists("demo_customers"));
            String customerId = database.insertDemoCustomer(
                    "Dev Client Customer",
                    "client@gmail.com",
                    "Warsaw"
            );

            System.out.println("Inserted demo customer: " + customerId);
            System.out.println("Demo customers after insert:");
            database.printDemoCustomers();

            PayloadBuilder payloadBuilder = new PayloadBuilder(database);

            System.out.println("Insert changes:");
            for (TableChanges change : payloadBuilder.buildChanges()) {
                System.out.println(change);
            }

            database.updateDemoCustomerCity(customerId, "Krakow");
            System.out.println("Updated demo customer: " + customerId);
            System.out.println("Demo customers after update:");
            database.printDemoCustomers();

            System.out.println("Changes after update:");
            for (TableChanges change : payloadBuilder.buildChanges()) {
                System.out.println(change);
            }

            String existingCustomerId = database.findFirstExistingCustomer();
            database.updateDemoCustomerCity(existingCustomerId, "Lodz");

            System.out.println("Updated existing demo customer: " + existingCustomerId);
            System.out.println("Changes after existing customer update:");
            for (TableChanges change : payloadBuilder.buildChanges()) {
                System.out.println(change);
            }

            database.deleteDemoCustomer(customerId);
            System.out.println("Deleted demo customer: " + customerId);
            System.out.println("Demo customers after delete:");
            database.printDemoCustomers();

            System.out.println("Deleted records:");
            for (DeletedRecord deletedRecord : database.findDeletedRecords()) {
                System.out.println(deletedRecord);
            }

            PayloadBuilder.PushPayload pushPayload = payloadBuilder.buildPushPayload();

            System.out.println("Full push payload:");
            System.out.println(pushPayload);

            System.out.println("Full push payload JSON:");
            System.out.println(objectMapper.writeValueAsString(pushPayload));

            client.sendChanges("dev-client-device-1", pushPayload);
            System.out.println("Push payload sent to backend.");

            PayloadBuildResult result = payloadBuilder.buildPushPayloadResult();

            System.out.println("Full push payload:");
            System.out.println(result.payload());

            System.out.println("Full push payload JSON:");
            System.out.println(objectMapper.writeValueAsString(result.payload()));

            client.sendChanges("dev-client-device-1", result.payload());
            System.out.println("Push payload sent to backend.");

            database.clearProcessedChanges(result);
            System.out.println("Local processed changes cleared.");

        }
    }
}
