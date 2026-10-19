package dev.mytechprofile.langgraph4j.domain;

/**
 * Outcome of one Cursor (or stub) implementation attempt.
 *
 * <pre>{@code
 * ImplementationResult ok = new ImplementationResult(
 *     "stub", "local", "", "Patched PingService", true, "BUILD SUCCESSFUL", "STUB");
 * }</pre>
 */
public record ImplementationResult(
        String agentId,
        String runId,
        String agentUrl,
        String summary,
        boolean passed,
        String verifyOutput,
        String mode) {

    public static ImplementationResult stub(String summary, boolean passed, String verifyOutput) {
        return new ImplementationResult("stub", "local", "", summary, passed, verifyOutput, "STUB");
    }

    public static ImplementationResult cli(String summary, boolean passed, String verifyOutput) {
        return new ImplementationResult("cli", "local", "", summary, passed, verifyOutput, "CLI");
    }
}
