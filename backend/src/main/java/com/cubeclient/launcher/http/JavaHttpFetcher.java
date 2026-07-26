package com.cubeclient.launcher.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class JavaHttpFetcher implements HttpFetcher {
    // Redirects must be followed: Adoptium's download links point at GitHub releases, which
    // answer with a 302 to a CDN. HttpClient defaults to Redirect.NEVER, which turned every
    // JRE download into a bare "returned status 302" failure. NORMAL still refuses an
    // HTTPS -> HTTP downgrade.
    private final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    public String getString(String url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("GET " + url + " returned status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, e);
        }
    }

    @Override
    public String getString(String url, Map<String, String> headers) throws IOException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
            headers.forEach(builder::header);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // Deliberately omits the response body: these endpoints are authenticated and their
                // error payloads are not guaranteed to be free of sensitive material.
                throw new IOException("GET " + url + " returned status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, e);
        }
    }

    @Override
    public void downloadToFile(String url, Path destination) throws IOException {
        try {
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(destination);
                throw new IOException("GET " + url + " returned status " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        }
    }

    @Override
    public HttpResult postForm(String url, Map<String, String> form) throws IOException {
        StringBuilder encoded = new StringBuilder();
        form.forEach((key, value) -> {
            if (encoded.length() > 0) {
                encoded.append('&');
            }
            encoded.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded.toString()))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // Returns the status rather than throwing: a 400 here is normal protocol traffic
            // during device-code polling, not a failure.
            return new HttpResult(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while posting to " + url, e);
        }
    }

    @Override
    public HttpResult postJsonAllowingErrors(String url, String jsonBody, Map<String, String> headers)
            throws IOException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            headers.forEach(builder::header);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while posting to " + url, e);
        }
    }

    @Override
    public String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            headers.forEach(builder::header);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // Status only, never the body: this method carries auth tokens to identity
                // providers, and their error payloads are echoed straight into the user-visible
                // error event. Keep credential material out of it.
                throw new IOException("POST " + url + " returned status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while posting to " + url, e);
        }
    }
}
