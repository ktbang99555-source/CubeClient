package com.cubeclient.launcher.auth;

import com.cubeclient.launcher.http.HttpFetcher;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrosoftAuthClientTest {

    static class ScriptedFetcher implements HttpFetcher {
        /** Xbox and Minecraft steps go through here so the client can read refusal bodies. */
        @Override
        public HttpResult postJsonAllowingErrors(
                String url, String jsonBody, Map<String, String> headers) throws IOException {
            return new HttpResult(200, postJson(url, jsonBody, headers));
        }


        String minecraftLoginIdentityToken;
        String profileAuthorizationHeader;

        /**
         * The two Microsoft OAuth endpoints are form-encoded; the Xbox and Minecraft endpoints
         * that follow are JSON. Sending JSON to these two is rejected with AADSTS900144.
         */
        @Override
        public HttpResult postForm(String url, Map<String, String> form) {
            if (url.contains("devicecode")) {
                return new HttpResult(200, """
                    { "device_code": "DCODE", "user_code": "ABCD-EFGH",
                      "verification_uri": "https://microsoft.com/link",
                      "expires_in": 900, "interval": 5 }
                    """);
            }
            if (url.contains("/token")) {
                return new HttpResult(200, """
                    { "access_token": "MS_ACCESS_TOKEN", "token_type": "Bearer" }
                    """);
            }
            throw new IllegalStateException("Unexpected form POST url: " + url);
        }

        @Override
        public String getString(String url) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public String getString(String url, Map<String, String> headers) {
            if (url.contains("minecraft/profile")) {
                profileAuthorizationHeader = headers.get("Authorization");
                return """
                    { "id": "abc123uuid", "name": "Steve" }
                    """;
            }
            throw new IllegalStateException("Unexpected authenticated GET url: " + url);
        }

        @Override
        public void downloadToFile(String url, Path destination) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
            // Order matters: the XSTS host CONTAINS "xboxlive.com", so it must be matched first
            // or its branch is unreachable and every XSTS call silently gets the XBL response.
            if (url.contains("xsts.auth.xboxlive.com")) {
                return """
                    { "Token": "XSTS_TOKEN", "DisplayClaims": { "xui": [ { "uhs": "XSTS_USER_HASH" } ] } }
                    """;
            }
            if (url.contains("user.auth.xboxlive.com")) {
                return """
                    { "Token": "XBL_TOKEN", "DisplayClaims": { "xui": [ { "uhs": "XBL_USER_HASH" } ] } }
                    """;
            }
            if (url.contains("login_with_xbox")) {
                // Capture the identity token so the test can prove which token/hash pair was used.
                minecraftLoginIdentityToken =
                    JsonParser.parseString(jsonBody).getAsJsonObject().get("identityToken").getAsString();
                return """
                    { "access_token": "MC_ACCESS_TOKEN" }
                    """;
            }
            throw new IllegalStateException("Unexpected POST url: " + url);
        }
    }

    @Test
    void pollForMinecraftAuthChainsAllStepsAndReturnsResult() throws IOException {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        MicrosoftAuthClient client = new MicrosoftAuthClient(fetcher);

        MicrosoftAuthClient.DeviceCodeResponse deviceCode = client.requestDeviceCode();
        assertEquals("ABCD-EFGH", deviceCode.userCode());

        MicrosoftAuthClient.MinecraftAuthResult result = client.pollForMinecraftAuth(deviceCode);

        assertEquals("MC_ACCESS_TOKEN", result.accessToken());
        assertEquals("abc123uuid", result.uuid());
        assertEquals("Steve", result.username());

        // The identity token must be built from the XSTS token and XSTS user hash — NOT the XBL
        // ones. Distinct canned values make a swapped-step regression fail here instead of passing
        // silently.
        assertEquals("XBL3.0 x=XSTS_USER_HASH;XSTS_TOKEN", fetcher.minecraftLoginIdentityToken);

        // The profile must be fetched with an authenticated GET carrying the Minecraft token.
        assertEquals("Bearer MC_ACCESS_TOKEN", fetcher.profileAuthorizationHeader);
    }
}
