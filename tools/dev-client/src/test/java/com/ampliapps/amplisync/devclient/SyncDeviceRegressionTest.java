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
            Map<String, Object> insertedCustomer = DemoCustomersFixture.insertedCustomer(insertedCustomerId);

            deviceA.insertRow(DemoCustomersFixture.TABLE, insertedCustomer);
            deviceA.updateRow(
                    DemoCustomersFixture.TABLE,
                    DemoCustomersFixture.updatedCustomerValues(),
                    "id",
                    DemoCustomersFixture.UPDATED_CUSTOMER_ID
            );
            deviceA.deleteRow(DemoCustomersFixture.TABLE, "id", DemoCustomersFixture.DELETED_CUSTOMER_ID);

            deviceA.push();

            SyncDeviceAssertions.assertNoPendingUpdateOrDeleteMarkers(deviceA);

            deviceA.pullTable(DemoCustomersFixture.TABLE);
            SyncDeviceAssertions.assertNoLocalChanges(deviceA);

            deviceB.pullTable(DemoCustomersFixture.TABLE);

            SyncDeviceAssertions.assertRowValues(
                    deviceB,
                    DemoCustomersFixture.TABLE,
                    "id",
                    insertedCustomerId,
                    insertedCustomer
            );

            SyncDeviceAssertions.assertRowValues(
                    deviceB,
                    DemoCustomersFixture.TABLE,
                    "id",
                    DemoCustomersFixture.UPDATED_CUSTOMER_ID,
                    DemoCustomersFixture.expectedUpdatedCustomer()
            );

            SyncDeviceAssertions.assertRowDoesNotExist(
                    deviceB,
                    DemoCustomersFixture.TABLE,
                    "id",
                    DemoCustomersFixture.DELETED_CUSTOMER_ID
            );

            SyncDeviceAssertions.assertNoLocalChanges(deviceB);
        }
    }

}
