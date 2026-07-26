package com.cubeclient.launcher.auth;

import com.cubeclient.launcher.http.HttpFetcher;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Map;

public class MicrosoftAuthClient {
    private static final String CLIENT_ID = "CUBECLIENT_AZURE_APP_ID"; // replace with real registered app ID
    private static final String DEVICE_CODE_URL =
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
        "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private final HttpFetcher fetcher;

    public MicrosoftAuthClient(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public record DeviceCodeResponse(
        String deviceCode, String userCode, String verificationUri, int expiresIn, int intervalSeconds
    ) {}

    public record MinecraftAuthResult(String accessToken, String uuid, String username) {}

    public DeviceCodeResponse requestDeviceCode() throws IOException {
        String body = "{\"client_id\":\"" + CLIENT_ID + "\",\"scope\":\"XboxLive.signin offline_access\"}";
        String response = fetcher.postJson(DEVICE_CODE_URL, body, Map.of());
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        return new DeviceCodeResponse(
            json.get("device_code").getAsString(),
            json.get("user_code").getAsString(),
            json.get("verification_uri").getAsString(),
            json.get("expires_in").getAsInt(),
            json.get("interval").getAsInt()
        );
    }

    public MinecraftAuthResult pollForMinecraftAuth(DeviceCodeResponse deviceCode) throws IOException {
        String msAccessToken = exchangeDeviceCodeForMicrosoftToken(deviceCode.deviceCode());
        XboxAuth xbl = authenticateWithXboxLive(msAccessToken);
        XboxAuth xsts = authorizeWithXsts(xbl.token());
        String minecraftAccessToken = loginWithXbox(xsts.userHash(), xsts.token());
        return fetchProfileAndBuildResult(minecraftAccessToken);
    }

    private String exchangeDeviceCodeForMicrosoftToken(String deviceCode) throws IOException {
        String body = "{\"client_id\":\"" + CLIENT_ID + "\",\"device_code\":\"" + deviceCode
            + "\",\"grant_type\":\"urn:ietf:params:oauth:grant-type:device_code\"}";
        String response = fetcher.postJson(TOKEN_URL, body, Map.of());
        return JsonParser.parseString(response).getAsJsonObject().get("access_token").getAsString();
    }

    private record XboxAuth(String token, String userHash) {}

    private XboxAuth authenticateWithXboxLive(String msAccessToken) throws IOException {
        String body = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
            + "\"RpsTicket\":\"d=" + msAccessToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\","
            + "\"TokenType\":\"JWT\"}";
        String response = fetcher.postJson(XBL_AUTH_URL, body, Map.of());
        return parseXboxAuth(response);
    }

    private XboxAuth authorizeWithXsts(String xblToken) throws IOException {
        String body = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},"
            + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
        String response = fetcher.postJson(XSTS_AUTH_URL, body, Map.of());
        return parseXboxAuth(response);
    }

    private XboxAuth parseXboxAuth(String response) {
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        String token = json.get("Token").getAsString();
        String userHash = json.getAsJsonObject("DisplayClaims")
            .getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
        return new XboxAuth(token, userHash);
    }

    private String loginWithXbox(String userHash, String xstsToken) throws IOException {
        String body = "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}";
        String response = fetcher.postJson(MC_LOGIN_URL, body, Map.of());
        return JsonParser.parseString(response).getAsJsonObject().get("access_token").getAsString();
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
