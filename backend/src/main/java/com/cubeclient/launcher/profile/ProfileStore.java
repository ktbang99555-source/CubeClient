package com.cubeclient.launcher.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProfileStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PROFILE_LIST_TYPE = new TypeToken<List<Profile>>() {}.getType();

    private final Path profilesJsonPath;

    public ProfileStore(Path profilesJsonPath) {
        this.profilesJsonPath = profilesJsonPath;
    }

    public List<Profile> loadAll() throws IOException {
        if (!Files.exists(profilesJsonPath)) {
            return List.of();
        }
        String json = Files.readString(profilesJsonPath);
        try {
            List<Profile> profiles = GSON.fromJson(json, PROFILE_LIST_TYPE);
            return profiles == null ? List.of() : profiles;
        } catch (JsonSyntaxException e) {
            throw new IOException("Malformed profiles JSON at " + profilesJsonPath + ": " + e.getMessage(), e);
        }
    }

    public void saveAll(List<Profile> profiles) throws IOException {
        if (profilesJsonPath.getParent() != null) {
            Files.createDirectories(profilesJsonPath.getParent());
        }
        Files.writeString(profilesJsonPath, GSON.toJson(profiles));
    }
}
