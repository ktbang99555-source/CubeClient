package com.cubeclient.launcher.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RealProcessRunner implements ProcessRunner {
    @Override
    public Process start(List<String> command, Path workingDir) throws IOException {
        Files.createDirectories(workingDir);
        return new ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .start();
    }
}
