package dev.mytechprofile.langgraph4j.api;

import java.util.List;

import dev.mytechprofile.langgraph4j.domain.Decision;

/**
 * One-click recipe for the lab UI scenario picker.
 *
 * <p>Sample: {@code interrupt-only} starts the graph and leaves it at {@code WAITING_APPROVAL}.
 */
public record LabScenario(
        String id,
        int step,
        String title,
        String purpose,
        String brief,
        Decision autoDecision,
        String promptOverride,
        String expectedStatus,
        List<String> watchFor,
        boolean worksInStub,
        String durationHint,
        String accent) {}
