package dev.mytechprofile.research.langchain4j.domain;

/**
 * Application command to run the research pipeline (no transport validation).
 */
public record ResearchCommand(String topic, Integer depth) {
}
