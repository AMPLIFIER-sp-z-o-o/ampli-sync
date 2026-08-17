package com.ampliapps.amplisync.devclient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import com.ampliapps.amplisync.devclient.PayloadBuilder.PushPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;



public class SyncDevClient {
    private static final String DEV_AUTH_HEADER = "Bearer dev-local-token";
    private static final String DEV_USER_ID_HEADER = "Dev-User-Id";

    private final String syncBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String devUserId;

    public SyncDevClient(String syncBaseUrl) {
        this.syncBaseUrl = normalizeBaseUrl(syncBaseUrl);
        this.devUserId = null;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public SyncDevClient(String syncBaseUrl, String devUserId) {
        this.syncBaseUrl = normalizeBaseUrl(syncBaseUrl);
        this.devUserId = devUserId;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public String healthCheck() {
        HttpRequest request = requestBuilder(syncBaseUrl)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Health check failed with status: " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Health check request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Health check request was interrupted", e);
        }
    }

    public String syncBaseUrl() {
        return syncBaseUrl;
    }

    private static String normalizeBaseUrl(String syncBaseUrl) {
        if (syncBaseUrl == null || syncBaseUrl.isBlank()) {
            throw new IllegalArgumentException("syncBaseUrl cannot be empty");
        }

        return syncBaseUrl.endsWith("/") ? syncBaseUrl : syncBaseUrl + "/";
    }

    public Path downloadPrepopulatedDatabaseArchive(String deviceId, Path outputDirectory) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId can't be empty");
        }

        try {
            Files.createDirectories(outputDirectory);

            Path archivePath = outputDirectory.resolve("database.zip");

            HttpRequest request = requestBuilder(syncBaseUrl + "prepopulate-db/" + deviceId)
                    .GET()
                    .build();

            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(archivePath));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Prepopulate database failed with status: " + response.statusCode());
            }

            return archivePath;
        } catch (IOException e) {
            throw new IllegalStateException("Prepopulate database request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Prepopulate database request was interrupted", e);
        }
    }

    public void sendChanges(String deviceId, PushPayload payload) {
        int statusCode = sendChangesAndReturnStatus(deviceId, payload);

        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Send changes failed with status: " +
                    statusCode);
        }
    }

    public int sendChangesAndReturnStatus(String deviceId, PushPayload payload) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId can't be empty");
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize push payload",
                    e);
        }

        HttpRequest request = requestBuilder(syncBaseUrl + "receive-changes/" +
                deviceId)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                System.out.println("receive-changes failed with status " + response.statusCode());
                System.out.println(response.body());
            }

            return response.statusCode();
        } catch (IOException e) {
            throw new IllegalStateException("Send changes request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Send changes request was interrupted", e);
        }


    }

    public List<PullChanges> pullChangesForTable(String tableName, String deviceId) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName can't be empty");
        }

        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId can't be empty");
        }

        HttpRequest request = requestBuilder(syncBaseUrl + "sync-compressed/" + tableName + "/" + deviceId)
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Pull changes failed with status: " + response.statusCode());
            }

            String json = unzipGzip(response.body());

            return objectMapper.readValue(json, new TypeReference<List<PullChanges>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Pull changes request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pull changes request was interrupted", e);
        }
    }

    public void initializeChangeNotificationPoc() {
        HttpRequest request = requestBuilder(syncBaseUrl + "change-notification-poc/initialize")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Initialize change notification PoC failed with status: "
                        + response.statusCode());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Initialize change notification PoC request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Initialize change notification PoC request was interrupted", e);
        }
    }

    public void commitSync(int syncId) {
        if (syncId <= 0) {
            return;
        }

        int statusCode = commitSyncAndReturnStatus(syncId);

        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Commit sync failed with status: " + statusCode);
        }
    }

    public int commitSyncAndReturnStatus(int syncId) {
        HttpRequest request = requestBuilder(syncBaseUrl + "commit-sync/" + syncId)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        } catch (IOException e) {
            throw new IllegalStateException("Commit sync request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Commit sync request was interrupted", e);
        }
    }

    private HttpRequest.Builder requestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", DEV_AUTH_HEADER);

        if (devUserId != null && !devUserId.isBlank()) {
            builder.header(DEV_USER_ID_HEADER, devUserId);
        }

        return builder;
    }

    private static String unzipGzip(byte[] compressedBytes) throws IOException {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedBytes))) {
            return new String(gzipInputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }



    public Path unpackDatabaseArchive(Path archivePath, Path outputDirectory) {
        try {
            Files.createDirectories(outputDirectory);
            unzip(archivePath, outputDirectory);

            Path databasePath = outputDirectory.resolve("amperflow.db");

            if (!Files.exists(databasePath)) {
                throw new IllegalStateException("Prepopulated database was not found in archive: " + databasePath);
            }

            return databasePath;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to unpack prepopulated database archive", e);
        }
    }

    private static void unzip(Path archivePath, Path outputDirectory) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(archivePath))) {
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path targetPath = outputDirectory.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zipInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zipInputStream.closeEntry();
            }
        }
    }



}
