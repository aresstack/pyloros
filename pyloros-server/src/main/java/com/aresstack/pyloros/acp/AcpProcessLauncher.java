package com.aresstack.pyloros.acp;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AcpProcessLauncher {

    public AcpProcessHandle launch(AcpProcessConfiguration config) {
        AcpProcessConfiguration processConfiguration = Objects.requireNonNull(config, "config must not be null");
        List<String> commandLine = commandLine(processConfiguration);
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);
        applyWorkingDirectory(processConfiguration, processBuilder);
        applyEnvironment(processConfiguration.environment(), processBuilder.environment());

        try {
            return new AcpProcessHandle(processBuilder.start());
        } catch (IOException exception) {
            throw new AcpProcessFailure(reasonFor(exception), processConfiguration.command(), detailFor(exception), exception);
        }
    }

    private static List<String> commandLine(AcpProcessConfiguration config) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(config.command());
        commandLine.addAll(config.args());
        return List.copyOf(commandLine);
    }

    private static void applyWorkingDirectory(AcpProcessConfiguration config, ProcessBuilder processBuilder) {
        String workingDirectory = config.workingDirectory();
        if (workingDirectory == null || workingDirectory.isBlank()) {
            return;
        }

        Path directory;
        try {
            directory = Path.of(workingDirectory);
        } catch (InvalidPathException exception) {
            throw new AcpProcessFailure("working_directory_not_found", config.command(), exception.getMessage(), exception);
        }

        if (!directory.toFile().isDirectory()) {
            throw new AcpProcessFailure("working_directory_not_found", config.command(), "Directory does not exist: " + workingDirectory);
        }

        processBuilder.directory(directory.toFile());
    }

    private static void applyEnvironment(Map<String, String> configuredEnvironment, Map<String, String> processEnvironment) {
        Objects.requireNonNull(configuredEnvironment, "configuredEnvironment must not be null");
        Objects.requireNonNull(processEnvironment, "processEnvironment must not be null");
        processEnvironment.putAll(configuredEnvironment);
    }

    private static String reasonFor(IOException exception) {
        String message = detailFor(exception);
        return message.contains("No such file or directory") ? "command_not_found" : "process_launch_failed";
    }

    private static String detailFor(IOException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
