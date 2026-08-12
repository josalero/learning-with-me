package dev.mytechprofile.research.springai.domain;

import java.util.List;

/**
 * Structured researcher output: answers for each planner question.
 */
public record ResearchFindings(List<Finding> findings) {
}
