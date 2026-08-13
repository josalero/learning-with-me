package dev.mytechprofile.sdlc.domain;

import java.util.Objects;

/**
 * Outcome of an allowlisted process invocation.
 *
 * <p>Sample: {@code new CommandResult(0, "ok", 100, false)}.
 */
public record CommandResult(int exitCode, String output, long durationMs, boolean timedOut) {

    public CommandResult {
        output = Objects.requireNonNullElse(output, "");
    }

    public boolean success() {
        return exitCode == 0 && !timedOut;
    }
}
