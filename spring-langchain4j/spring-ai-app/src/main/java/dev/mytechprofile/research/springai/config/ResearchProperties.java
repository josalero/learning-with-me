package dev.mytechprofile.research.springai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "research")
public record ResearchProperties(
        String engine,
        String framework,
        String frameworkVersion,
        String chatModel,
        String researchModel,
        int passThreshold,
        int maxRevisions,
        int defaultDepth,
        long sseTimeoutMs
) {
}
