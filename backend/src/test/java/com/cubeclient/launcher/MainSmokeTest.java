package com.cubeclient.launcher;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainSmokeTest {
    @Test
    void pingPrintsPongJsonAndReturnsZero() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(captured));
        int exitCode;
        try {
            exitCode = Main.run(new String[] { "ping" });
        } finally {
            System.setOut(original);
        }
        assertEquals(0, exitCode);
        assertTrue(captured.toString().contains("\"type\":\"pong\""));
    }
}
