package dev.mytechprofile.langgraph4j.domain;

/**
 * Lifecycle of a LangGraph4j implementation thread.
 */
public enum TaskStatus {
    WAITING_APPROVAL,
    IMPLEMENTING,
    VERIFYING,
    DONE,
    FAILED,
    REJECTED
}
