package com.ampliapps.amplisync.devclient;

import java.nio.file.Path;

public class DevClientRunner {
    public static void main(String[] args) {
        SyncDevClient client = new SyncDevClient("http://localhost:8080/ampli-sync/");

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
            for (TableChanges change : payloadBuilder.findInsertChanges()) {
                System.out.println(change);
            }

            database.updateDemoCustomerCity(customerId, "Krakow");
            System.out.println("Updated demo customer: " + customerId);
            System.out.println("Demo customers after update:");
            database.printDemoCustomers();

            database.deleteDemoCustomer(customerId);
            System.out.println("Deleted demo customer: " + customerId);
            System.out.println("Demo customers after delete:");
            database.printDemoCustomers();
        }
    }
}
