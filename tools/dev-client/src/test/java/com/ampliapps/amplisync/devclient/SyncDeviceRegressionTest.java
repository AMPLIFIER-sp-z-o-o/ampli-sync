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

            SyncDeviceAssertions.assertNoLocalChanges(deviceA);
            SyncDeviceAssertions.assertNoLocalChanges(deviceB);

            String insertedCustomerId = UUID.randomUUID().toString();
            Map<String, Object> insertedCustomer = DemoCustomers.insertedCustomer(insertedCustomerId);

            deviceA.insertRow(DemoCustomers.TABLE, insertedCustomer);
            deviceA.updateRow(
                    DemoCustomers.TABLE,
                    DemoCustomers.updatedCustomerValues(),
                    "id",
                    DemoCustomers.UPDATED_CUSTOMER_ID
            );
            deviceA.deleteRow(DemoCustomers.TABLE, "id", DemoCustomers.DELETED_CUSTOMER_ID);

            deviceA.push();

            SyncDeviceAssertions.assertNoLocalChanges(deviceA);

            deviceB.pullTable(DemoCustomers.TABLE);

            SyncDeviceAssertions.assertRowValues(
                    deviceB,
                    DemoCustomers.TABLE,
                    "id",
                    insertedCustomerId,
                    insertedCustomer
            );

            SyncDeviceAssertions.assertRowValues(
                    deviceB,
                    DemoCustomers.TABLE,
                    "id",
                    DemoCustomers.UPDATED_CUSTOMER_ID,
                    DemoCustomers.expectedUpdatedCustomer()
            );

            SyncDeviceAssertions.assertRowDoesNotExist(
                    deviceB,
                    DemoCustomers.TABLE,
                    "id",
                    DemoCustomers.DELETED_CUSTOMER_ID
            );

            SyncDeviceAssertions.assertNoLocalChanges(deviceB);
        }
    }



}
