package com.ampliapps.amplisync.devclient;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class DevClientRunner {
    public static void main(String[] args) throws Exception {
        String devUserId = argumentOrDefault(args, 0, "1");
        String deviceAId = argumentOrDefault(args, 1, "dev-client-device-a");
        String deviceBId = argumentOrDefault(args, 2, "dev-client-device-b");

        SyncDevClient client = new SyncDevClient("http://localhost:8080/ampli-sync/", devUserId);


        try (SyncDevice deviceA = new SyncDevice(
                client,
                deviceAId,
                Path.of("/tmp/ampli-sync-dev-client/device-a")
        );
             SyncDevice deviceB = new SyncDevice(
                     client,
                     deviceBId,
                     Path.of("/tmp/ampli-sync-dev-client/device-b")
             )) {
            deviceA.prepopulate();
            deviceB.prepopulate();

            String insertedCustomerId = UUID.randomUUID().toString();
            String updatedCustomerId = "0b8e9b8e-0fb5-4f2d-8d4c-3c57e7dc8e47";
            String deletedCustomerId = "8fb5f9c7-9929-4f87-8fcb-19f2092f0a5d";

            deviceA.insertRow("demo_customers", Map.of(
                    "id", insertedCustomerId,
                    "name", "Inserted From Device A",
                    "email", "inserted-device-a@example.com",
                    "city", "Warsaw"
            ));
            System.out.println("Device A inserted customer: " + insertedCustomerId);

            deviceA.updateRow(
                    "demo_customers",
                    Map.of("city", "Wroclaw"),
                    "id",
                    updatedCustomerId
            );
            System.out.println("Device A updated customer: " + updatedCustomerId);

            deviceA.deleteRow("demo_customers", "id", deletedCustomerId);
            System.out.println("Device A deleted customer: " + deletedCustomerId);

            deviceA.push();
            System.out.println("Device A pushed changes.");

            deviceB.pullTable("demo_customers");
            System.out.println("Device B pulled changes.");

            String insertedCustomerNameOnB = deviceB.findFirstValue(
                    "demo_customers",
                    "name",
                    "id",
                    insertedCustomerId
            );

            String updatedCustomerCityOnB = deviceB.findFirstValue(
                    "demo_customers",
                    "city",
                    "id",
                    updatedCustomerId
            );

            System.out.println("Device B inserted customer name: " + insertedCustomerNameOnB);
            System.out.println("Device B updated customer city: " + updatedCustomerCityOnB);

            try {
                deviceB.findFirstValue(
                        "demo_customers",
                        "city",
                        "id",
                        deletedCustomerId
                );

                System.out.println("Device B deleted customer still exists: " + deletedCustomerId);
            } catch (IllegalStateException e) {
                System.out.println("Device B deleted customer is gone: " + deletedCustomerId);
            }
        }
    }

    private static String argumentOrDefault(String[] args, int index, String defaultValue) {
        return args.length > index ? args[index] : defaultValue;
    }

}
