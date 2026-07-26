package com.cubeclient.launcher.auth;

/**
 * The account the game is launched as.
 *
 * <p>{@code accessToken} is a credential: it must never be emitted as an event, written to a
 * log, or placed in an exception message. It does end up on the game's command line, because
 * that is the only way Minecraft accepts it.
 */
public record Session(String username, String uuid, String accessToken, boolean online) {

    /**
     * A session with no real credentials. The game starts and singleplayer works, but servers
     * reject it — so this is a fallback for "not logged in", not a supported way to play.
     */
    public static Session offline(String username) {
        return new Session(username, "0".repeat(32), "0", false);
    }

    public static Session online(String username, String uuid, String accessToken) {
        return new Session(username, uuid, accessToken, true);
    }

    /** Keeps the token out of logs and crash dumps if this record is ever printed. */
    @Override
    public String toString() {
        return "Session[username=" + username + ", uuid=" + uuid
            + ", accessToken=<redacted>, online=" + online + "]";
    }
}
