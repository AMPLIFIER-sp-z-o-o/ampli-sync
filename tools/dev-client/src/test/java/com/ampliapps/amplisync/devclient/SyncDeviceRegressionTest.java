package com.ampliapps.amplisync.devclient;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import java.nio.file.Path;
import java.io.File;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class SyncDeviceRegressionTest {
    private static final String DEV_USER_ID = "1";
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

            // Arrange: create two local devices for the same dev user.
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

            // Act: push device A changes and pull backend
            deviceA.push();

            SyncDeviceAssertions.assertNoPendingUpdateOrDeleteMarkers(deviceA);

            deviceA.pullTable(DemoCustomersFixture.TABLE);
            SyncDeviceAssertions.assertNoLocalChanges(deviceA);

            deviceB.pullTable(DemoCustomersFixture.TABLE);

            // Assert: device B local SQLite has the expected synced state.
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

    @Test
    void shouldPropagateInsertToSecondDevice() {
        SyncDevClient client = createClient("1");
        String testRunId = newId();

        try (SyncDevice deviceA = createDevice(client, testRunId, "device-a");
             SyncDevice deviceB = createDevice(client, testRunId, "device-b")) {
            // Arrange
            deviceA.prepopulate();
            deviceB.prepopulate();

            String insertedCustomerId = newId();
            Map<String, Object> insertedCustomer = customer(
                    insertedCustomerId,
                    "Inserted Customer",
                    "inserted@example.com",
                    "Warsaw"
            );

            // Act
            deviceA.insertRow(DemoCustomersFixture.TABLE, insertedCustomer);
            deviceA.push();
            deviceA.pullTable(DemoCustomersFixture.TABLE);
            deviceB.pullTable(DemoCustomersFixture.TABLE);

            // Assert
            SyncDeviceAssertions.assertRowValues(
                    deviceB,
                    DemoCustomersFixture.TABLE,
                    "id",
                    insertedCustomerId,
                    insertedCustomer
            );
            SyncDeviceAssertions.assertNoLocalChanges(deviceA);
            SyncDeviceAssertions.assertNoLocalChanges(deviceB);
        }
    }

    @Test
    void shouldPropagateUpdateToSecondDevice() {
        SyncDevClient client = createClient("1");
        String testRunId = newId();

        try (SyncDevice deviceA = createDevice(client, testRunId, "device-a");
             SyncDevice deviceB = createDevice(client, testRunId, "device-b")) {
            // Arrange
            deviceA.prepopulate();
            deviceB.prepopulate();

            String customerId = newId();
            Map<String, Object> initialCustomer = customer(
                    customerId,
                    "Customer To Update",
                    "update@example.com",
                    "Warsaw"
            );
            deviceA.insertRow(DemoCustomersFixture.TABLE, initialCustomer);
            deviceA.push();
            deviceA.pullTable(DemoCustomersFixture.TABLE);
            deviceB.pullTable(DemoCustomersFixture.TABLE);

            // Act
            deviceA.updateRow(
                    DemoCustomersFixture.TABLE,
                    Map.of("city", "Wroclaw"),
                    "id",
                    customerId
            );
            deviceA.push();
            deviceB.pullTable(DemoCustomersFixture.TABLE);

            // Assert
            SyncDeviceAssertions.assertRowValues(
                    deviceB,
                    DemoCustomersFixture.TABLE,
                    "id",
                    customerId,
                    Map.of(
                            "id", customerId,
                            "name", "Customer To Update",
                            "email", "update@example.com",
                            "city", "Wroclaw"
                    )
            );
            assertEquals(1, deviceB.countRows(DemoCustomersFixture.TABLE, "id", customerId));
            SyncDeviceAssertions.assertNoPendingUpdateOrDeleteMarkers(deviceA);
            SyncDeviceAssertions.assertNoLocalChanges(deviceB);
        }
    }

    @Test
    void shouldPropagateDeleteToSecondDevice() {
        SyncDevClient client = createClient("1");
        String testRunId = newId();

        try (SyncDevice deviceA = createDevice(client, testRunId, "device-a");
             SyncDevice deviceB = createDevice(client, testRunId, "device-b")) {
            // Arrange
            deviceA.prepopulate();
            deviceB.prepopulate();

            String customerId = newId();
            Map<String, Object> customer = customer(
                    customerId,
                    "Customer To Delete",
                    "delete@example.com",
                    "Warsaw"
            );

            deviceA.insertRow(DemoCustomersFixture.TABLE, customer);
            deviceA.push();
            deviceA.pullTable(DemoCustomersFixture.TABLE);
            deviceB.pullTable(DemoCustomersFixture.TABLE);

            // Act
            deviceA.deleteRow(DemoCustomersFixture.TABLE, "id", customerId);
            deviceA.push();
            deviceB.pullTable(DemoCustomersFixture.TABLE);

            // Assert
            SyncDeviceAssertions.assertRowDoesNotExist(
                    deviceB,
                    DemoCustomersFixture.TABLE,
                    "id",
                    customerId
            );
            SyncDeviceAssertions.assertNoPendingUpdateOrDeleteMarkers(deviceA);
            SyncDeviceAssertions.assertNoLocalChanges(deviceB);
        }
    }

    @Test
    void shouldSyncInsertUpdateAndDeleteInOnePush() {
        SyncDevClient client = createClient("1");
        String testRunId = newId();

        try (SyncDevice deviceA = createDevice(client, testRunId, "device-a");
             SyncDevice deviceB = createDevice(client, testRunId, "device-b")) {
            // Arrange
            deviceA.prepopulate();
            deviceB.prepopulate();

            String updatedCustomerId = newId();
            String deletedCustomerId = newId();

            Map<String, Object> customerToUpdate = customer(
                    updatedCustomerId,
                    "Customer To Update",
                    "multi-update@example.com",
                    "Warsaw"
            );

            Map<String, Object> customerToDelete = customer(
                    deletedCustomerId,
                    "Customer To Delete",
                    "multi-delete@example.com",
                    "Poznan"
            );

            deviceA.insertRow(DemoCustomersFixture.TABLE, customerToUpdate);
            deviceA.insertRow(DemoCustomersFixture.TABLE, customerToDelete);
            deviceA.push();
            deviceA.pullTable(DemoCustomersFixture.TABLE);
            deviceB.pullTable(DemoCustomersFixture.TABLE);

            SyncDeviceAssertions.assertNoLocalChanges(deviceA);
            SyncDeviceAssertions.assertNoLocalChanges(deviceB);

            String insertedCustomerId = newId();
            Map<String, Object> insertedCustomer = customer(
                    insertedCustomerId,
                    "Customer To Insert",
                    "multi-insert@example.com",
                    "Gdansk"
            );

            // Act
            deviceA.insertRow(DemoCustomersFixture.TABLE, insertedCustomer);
            deviceA.updateRow(
                    DemoCustomersFixture.TABLE,
                    Map.of("city", "Krakow"),
                    "id",
                    updatedCustomerId
            );
            deviceA.deleteRow(DemoCustomersFixture.TABLE, "id", deletedCustomerId);

            deviceA.push();
            deviceA.pullTable(DemoCustomersFixture.TABLE);
            deviceB.pullTable(DemoCustomersFixture.TABLE);

            // Assert
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
                    updatedCustomerId,
                    Map.of(
                            "id", updatedCustomerId,
                            "name", "Customer To Update",
                            "email", "multi-update@example.com",
                            "city", "Krakow"
                    )
            );

            SyncDeviceAssertions.assertRowDoesNotExist(
                    deviceB,
                    DemoCustomersFixture.TABLE,
                    "id",
                    deletedCustomerId
            );

            SyncDeviceAssertions.assertNoLocalChanges(deviceA);
            SyncDeviceAssertions.assertNoLocalChanges(deviceB);
        }
    }

    @Test
    void shouldRequirePullAfterPushForFreshInsertRowId() {
        SyncDevClient client = createClient("1");
        String testRunId = newId();

        try (SyncDevice deviceA = createDevice(client, testRunId, "device-a")) {
            // Arrange
            deviceA.prepopulate();

            String insertedCustomerId = newId();
            Map<String, Object> insertedCustomer = customer(
                    insertedCustomerId,
                    "Fresh Insert Customer",
                    "fresh-insert@example.com",
                    "Warsaw"
            );

            // Act
            deviceA.insertRow(DemoCustomersFixture.TABLE, insertedCustomer);
            deviceA.push();

            // Assert after push
            SyncDeviceAssertions.assertNoPendingUpdateOrDeleteMarkers(deviceA);
            assertNull(
                    deviceA.findRow(DemoCustomersFixture.TABLE, "id", insertedCustomerId).get("rowid"),
                    "Fresh insert should still have null rowid before pull reconciliation"
            );

            // Act
            deviceA.pullTable(DemoCustomersFixture.TABLE);

            // Assert after pull
            assertNotNull(
                    deviceA.findRow(DemoCustomersFixture.TABLE, "id", insertedCustomerId).get("rowid"),
                    "Fresh insert should receive backend rowid after pull reconciliation"
            );
            SyncDeviceAssertions.assertNoLocalChanges(deviceA);
        }
    }

    @Test
    void shouldNotChangeLocalDatabaseWhenPullHasNoChanges() {
        SyncDevClient client = createClient("1");
        String testRunId = newId();

        try (SyncDevice deviceA = createDevice(client, testRunId, "device-a")) {
            // Arrange
            deviceA.prepopulate();
            int rowsBeforePull = deviceA.countRows(DemoCustomersFixture.TABLE);

            // Act
            deviceA.pullTable(DemoCustomersFixture.TABLE);

            // Assert
            assertEquals(
                    rowsBeforePull,
                    deviceA.countRows(DemoCustomersFixture.TABLE),
                    "Pull without backend changes should not change local row count"
            );
            SyncDeviceAssertions.assertNoLocalChanges(deviceA);
        }
    }

    @Test
    void shouldSynchronizeTenantDataBetweenTwoDevicesOfSameUser() {
        SyncDevClient client = createClient(DEV_USER_ID);
        String testRunId = newId();

        try (SyncDevice deviceA = createDevice(client, testRunId, "device-a");
             SyncDevice deviceB = createDevice(client, testRunId, "device-b")) {
            // Arrange
            deviceA.prepopulate();
            deviceB.prepopulate();

            String customerId = newId();
            Map<String, Object> customer = customer(
                    customerId,
                    "Same User Customer",
                    "same-user@example.com",
                    "Warsaw"
            );

            // Act: device A creates data, device B receives it.
            deviceA.insertRow(DemoCustomersFixture.TABLE, customer);
            deviceA.push();
            deviceA.pullTable(DemoCustomersFixture.TABLE);
            deviceB.pullTable(DemoCustomersFixture.TABLE);

            SyncDeviceAssertions.assertRowValues(
                    deviceB,
                    DemoCustomersFixture.TABLE,
                    "id",
                    customerId,
                    customer
            );

            // Act: device B changes the same tenant data, device A receives it.
            deviceB.updateRow(
                    DemoCustomersFixture.TABLE,
                    Map.of("city", "Gdynia"),
                    "id",
                    customerId
            );
            deviceB.push();
            deviceA.pullTable(DemoCustomersFixture.TABLE);
            deviceB.pullTable(DemoCustomersFixture.TABLE);

            // Assert
            Map<String, Object> expectedCustomer = Map.of(
                    "id", customerId,
                    "name", "Same User Customer",
                    "email", "same-user@example.com",
                    "city", "Gdynia"
            );

            SyncDeviceAssertions.assertRowValues(
                    deviceA,
                    DemoCustomersFixture.TABLE,
                    "id",
                    customerId,
                    expectedCustomer
            );
            SyncDeviceAssertions.assertRowValues(
                    deviceB,
                    DemoCustomersFixture.TABLE,
                    "id",
                    customerId,
                    expectedCustomer
            );
            SyncDeviceAssertions.assertNoLocalChanges(deviceA);
            SyncDeviceAssertions.assertNoLocalChanges(deviceB);
        }
    }





    private static Map<String, Object> customer(String id, String name, String email, String city) {
        return Map.of(
                "id", id,
                "name", name,
                "email", email,
                "city", city
        );
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static SyncDevClient createClient(String devUserId) {
        String host = ENVIRONMENT.getServiceHost("amplisync", 8080);
        Integer port = ENVIRONMENT.getServicePort("amplisync", 8080);

        return new SyncDevClient("http://" + host + ":" + port + "/ampli-sync/", devUserId);
    }

    private static SyncDevice createDevice(SyncDevClient client, String testRunId, String deviceName) {
        return new SyncDevice(
                client,
                testRunId + "-" + deviceName,
                Path.of("target/test-devices", testRunId, deviceName)
        );
    }





}
