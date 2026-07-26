package com.cubeclient.launcher.auth;

import com.cubeclient.launcher.http.HttpFetcher;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Map;

public class MicrosoftAuthClient {
    /**
     * Azure AD application (client) ID for CubeClient.
     *
     * <p>Not a secret. This is a public client — the device-code flow has no client secret by
     * design, and the ID is embedded in every distributed copy of the app. The Azure app must
     * have "Allow public client flows" enabled or every token request is rejected.
     */
    private static final String CLIENT_ID = "d6c02d3d-dc56-4266-b9c8-d2a7299ef9d3";

    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code";

    private static final String DEVICE_CODE_URL =
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
        "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    /** Lets tests drive the polling loop without spending real seconds. */
    @FunctionalInterface
    public interface Sleeper {
        void sleepSeconds(int seconds) throws InterruptedException;
    }

    private final HttpFetcher fetcher;
    private final Sleeper sleeper;

    public MicrosoftAuthClient(HttpFetcher fetcher) {
        this(fetcher, seconds -> Thread.sleep(seconds * 1000L));
    }

    MicrosoftAuthClient(HttpFetcher fetcher, Sleeper sleeper) {
        this.fetcher = fetcher;
        this.sleeper = sleeper;
    }

    public record DeviceCodeResponse(
        String deviceCode, String userCode, String verificationUri, int expiresIn, int intervalSeconds
    ) {}

    public record MinecraftAuthResult(String accessToken, String uuid, String username) {}

    public DeviceCodeResponse requestDeviceCode() throws IOException {
        // Form-encoded, not JSON: a JSON body here is rejected with
        // "AADSTS900144: The request body must contain the following parameter: 'client_id'".
        var response = fetcher.postForm(DEVICE_CODE_URL, Map.of(
            "client_id", CLIENT_ID,
            "scope", SCOPE
        ));
        JsonObject json = parseOrThrow(response.body(), DEVICE_CODE_URL);

        if (response.statusCode() / 100 != 2) {
            throw new IOException("Device code request rejected: " + describeOAuthError(json));
        }
        return new DeviceCodeResponse(
            json.get("device_code").getAsString(),
            json.get("user_code").getAsString(),
            json.get("verification_uri").getAsString(),
            json.get("expires_in").getAsInt(),
            json.get("interval").getAsInt()
        );
    }

    public MinecraftAuthResult pollForMinecraftAuth(DeviceCodeResponse deviceCode) throws IOException {
        String msAccessToken = exchangeDeviceCodeForMicrosoftToken(deviceCode);
        XboxAuth xbl = authenticateWithXboxLive(msAccessToken);
        XboxAuth xsts = authorizeWithXsts(xbl.token());
        String minecraftAccessToken = loginWithXbox(xsts.userHash(), xsts.token());
        return fetchProfileAndBuildResult(minecraftAccessToken);
    }

    /**
     * Polls the token endpoint until the user finishes signing in, per RFC 8628 §3.5.
     *
     * <p>The first request is essentially always rejected with {@code authorization_pending} —
     * the user has not even opened the page yet. Treating that first rejection as a failure
     * (which an earlier version did) makes login impossible.
     */
    private String exchangeDeviceCodeForMicrosoftToken(DeviceCodeResponse deviceCode)
            throws IOException {
        int intervalSeconds = Math.max(1, deviceCode.intervalSeconds());
        // Bound the loop by the server's own expiry so a user who walks away cannot leave the
        // launcher polling forever.
        long deadlineNanos = System.nanoTime() + deviceCode.expiresIn() * 1_000_000_000L;

        while (true) {
            var response = fetcher.postForm(TOKEN_URL, Map.of(
                "client_id", CLIENT_ID,
                "device_code", deviceCode.deviceCode(),
                "grant_type", DEVICE_CODE_GRANT
            ));
            JsonObject json = parseOrThrow(response.body(), TOKEN_URL);

            if (response.statusCode() / 100 == 2) {
                return json.get("access_token").getAsString();
            }

            String error = json.has("error") ? json.get("error").getAsString() : "";
            switch (error) {
                case "authorization_pending" -> { /* keep waiting */ }
                // The server is telling us we are hammering it; RFC 8628 says add 5 seconds.
                case "slow_down" -> intervalSeconds += 5;
                case "expired_token" -> throw new IOException(
                    "The sign-in code expired before it was used. Start the login again.");
                case "authorization_declined", "access_denied" -> throw new IOException(
                    "Sign-in was declined.");
                default -> throw new IOException("Sign-in failed: " + describeOAuthError(json));
            }

            if (System.nanoTime() >= deadlineNanos) {
                throw new IOException("The sign-in code expired before it was used. "
                    + "Start the login again.");
            }
            try {
                sleeper.sleepSeconds(intervalSeconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for sign-in", e);
            }
        }
    }

    private JsonObject parseOrThrow(String body, String url) throws IOException {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            // Does not echo the body: these responses can carry tokens.
            throw new IOException("Malformed response from " + url, e);
        }
    }

    /** OAuth error codes and descriptions are diagnostics, not credentials — safe to surface. */
    private String describeOAuthError(JsonObject json) {
        String code = json.has("error") ? json.get("error").getAsString() : "unknown_error";
        if (json.has("error_description")) {
            // Azure descriptions are multi-line with trace ids; the first line is the useful part.
            String description = json.get("error_description").getAsString();
            int newline = description.indexOf('\n');
            return code + ": " + (newline > 0 ? description.substring(0, newline) : description);
        }
        return code;
    }

    private record XboxAuth(String token, String userHash) {}

    private XboxAuth authenticateWithXboxLive(String msAccessToken) throws IOException {
        String body = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
            + "\"RpsTicket\":\"d=" + msAccessToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\","
            + "\"TokenType\":\"JWT\"}";
        var response = fetcher.postJsonAllowingErrors(XBL_AUTH_URL, body, Map.of());
        if (response.statusCode() / 100 != 2) {
            throw new IOException(describeXboxRefusal("Xbox Live", response.statusCode(), response.body()));
        }
        return parseXboxAuth(response.body(), XBL_AUTH_URL);
    }

    private XboxAuth authorizeWithXsts(String xblToken) throws IOException {
        String body = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},"
            + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
        var response = fetcher.postJsonAllowingErrors(XSTS_AUTH_URL, body, Map.of());
        if (response.statusCode() / 100 != 2) {
            throw new IOException(describeXboxRefusal("XSTS", response.statusCode(), response.body()));
        }
        return parseXboxAuth(response.body(), XSTS_AUTH_URL);
    }

    /**
     * Turns an Xbox refusal into something the person signing in can act on.
     *
     * <p>Xbox answers a rejected authorisation with HTTP 401 and an {@code XErr} number that says
     * exactly what is wrong with the account. Reporting only "returned status 401" sends the user
     * looking for a bug in the launcher when the fix is on their Microsoft account.
     *
     * <p>The {@code XErr} number and Xbox's own redirect URL are diagnostics, not credentials.
     */
    private String describeXboxRefusal(String stage, int statusCode, String body) {
        long xErr = 0;
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("XErr")) {
                xErr = json.get("XErr").getAsLong();
            }
        } catch (RuntimeException ignored) {
            // Non-JSON body; the status code is all there is.
        }

        String advice = switch ((int) xErr) {
            case 0 -> "";
            // Documented Xbox authorisation failures.
            case (int) 2148916233L -> " This Microsoft account has no Xbox profile. "
                + "Sign in once at https://www.xbox.com to create one, then try again.";
            case (int) 2148916235L -> " Xbox Live is not available in this account's country or region.";
            case (int) 2148916236L, (int) 2148916237L ->
                " This account needs adult verification before it can be used.";
            case (int) 2148916238L -> " This account belongs to someone under 18. "
                + "An adult must add it to a Microsoft Family group before it can sign in.";
            default -> " Xbox error code " + xErr + ".";
        };

        return "Sign-in was refused at the " + stage + " step (HTTP " + statusCode + ")." + advice;
    }

    private XboxAuth parseXboxAuth(String response, String url) throws IOException {
        JsonObject json = parseOrThrow(response, url);
        try {
            String token = json.get("Token").getAsString();
            String userHash = json.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
            return new XboxAuth(token, userHash);
        } catch (RuntimeException e) {
            throw new IOException("Unexpected response shape from " + url, e);
        }
    }

    private String loginWithXbox(String userHash, String xstsToken) throws IOException {
        String body = "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}";
        var response = fetcher.postJsonAllowingErrors(MC_LOGIN_URL, body, Map.of());

        if (response.statusCode() / 100 != 2) {
            throw new IOException(describeMinecraftRefusal(response.statusCode(), response.body()));
        }
        return parseOrThrow(response.body(), MC_LOGIN_URL).get("access_token").getAsString();
    }

    /**
     * Turns a Minecraft Services refusal into something the operator can act on.
     *
     * <p>A 403 here almost always means the Azure application has not been approved for
     * Minecraft authentication — every earlier step (Microsoft token, Xbox Live, XSTS) succeeds,
     * so the bare status code points at the wrong place entirely.
     *
     * <p>Only the service's own diagnostic fields are surfaced, never the raw body: the request
     * that produced it carried an identity token.
     */
    private String describeMinecraftRefusal(int statusCode, String body) {
        String detail = "";
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            for (String field : new String[] { "errorMessage", "error", "errorType", "message" }) {
                if (json.has(field) && json.get(field).isJsonPrimitive()) {
                    detail = json.get(field).getAsString();
                    if (field.equals("errorMessage")) break;
                }
            }
        } catch (RuntimeException ignored) {
            // Non-JSON body; the status code is all we can report.
        }

        StringBuilder message = new StringBuilder("Minecraft sign-in was refused (HTTP ")
            .append(statusCode).append(")");
        if (!detail.isBlank()) {
            message.append(": ").append(detail);
        }
        if (statusCode == 403) {
            message.append(". This usually means the Azure application is not approved for "
                + "Minecraft authentication — see https://aka.ms/AppRegInfo. It can also mean the "
                + "signed-in account does not own Minecraft: Java Edition.");
        }
        return message.toString();
    }

    private MinecraftAuthResult fetchProfileAndBuildResult(String minecraftAccessToken) throws IOException {
        String profileJson = fetchMinecraftProfile(minecraftAccessToken);
        JsonObject json = JsonParser.parseString(profileJson).getAsJsonObject();
        return new MinecraftAuthResult(
            minecraftAccessToken,
            json.get("id").getAsString(),
            json.get("name").getAsString()
        );
    }

    /**
     * The Minecraft Services profile endpoint is a GET with a bearer header — not a POST.
     * Sending POST here returns an error from the live API even when every preceding step
     * succeeded, so this must stay a GET.
     */
    private String fetchMinecraftProfile(String minecraftAccessToken) throws IOException {
        return fetcher.getString(MC_PROFILE_URL, Map.of("Authorization", "Bearer " + minecraftAccessToken));
    }
}
