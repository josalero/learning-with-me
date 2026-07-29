package dev.mytechprofile.gateway.model;

/**
 * Tool access mode from configuration.
 *
 * <p>{@link #WRITE} tools must set {@code requires-approval: true} (enforced at compile).
 */
public enum ToolMode {
    READ,
    WRITE
}
