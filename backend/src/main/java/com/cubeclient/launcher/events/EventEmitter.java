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
