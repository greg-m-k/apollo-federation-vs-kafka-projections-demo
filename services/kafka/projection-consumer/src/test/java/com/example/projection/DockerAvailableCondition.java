package com.example.projection;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;

/**
 * JUnit 5 condition that checks if Docker is available.
 * Tests using this condition will be skipped if Docker is not running.
 */
public class DockerAvailableCondition implements ExecutionCondition {

    private static Boolean dockerAvailable;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (isDockerAvailable()) {
            return ConditionEvaluationResult.enabled("Docker is available");
        }
        return ConditionEvaluationResult.disabled("Docker is not available - skipping test");
    }

    private static synchronized boolean isDockerAvailable() {
        if (dockerAvailable == null) {
            dockerAvailable = checkDocker();
        }
        return dockerAvailable;
    }

    private static boolean checkDocker() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
