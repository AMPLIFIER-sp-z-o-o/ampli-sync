package com.ampliapps.amplisync.devclient;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;

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
}
