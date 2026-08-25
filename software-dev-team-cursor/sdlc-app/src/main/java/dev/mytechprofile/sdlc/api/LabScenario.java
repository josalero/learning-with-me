package dev.mytechprofile.sdlc.api;

import java.util.List;

/**
 * One-click POC recipe for the Samples tab.
 *
 * <p>Sample: {@code java-full-demo} is step 3, team {@code default-scrum-team}, project
 * {@code users-service-java}.
 */
public record LabScenario(
        String id,
        int step,
        String track,
        String title,
        String purpose,
        String teamId,
        String projectId,
        String featureRequest,
        boolean worksOffline,
        boolean needsLiveLlmToChangeCode,
        String durationHint,
        String expect,
        List<String> watchFor,
        String accent) {}
