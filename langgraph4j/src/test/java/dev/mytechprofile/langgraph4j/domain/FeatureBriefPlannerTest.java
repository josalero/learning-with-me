package dev.mytechprofile.langgraph4j.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeatureBriefPlannerTest {

    @Test
    void planIncludesBriefAndVerifyCommand() {
        String prompt = FeatureBriefPlanner.plan("Return ok from PingService");

        assertTrue(prompt.contains("Return ok from PingService"));
        assertTrue(prompt.contains("./gradlew test"));
        assertTrue(prompt.contains("ping-service-java"));
    }

    @Test
    void blankBriefUsesDefault() {
        String prompt = FeatureBriefPlanner.plan("  ");

        assertTrue(prompt.contains("PingService.status()"));
        assertTrue(prompt.contains(FeatureBriefPlanner.DEFAULT_BRIEF.substring(0, 20)));
    }
}
