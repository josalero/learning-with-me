package dev.mytechprofile.sdlc.domain;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic build/test gate result. Not produced by an LLM.
 *
 * <p>Sample: {@code new BuildResult(true, 0, "BUILD SUCCESSFUL", 12_000, false)} or the same result
 * with {@link #withExecutedTests} after the gate reads JUnit XML.
 */
public record BuildResult(
        boolean success,
        int exitCode,
        String truncatedOutput,
        long durationMs,
        boolean timedOut,
        List<String> executedTests) {

    public BuildResult {
        truncatedOutput = Objects.requireNonNullElse(truncatedOutput, "");
        executedTests = executedTests == null ? List.of() : List.copyOf(executedTests);
    }

    /**
     * Creates a result with no executed-test catalog.
     *
     * @param success whether the command exited 0
     * @param exitCode process exit
     * @param truncatedOutput stdout/stderr
     * @param durationMs elapsed time
     * @param timedOut whether the process was killed
     */
    public BuildResult(boolean success, int exitCode, String truncatedOutput, long durationMs, boolean timedOut) {
        this(success, exitCode, truncatedOutput, durationMs, timedOut, List.of());
    }

    public static BuildResult none() {
        return new BuildResult(false, -1, "", 0L, false, List.of());
    }

    public static BuildResult from(CommandResult result) {
        return new BuildResult(
                result.success(),
                result.exitCode(),
                result.output(),
                result.durationMs(),
                result.timedOut(),
                List.of());
    }

    /**
     * Returns a copy that lists tests the gate observed, and appends them to the output QA reads.
     *
     * <p>Sample: {@code result.withExecutedTests(List.of("UserControllerTest.unknownIdReturns404"))}
     * so a later QA turn can PASS without the method name appearing in Gradle's truncated log.
     *
     * @param tests {@code SimpleClass.method} names, already normalized
     * @return this result when {@code tests} is empty; otherwise a copy with the catalog
     */
    public BuildResult withExecutedTests(List<String> tests) {
        List<String> names = tests == null ? List.of() : List.copyOf(tests);
        if (names.isEmpty()) {
            return this;
        }
        String output = truncatedOutput;
        if (!output.contains("Executed tests:")) {
            String block = "Executed tests:" + System.lineSeparator() + String.join(System.lineSeparator(), names);
            output = output.isBlank() ? block : output + System.lineSeparator() + block;
        }
        return new BuildResult(success, exitCode, output, durationMs, timedOut, names);
    }

    /**
     * Returns whether a real test command has run (as opposed to {@link #none()}).
     *
     * @return {@code true} when duration or output is present
     */
    public boolean evaluated() {
        return durationMs > 0 || !truncatedOutput.isBlank();
    }
}
