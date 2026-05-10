package com.stugger.logviewer.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.stugger.logviewer.MainApp;
import com.stugger.logviewer.api.model.RemoteLogExportEstimate;
import com.stugger.logviewer.api.model.RemoteLogExportRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;

/**
 * HTTP client used for communicating with the remote Log Provider API.
 * <p>
 * Handles:
 * <ul>
 *     <li>Remote metadata retrieval</li>
 *     <li>Export estimation requests</li>
 *     <li>ZIP export downloads</li>
 *     <li>API key authentication headers</li>
 * </ul>
 *
 * @author Jake
 * @since May 4th, 2026
 */
public class LogProviderApiClient {

    public static final String DEFAULT_API_URL = "http://localhost:8080";

    private final String baseUrl;
    private final String apiKey;

    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    /**
     * Creates an API client using the current application settings.
     */
    public LogProviderApiClient() {
        String url = MainApp.getSettings().getLogProviderApiUrl();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.apiKey = MainApp.getSettings().getLogProviderApiKey();
    }

    /**
     * Creates a request builder preconfigured with the API base URL and
     * authentication header.
     *
     * @param path API endpoint path
     * @return configured request builder
     */
    private HttpRequest.Builder requestBuilder(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path));

        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("X-API-Key", apiKey);
        }

        return builder;
    }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Logs
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    /**
     * Requests a remote export estimate for the supplied log export request.
     *
     * @param exportRequest export filters and date range
     * @return estimated file count and total byte size
     * @throws IOException if the API request fails
     * @throws InterruptedException if the request is interrupted
     */
    public RemoteLogExportEstimate estimateExport(RemoteLogExportRequest exportRequest) throws IOException, InterruptedException {
        String body = gson.toJson(exportRequest);

        HttpRequest request = requestBuilder("/api/logs/export/estimate")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("API request failed: HTTP " + response.statusCode() + " - " + response.body());
        }

        return gson.fromJson(response.body(), RemoteLogExportEstimate.class);
    }

    /**
     * Downloads a remote ZIP export matching the supplied request.
     *
     * @param exportRequest export filters and date range
     * @param outputZip destination ZIP file path
     * @throws IOException if the download fails
     * @throws InterruptedException if the request is interrupted
     */
    public void downloadExport(RemoteLogExportRequest exportRequest, Path outputZip) throws IOException, InterruptedException {
        String body = gson.toJson(exportRequest);

        HttpRequest request = requestBuilder("/api/logs/export")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(outputZip));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("API download failed: HTTP " + response.statusCode());
        }
    }

    /* -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    |
    |------ Metadata
    |
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

    /**
     * Retrieves available global log category/type paths from the remote API.
     *
     * @return global log category/type paths
     * @throws IOException if the API request fails
     * @throws InterruptedException if the request is interrupted
     */
    public List<String> getGlobalLogTypes() throws IOException, InterruptedException {
        return getStringList("/api/meta/log-types/global");
    }

    /**
     * Retrieves available player log category/type paths from the remote API.
     *
     * @return player log category/type paths
     * @throws IOException if the API request fails
     * @throws InterruptedException if the request is interrupted
     */
    public List<String> getPlayerLogTypes() throws IOException, InterruptedException {
        return getStringList("/api/meta/log-types/players");
    }

    /**
     * Executes a GET request against the supplied API endpoint and parses the
     * response as a string list.
     *
     * @param path API endpoint path
     * @return parsed string list response
     * @throws IOException if the API request fails
     * @throws InterruptedException if the request is interrupted
     */
    private List<String> getStringList(String path) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(path)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("API request failed: HTTP " + response.statusCode() + " - " + response.body());
        }

        return gson.fromJson(response.body(), new TypeToken<List<String>>() {}.getType());
    }
}