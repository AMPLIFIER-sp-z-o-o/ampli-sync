package com.ampliapps.amplisync.devclient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
