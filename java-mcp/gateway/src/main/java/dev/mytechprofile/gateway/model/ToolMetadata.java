package dev.mytechprofile.gateway.model;

/**
 * Operational catalog metadata that is never sent as model-controlled input.
 *
 * @param owner team or domain responsible for the tool
 * @param version catalog contract version
 * @param deprecated whether new consumers should avoid the tool
 */
public record ToolMetadata(String owner, String version, boolean deprecated) {

    public static ToolMetadata unspecified() {
        return new ToolMetadata("unassigned", "1", false);
    }
}
