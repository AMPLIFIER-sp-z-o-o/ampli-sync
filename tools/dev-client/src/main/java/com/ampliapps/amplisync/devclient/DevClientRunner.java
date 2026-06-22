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
        }
    }
}
