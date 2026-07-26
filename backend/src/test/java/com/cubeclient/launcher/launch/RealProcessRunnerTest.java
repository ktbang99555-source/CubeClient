package com.cubeclient.launcher.launch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealProcessRunnerTest {

    @TempDir
    Path tempDir;

    /** A trivial OS command so the runner has something real to start. */
    private static List<String> trivialCommand() {
        return System.getProperty("os.name").toLowerCase().contains("win")
            ? List.of("cmd", "/c", "exit 0")
            : List.of("true");
    }

    @Test
    void redirectsGameOutputToALogFile() throws Exception {
        Process process = new RealProcessRunner().start(trivialCommand(), tempDir);
        process.waitFor();

        assertTrue(Files.exists(tempDir.resolve(Path.of("logs", "latest.log"))));
    }

    // "The game crashed, so I ran it again to see why" must not destroy the crash log — the
    // redirect truncates latest.log on open.
    @Test
    void keepsThePreviousRunsLogInsteadOfTruncatingIt() throws Exception {
        Path logFile = tempDir.resolve(Path.of("logs", "latest.log"));
        Files.createDirectories(logFile.getParent());
        Files.writeString(logFile, "CRASH FROM THE PREVIOUS RUN");

        new RealProcessRunner().start(trivialCommand(), tempDir).waitFor();

        try (Stream<Path> logs = Files.list(logFile.getParent())) {
            List<Path> rotated = logs
                .filter(p -> p.getFileName().toString().startsWith("game-"))
                .toList();
            assertEquals(1, rotated.size(), "previous log should have been rotated aside");
            assertEquals("CRASH FROM THE PREVIOUS RUN", Files.readString(rotated.get(0)));
        }
    }

    @Test
    void createsTheWorkingDirectoryIfMissing() throws Exception {
        Path gameDir = tempDir.resolve("instances").resolve("new-profile");

        new RealProcessRunner().start(trivialCommand(), gameDir).waitFor();

        assertTrue(Files.isDirectory(gameDir));
    }
}
