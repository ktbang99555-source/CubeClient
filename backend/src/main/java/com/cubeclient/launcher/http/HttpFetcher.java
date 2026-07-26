package com.cubeclient.launcher.http;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface HttpFetcher {
    String getString(String url) throws IOException;

    String getString(String url, Map<String, String> headers) throws IOException;

    void downloadToFile(String url, Path destination) throws IOException;

    String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException;

    /**
     * POSTs an {@code application/x-www-form-urlencoded} body.
     *
     * <p>Microsoft's OAuth endpoints reject a JSON body outright — sending one returns
     * {@code AADSTS900144: The request body must contain the following parameter: 'client_id'}.
     * The Xbox and Minecraft endpoints in the same chain do use JSON, so both are needed.
     *
     * <p>Unlike the other methods this does NOT throw on a 4xx. The OAuth device-code flow
     * signals "the user has not finished signing in yet" with an HTTP 400 carrying
     * {@code error=authorization_pending}, so the status and body are both normal protocol
     * output that the caller has to interpret.
     */
    HttpResult postForm(String url, Map<String, String> form) throws IOException;

    /**
     * Like {@link #postJson} but returns the status and body instead of throwing on 4xx/5xx.
     *
     * <p>Needed because the Xbox and Minecraft endpoints put the actual reason for a refusal in
     * the response body ("Invalid app registration", an Xbox {@code XErr} code, and so on).
     * Throwing on the status alone leaves the caller with a bare "returned status 403" and no
     * way to tell the user what to do about it.
     */
    HttpResult postJsonAllowingErrors(String url, String jsonBody, Map<String, String> headers)
        throws IOException;

    record HttpResult(int statusCode, String body) {}
}
