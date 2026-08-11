package dev.mytechprofile.research.springai.api;

/**
 * Engine identity returned by {@code GET /api/v1/meta}.
 */
public record EngineMeta(
        String engine,
        String framework,
        String frameworkVersion,
        String chatModel,
        String researchModel
) {
}
