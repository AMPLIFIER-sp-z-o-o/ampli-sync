package com.ampliapps.amplisync.devclient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.List;

public class DevClientRunner {
    public static void main(String[] args) throws Exception {

        SyncDevClient client = new SyncDevClient("http://localhost:8080/ampli-sync/");
        ObjectMapper objectMapper = new ObjectMapper();

        String deviceA = "dev-client-device-a";
        String deviceB = "dev-client-device-b";

        Path deviceADirectory = Path.of("/tmp/ampli-sync-dev-client/device-a");
        Path deviceBDirectory = Path.of("/tmp/ampli-sync-dev-client/device-b");

        Path deviceAArchive = client.downloadPrepopulatedDatabaseArchive(deviceA, deviceADirectory);
        Path deviceADatabasePath = client.unpackDatabaseArchive(deviceAArchive, deviceADirectory);

        Path deviceBArchive = client.downloadPrepopulatedDatabaseArchive(deviceB, deviceBDirectory);
        Path deviceBDatabasePath = client.unpackDatabaseArchive(deviceBArchive, deviceBDirectory);

        try (SqliteDatabase deviceADatabase = SqliteDatabase.open(deviceADatabasePath);
             SqliteDatabase deviceBDatabase = SqliteDatabase.open(deviceBDatabasePath)) {

            PayloadBuilder deviceAPayloadBuilder = new PayloadBuilder(deviceADatabase);

            String insertedCustomerId = UUID.randomUUID().toString();

            Map<String, Object> insertedCustomer = Map.of(
                    "id", insertedCustomerId,
                    "name", "Inserted From Device A",
                    "email", "inserted-device-a@example.com",
                    "city", "Warsaw"
            );

            String updatedCustomerId = "0b8e9b8e-0fb5-4f2d-8d4c-3c57e7dc8e47";
            String deletedCustomerId = "8fb5f9c7-9929-4f87-8fcb-19f2092f0a5d";

            deviceADatabase.insertRow("demo_customers", insertedCustomer);
            System.out.println("Device A inserted customer: " + insertedCustomerId);

            deviceADatabase.updateRow(
                    "demo_customers",
                    Map.of("city", "Wroclaw"),
                    "id",
                    updatedCustomerId
            );
            System.out.println("Device A updated customer: " + updatedCustomerId);

            deviceADatabase.deleteRow("demo_customers", "id", deletedCustomerId);
            System.out.println("Device A deleted customer: " + deletedCustomerId);

            PayloadBuildResult pushResult = deviceAPayloadBuilder.buildPushPayloadResult();

            System.out.println("Device A push payload:");
            System.out.println(objectMapper.writeValueAsString(pushResult.payload()));

            client.sendChanges(deviceA, pushResult.payload());
            System.out.println("Device A pushed changes.");

            deviceADatabase.clearProcessedChanges(pushResult);
            System.out.println("Device A local markers cleared.");

            List<PullChanges> deviceBPullChanges = client.pullChangesForTable("demo_customers", deviceB);

            System.out.println("Device B pull response:");
            System.out.println(objectMapper.writeValueAsString(deviceBPullChanges));

            deviceBDatabase.applyPullChanges(deviceBPullChanges);
            System.out.println("Device B applied pulled changes.");

            String insertedCustomerNameOnB = deviceBDatabase.findFirstValue(
                    "demo_customers",
                    "name",
                    "id = '" + insertedCustomerId + "'"
            );

            String updatedCustomerCityOnB = deviceBDatabase.findFirstValue(
                    "demo_customers",
                    "city",
                    "id = '" + updatedCustomerId + "'"
            );

            System.out.println("Device B inserted customer name: " + insertedCustomerNameOnB);
            System.out.println("Device B updated customer city: " + updatedCustomerCityOnB);

            try {
                deviceBDatabase.findFirstValue(
                        "demo_customers",
                        "id",
                        "id = '" + deletedCustomerId + "'"
                );

                System.out.println("Device B deleted customer still exists: " + deletedCustomerId);
            } catch (IllegalStateException e) {
                System.out.println("Device B deleted customer is gone: " + deletedCustomerId);
            }
        }


    }
}

