package dev.mytechprofile.research.langchain4j.domain;

import java.util.List;

/**
 * Wrapper for researcher structured output (LangChain4j collection parsers prefer a POJO root).
 */
public record ResearchFindings(List<Finding> findings) {
}
