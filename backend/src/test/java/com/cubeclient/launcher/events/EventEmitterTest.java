package com.cubeclient.launcher.events;

import com.cubeclient.launcher.profile.Profile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventEmitterTest {

    @Test
    void progressWritesOneJsonLine() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EventEmitter emitter = new EventEmitter(new PrintStream(out));

        emitter.progress("libraries", 42);

        String line = out.toString().strip();
        assertEquals(1, line.split("\n").length);
        assertTrue(line.contains("\"type\":\"progress\""));
        assertTrue(line.contains("\"stage\":\"libraries\""));
        assertTrue(line.contains("\"percent\":42"));
    }

    @Test
    void errorWritesTypeAndMessage() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EventEmitter emitter = new EventEmitter(new PrintStream(out));

        emitter.error("auth", "session expired");

        String line = out.toString().strip();
        assertTrue(line.contains("\"type\":\"error\""));
        assertTrue(line.contains("\"stage\":\"auth\""));
        assertTrue(line.contains("\"message\":\"session expired\""));
    }

    @Test
    void profilesWritesProfileArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EventEmitter emitter = new EventEmitter(new PrintStream(out));

        emitter.profiles(List.of(new Profile("latest-1.21", "1.21.4", "fabric", List.of("minimap"))));

        String line = out.toString().strip();
        assertTrue(line.contains("\"type\":\"profiles\""));
        assertTrue(line.contains("\"mcVersion\":\"1.21.4\""));
    }

    // Main built this line by string concatenation, bypassing Gson. A quote or backslash
    // in either field would produce a line Electron cannot parse, and the sign-in would
    // stall with no visible cause.
    @Test
    void deviceCodeIsEscapedLikeEveryOtherEvent() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EventEmitter emitter = new EventEmitter(new PrintStream(out));

        emitter.deviceCode("AB\"CD", "https://example.com/a\b");

        String line = out.toString().strip();
        assertEquals(1, line.split("\n").length);
        assertTrue(line.contains("\"type\":\"device_code\""));
        // Gson escapes the quote rather than ending the string early.
        assertTrue(line.contains("AB\\\"CD"), line);
    }
}
