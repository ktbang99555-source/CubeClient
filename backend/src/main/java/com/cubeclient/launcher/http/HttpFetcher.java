package com.cubeclient.launcher.http;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface HttpFetcher {
    String getString(String url) throws IOException;
    String getString(String url, Map<String, String> headers) throws IOException;
    void downloadToFile(String url, Path destination) throws IOException;
    String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException;
}
