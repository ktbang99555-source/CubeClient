package com.cubeclient.launcher.launch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ProcessRunner {
    Process start(List<String> command, Path workingDir) throws IOException;
}
