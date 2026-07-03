package com.ampliapps.amplisync.devclient;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import java.nio.file.Path;
import java.io.File;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers
class SyncDeviceRegressionTest {
    @Container
    private static final ComposeContainer ENVIRONMENT = new ComposeContainer(
            new File("../../deploy-dev/docker/docker-compose.test.yml")
    ).withExposedService("amplisync", 8080);

    @Test
    void shouldStartBackend() {
        String host = ENVIRONMENT.getServiceHost("amplisync", 8080);
        Integer port = ENVIRONMENT.getServicePort("amplisync", 8080);

        SyncDevClient client = new SyncDevClient("http://" + host + ":" + port + "/ampli-sync/");

        String response = client.healthCheck();

        assertTrue(response.contains("Database connected"));
    }

    @Test
    void shouldSyncBetweenTwoDevices() {
        String host = ENVIRONMENT.getServiceHost("amplisync", 8080);
        Integer port = ENVIRONMENT.getServicePort("amplisync", 8080);

        SyncDevClient client = new SyncDevClient("http://" + host + ":" + port + "/ampli-sync/", "1");

        assertTrue(client.healthCheck().contains("Database connected"));

        try (SyncDevice deviceA = new SyncDevice(client, "test-device-a", Path.of("target/test-devices/device-a"));
             SyncDevice deviceB = new SyncDevice(client, "test-device-b", Path.of("target/test-devices/device-b"))) {

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

            deviceA.updateRow("demo_customers", Map.of("city", "Wroclaw"), "id", updatedCustomerId);
            deviceA.deleteRow("demo_customers", "id", deletedCustomerId);

            deviceA.push();
            deviceB.pullTable("demo_customers");

            assertEquals("Inserted From Device A",
                    deviceB.findFirstValue("demo_customers", "name", "id", insertedCustomerId));

            assertEquals("Wroclaw",
                    deviceB.findFirstValue("demo_customers", "city", "id", updatedCustomerId));
            assertFalse(deviceB.rowExists("demo_customers", "id", deletedCustomerId));
        }
    }

}
