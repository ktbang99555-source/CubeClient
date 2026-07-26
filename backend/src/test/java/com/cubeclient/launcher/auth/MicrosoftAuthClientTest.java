package com.cubeclient.launcher.auth;

import com.cubeclient.launcher.http.HttpFetcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrosoftAuthClientTest {

    static class ScriptedFetcher implements HttpFetcher {
        @Override
        public String getString(String url) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void downloadToFile(String url, Path destination) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
            if (url.contains("devicecode")) {
                return """
                    { "device_code": "DCODE", "user_code": "ABCD-EFGH",
                      "verification_uri": "https://microsoft.com/devicelogin",
                      "expires_in": 900, "interval": 5 }
                    """;
            }
            if (url.contains("/token")) {
                return """
                    { "access_token": "MS_ACCESS_TOKEN", "token_type": "Bearer" }
                    """;
            }
            if (url.contains("xboxlive.com")) {
                return """
                    { "Token": "XBL_TOKEN", "DisplayClaims": { "xui": [ { "uhs": "USER_HASH" } ] } }
                    """;
            }
            if (url.contains("xsts.auth.xboxlive.com")) {
                return """
                    { "Token": "XSTS_TOKEN", "DisplayClaims": { "xui": [ { "uhs": "USER_HASH" } ] } }
                    """;
            }
            if (url.contains("login_with_xbox")) {
                return """
                    { "access_token": "MC_ACCESS_TOKEN" }
                    """;
            }
            throw new IllegalStateException("Unexpected POST url: " + url);
        }
    }

    @Test
    void pollForMinecraftAuthChainsAllStepsAndReturnsResult() throws IOException {
        MicrosoftAuthClient client = new MicrosoftAuthClient(new ScriptedFetcher()) {
            @Override
            protected String fetchMinecraftProfile(String minecraftAccessToken) {
                return """
                    { "id": "abc123uuid", "name": "Steve" }
                    """;
            }
        };

        MicrosoftAuthClient.DeviceCodeResponse deviceCode = client.requestDeviceCode();
        assertEquals("ABCD-EFGH", deviceCode.userCode());

        MicrosoftAuthClient.MinecraftAuthResult result = client.pollForMinecraftAuth(deviceCode);

        assertEquals("MC_ACCESS_TOKEN", result.accessToken());
        assertEquals("abc123uuid", result.uuid());
        assertEquals("Steve", result.username());
    }
}
