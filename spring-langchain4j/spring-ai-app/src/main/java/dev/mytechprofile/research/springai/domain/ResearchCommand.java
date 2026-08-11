package dev.mytechprofile.research.springai.domain;

/**
 * Application command to run the research pipeline (no transport validation).
 */
public record ResearchCommand(String topic, Integer depth) {
}
