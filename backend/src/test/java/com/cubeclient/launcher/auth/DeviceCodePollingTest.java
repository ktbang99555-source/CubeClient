package com.cubeclient.launcher.auth;

import com.cubeclient.launcher.http.HttpFetcher;
import com.cubeclient.launcher.http.HttpFetcher.HttpResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers RFC 8628 device-code polling. The first token request is essentially always rejected
 * with {@code authorization_pending} — the user has not opened the sign-in page yet — so a
 * client that gives up on the first rejection can never log anyone in.
 */
class DeviceCodePollingTest {

    private static final String DEVICE_CODE_JSON = """
        { "device_code": "DCODE", "user_code": "ABCD-EFGH",
          "verification_uri": "https://microsoft.com/link",
          "expires_in": 900, "interval": 5 }
        """;

    private static final String SUCCESS_JSON = """
        { "access_token": "MS_ACCESS_TOKEN", "token_type": "Bearer" }
        """;

    /** Replays a scripted sequence of token-endpoint responses. */
    static class ScriptedFetcher implements HttpFetcher {
        @Override
        public HttpFetcher.HttpResult postJsonAllowingErrors(
                String url, String jsonBody, java.util.Map<String, String> headers) throws java.io.IOException {
            return new HttpFetcher.HttpResult(200, postJson(url, jsonBody, headers));
        }

        final Deque<HttpResult> tokenResponses = new ArrayDeque<>();
        final List<Map<String, String>> tokenRequests = new ArrayList<>();
        HttpResult deviceCodeResponse = new HttpResult(200, DEVICE_CODE_JSON);

        @Override
        public HttpResult postForm(String url, Map<String, String> form) {
            if (url.contains("devicecode")) {
                return deviceCodeResponse;
            }
            tokenRequests.add(Map.copyOf(form));
            if (tokenResponses.isEmpty()) {
                throw new IllegalStateException("polled more times than the script allows");
            }
            return tokenResponses.removeFirst();
        }

        @Override
        public String getString(String url) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public String getString(String url, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void downloadToFile(String url, Path destination) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public String postJson(String url, String jsonBody, Map<String, String> headers) {
            // The chain past the Microsoft token is covered by MicrosoftAuthClientTest; these
            // tests stop once a Microsoft access token has been obtained.
            throw new UnsupportedOperationException("chain stops here");
        }
    }

    private static HttpResult pending() {
        return new HttpResult(400, "{ \"error\": \"authorization_pending\" }");
    }

    private MicrosoftAuthClient clientWith(ScriptedFetcher fetcher, List<Integer> sleeps) {
        return new MicrosoftAuthClient(fetcher, sleeps::add);
    }

    @Test
    void keepsPollingWhileAuthorizationIsPending() {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        fetcher.tokenResponses.add(pending());
        fetcher.tokenResponses.add(pending());
        fetcher.tokenResponses.add(new HttpResult(200, SUCCESS_JSON));
        List<Integer> sleeps = new ArrayList<>();
        MicrosoftAuthClient client = clientWith(fetcher, sleeps);

        // Reaching the Xbox step means the Microsoft token was obtained; postJson then throws.
        assertThrows(UnsupportedOperationException.class,
            () -> client.pollForMinecraftAuth(deviceCode(client)));

        assertEquals(3, fetcher.tokenRequests.size(), "should have polled until success");
        assertEquals(List.of(5, 5), sleeps, "should wait the server's interval between polls");
    }

    @Test
    void sendsTheDeviceCodeGrantOnEveryPoll() {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        fetcher.tokenResponses.add(pending());
        fetcher.tokenResponses.add(new HttpResult(200, SUCCESS_JSON));
        MicrosoftAuthClient client = clientWith(fetcher, new ArrayList<>());

        assertThrows(UnsupportedOperationException.class,
            () -> client.pollForMinecraftAuth(deviceCode(client)));

        for (Map<String, String> request : fetcher.tokenRequests) {
            assertEquals("urn:ietf:params:oauth:grant-type:device_code", request.get("grant_type"));
            assertEquals("DCODE", request.get("device_code"));
            assertTrue(request.containsKey("client_id"));
        }
    }

    // RFC 8628 §3.5: on slow_down the client must increase the interval by 5 seconds.
    @Test
    void backsOffWhenTheServerSaysSlowDown() {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        fetcher.tokenResponses.add(new HttpResult(400, "{ \"error\": \"slow_down\" }"));
        fetcher.tokenResponses.add(pending());
        fetcher.tokenResponses.add(new HttpResult(200, SUCCESS_JSON));
        List<Integer> sleeps = new ArrayList<>();
        MicrosoftAuthClient client = clientWith(fetcher, sleeps);

        assertThrows(UnsupportedOperationException.class,
            () -> client.pollForMinecraftAuth(deviceCode(client)));

        assertEquals(List.of(10, 10), sleeps, "interval should rise from 5 to 10 and stay there");
    }

    @Test
    void stopsWithAClearMessageWhenTheCodeExpires() {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        fetcher.tokenResponses.add(new HttpResult(400, "{ \"error\": \"expired_token\" }"));
        MicrosoftAuthClient client = clientWith(fetcher, new ArrayList<>());

        IOException thrown = assertThrows(IOException.class,
            () -> client.pollForMinecraftAuth(deviceCode(client)));

        assertTrue(thrown.getMessage().toLowerCase().contains("expired"), thrown.getMessage());
    }

    @Test
    void stopsWhenTheUserDeclines() {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        fetcher.tokenResponses.add(new HttpResult(400, "{ \"error\": \"authorization_declined\" }"));
        MicrosoftAuthClient client = clientWith(fetcher, new ArrayList<>());

        IOException thrown = assertThrows(IOException.class,
            () -> client.pollForMinecraftAuth(deviceCode(client)));

        assertTrue(thrown.getMessage().toLowerCase().contains("declined"), thrown.getMessage());
    }

    // A misconfigured Azure app (e.g. public client flows left off) fails here; the operator
    // needs the actual reason, not a generic failure.
    @Test
    void surfacesTheOAuthErrorCodeForUnexpectedFailures() {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        fetcher.tokenResponses.add(new HttpResult(400, """
            { "error": "invalid_client",
              "error_description": "AADSTS7000218: The request body must contain client_assertion." }
            """));
        MicrosoftAuthClient client = clientWith(fetcher, new ArrayList<>());

        IOException thrown = assertThrows(IOException.class,
            () -> client.pollForMinecraftAuth(deviceCode(client)));

        assertTrue(thrown.getMessage().contains("invalid_client"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("AADSTS7000218"), thrown.getMessage());
    }

    @Test
    void requestDeviceCodeSurfacesARejection() {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        fetcher.deviceCodeResponse = new HttpResult(400, """
            { "error": "unauthorized_client",
              "error_description": "AADSTS700016: Application not found in the directory." }
            """);
        MicrosoftAuthClient client = clientWith(fetcher, new ArrayList<>());

        IOException thrown = assertThrows(IOException.class, client::requestDeviceCode);

        assertTrue(thrown.getMessage().contains("unauthorized_client"), thrown.getMessage());
    }

    @Test
    void requestDeviceCodeUsesFormEncodingNotJson() throws IOException {
        // A JSON body is rejected by Microsoft with AADSTS900144; the request must be a form.
        ScriptedFetcher fetcher = new ScriptedFetcher() {
            Map<String, String> deviceForm;

            @Override
            public HttpResult postForm(String url, Map<String, String> form) {
                if (url.contains("devicecode")) {
                    deviceForm = Map.copyOf(form);
                    assertTrue(deviceForm.containsKey("client_id"), "client_id must be a form field");
                    assertEquals("XboxLive.signin offline_access", deviceForm.get("scope"));
                }
                return super.postForm(url, form);
            }
        };
        MicrosoftAuthClient client = clientWith(fetcher, new ArrayList<>());

        var response = client.requestDeviceCode();

        assertEquals("ABCD-EFGH", response.userCode());
        assertEquals(5, response.intervalSeconds());
    }

    private MicrosoftAuthClient.DeviceCodeResponse deviceCode(MicrosoftAuthClient client)
            throws IOException {
        return client.requestDeviceCode();
    }
}
