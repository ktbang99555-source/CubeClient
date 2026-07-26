package com.cubeclient.launcher.auth;

import com.cubeclient.launcher.http.HttpFetcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xbox rejects an authorisation with HTTP 401 plus an {@code XErr} number naming the actual
 * problem with the account. Reporting only the status code sends the user hunting for a launcher
 * bug when the fix is on their Microsoft account.
 */
class XboxRefusalTest {

    private static final String DEVICE_CODE_JSON = """
        { "device_code": "DCODE", "user_code": "ABCD-EFGH",
          "verification_uri": "https://microsoft.com/link",
          "expires_in": 900, "interval": 5 }
        """;

    private static final String MS_TOKEN_JSON = """
        { "access_token": "MS_TOKEN" }
        """;

    private static final String XBOX_OK_JSON = """
        { "Token": "T", "DisplayClaims": { "xui": [ { "uhs": "U" } ] } }
        """;

    /** Succeeds through the Microsoft token, then refuses at the Xbox step under test. */
    static class RefusingFetcher implements HttpFetcher {
        private final String refusingUrlPart;
        private final String refusalBody;

        RefusingFetcher(String refusingUrlPart, String refusalBody) {
            this.refusingUrlPart = refusingUrlPart;
            this.refusalBody = refusalBody;
        }

        @Override
        public HttpResult postForm(String url, Map<String, String> form) {
            return url.contains("devicecode")
                ? new HttpResult(200, DEVICE_CODE_JSON)
                : new HttpResult(200, MS_TOKEN_JSON);
        }

        @Override
        public HttpResult postJsonAllowingErrors(String url, String body, Map<String, String> h) {
            return url.contains(refusingUrlPart)
                ? new HttpResult(401, refusalBody)
                : new HttpResult(200, XBOX_OK_JSON);
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
            throw new UnsupportedOperationException("not used");
        }
    }

    private IOException signInFailure(String urlPart, String body) {
        MicrosoftAuthClient client =
            new MicrosoftAuthClient(new RefusingFetcher(urlPart, body), seconds -> { });
        return assertThrows(IOException.class,
            () -> client.pollForMinecraftAuth(client.requestDeviceCode()));
    }

    @Test
    void explainsThatTheAccountHasNoXboxProfile() {
        IOException thrown = signInFailure("xsts.auth", "{ \"XErr\": 2148916233 }");
        assertTrue(thrown.getMessage().contains("no Xbox profile"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("xbox.com"), thrown.getMessage());
    }

    @Test
    void explainsThatAChildAccountNeedsAFamilyGroup() {
        IOException thrown = signInFailure("xsts.auth", "{ \"XErr\": 2148916238 }");
        assertTrue(thrown.getMessage().contains("under 18"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Family"), thrown.getMessage());
    }

    @Test
    void namesTheStepThatRefused() {
        IOException thrown = signInFailure("xsts.auth", "{ \"XErr\": 2148916235 }");
        assertTrue(thrown.getMessage().contains("XSTS"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("region"), thrown.getMessage());
    }

    @Test
    void stillReportsAnUnknownCodeRatherThanSwallowingIt() {
        IOException thrown = signInFailure("xsts.auth", "{ \"XErr\": 999999 }");
        assertTrue(thrown.getMessage().contains("999999"), thrown.getMessage());
    }

    @Test
    void handlesARefusalWithNoParseableBody() {
        IOException thrown = signInFailure("xsts.auth", "not json at all");
        assertTrue(thrown.getMessage().contains("401"), thrown.getMessage());
    }

    @Test
    void alsoCoversTheEarlierXboxLiveStep() {
        IOException thrown = signInFailure("user.auth", "{ \"XErr\": 2148916233 }");
        assertTrue(thrown.getMessage().contains("Xbox Live"), thrown.getMessage());
    }
}
