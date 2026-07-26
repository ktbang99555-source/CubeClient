package com.cubeclient.launcher.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Path logFile = workingDir.resolve("logs").resolve("latest.log");
        Files.createDirectories(logFile.getParent());
        return new ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()))
            .start();
    }
}
