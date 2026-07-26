package com.cubeclient.launcher.launch;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class RealProcessRunner implements ProcessRunner {

    /**
     * Starts the game with its merged stdout/stderr redirected straight to a log file.
     *
     * <p>The redirect is not a convenience — it is required for correctness. A subprocess whose
     * output nobody consumes blocks forever once the OS pipe buffer fills (a few dozen KB), and
     * Minecraft plus the Fabric loader emit far more than that during startup alone. Left as an
     * unread pipe, the game freezes and {@code Process#waitFor} never returns. Redirecting to a
     * file hands the draining to the OS, so there is no pipe to fill and no reader thread to
     * manage. It also satisfies the design spec's requirement to persist crash logs for the UI's
     * "show log" affordance.
     */
    @Override
    public Process start(List<String> command, Path workingDir) throws IOException {
        Files.createDirectories(workingDir);
        Path logsDir = workingDir.resolve("logs");
        Files.createDirectories(logsDir);

        Path logFile = logsDir.resolve("latest.log");
        rotate(logFile);

        return new ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()))
            .start();
    }

    /**
     * Moves the previous run's log aside before it is truncated.
     *
     * <p>Without this, the common case of "the game crashed, so I launched it again to see what
     * happened" destroys the very log the user needs — the redirect truncates on open.
     */
    private void rotate(Path logFile) throws IOException {
        if (Files.notExists(logFile)) {
            return;
        }
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now());
        Path rotated = logFile.resolveSibling("game-" + stamp + ".log");
        try {
            Files.move(logFile, rotated);
        } catch (FileAlreadyExistsException e) {
            // Two launches inside the same second: keeping the newer log matters more than
            // keeping both, and failing the launch over a log file would be worse.
            Files.move(logFile, rotated, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
