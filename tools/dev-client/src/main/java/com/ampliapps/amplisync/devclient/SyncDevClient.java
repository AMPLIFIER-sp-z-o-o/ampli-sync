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

public class SyncDevClient {
    private static final String DEV_AUTH_HEADER = "Bearer dev-local-token";

    private final String syncBaseUrl;
    private final HttpClient httpClient;

    public SyncDevClient(String syncBaseUrl) {
        this.syncBaseUrl = normalizeBaseUrl(syncBaseUrl);
        this.httpClient = HttpClient.newHttpClient();
    }

    public String healthCheck() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(syncBaseUrl))
                .header("Authorization", DEV_AUTH_HEADER)
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(syncBaseUrl + "prepopulate-db/" + deviceId))
                    .header("Authorization", DEV_AUTH_HEADER)
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

    /*
     - call backend health endpoint
     - download, unpack sqlite database from prepopulate-db
     - open sqlite database
     - perform local insert/update/delete operations
     - build payload from local SQLite, like in rn client
     - send local changes to the backend
     - pull changes from the backend
     */
}
