package dev.mytechprofile.research.langchain4j.api;

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
