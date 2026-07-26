package com.cubeclient.launcher.http;

import java.io.IOException;
import java.nio.file.Path;

public interface HttpFetcher {
    String getString(String url) throws IOException;
    void downloadToFile(String url, Path destination) throws IOException;
}
