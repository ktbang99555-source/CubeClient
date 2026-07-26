package com.cubeclient.launcher.events;

import com.cubeclient.launcher.profile.Profile;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.PrintStream;
import java.util.List;

public class EventEmitter {
    private static final Gson GSON = new Gson();
    private final PrintStream out;

    public EventEmitter(PrintStream out) {
        this.out = out;
    }

    public void progress(String stage, int percent) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "progress");
        event.addProperty("stage", stage);
        event.addProperty("percent", percent);
        write(event);
    }

    public void error(String stage, String message) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "error");
        event.addProperty("stage", stage);
        event.addProperty("message", message);
        write(event);
    }

    public void profiles(List<Profile> profiles) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "profiles");
        event.add("profiles", GSON.toJsonTree(profiles));
        write(event);
    }

    /**
     * Carries the Minecraft access token to the Electron parent so it can be encrypted.
     *
     * <p>This is the one event that contains a credential. It travels over the private stdout
     * pipe between parent and child, is never written to a file by the backend, and must be
     * consumed by the Electron main process rather than forwarded to the renderer.
     */
    public void authResult(String username, String uuid, String accessToken) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "auth_result");
        event.addProperty("username", username);
        event.addProperty("uuid", uuid);
        event.addProperty("accessToken", accessToken);
        write(event);
    }

    /** The code the user types at Microsoft's page, plus where to type it. Not a credential. */
    public void deviceCode(String userCode, String verificationUri) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "device_code");
        event.addProperty("userCode", userCode);
        event.addProperty("verificationUri", verificationUri);
        write(event);
    }

    public void launched() {
        JsonObject event = new JsonObject();
        event.addProperty("type", "launched");
        write(event);
    }

    public void exited(int code) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "exited");
        event.addProperty("code", code);
        write(event);
    }

    private void write(JsonObject event) {
        out.println(GSON.toJson(event));
        out.flush();
    }
}
