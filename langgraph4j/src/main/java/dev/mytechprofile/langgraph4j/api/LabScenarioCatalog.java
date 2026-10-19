package dev.mytechprofile.langgraph4j.api;

import java.util.List;

import dev.mytechprofile.langgraph4j.domain.Decision;
import dev.mytechprofile.langgraph4j.domain.FeatureBriefPlanner;
import dev.mytechprofile.langgraph4j.domain.TaskStatus;

/**
 * Built-in lab recipes so a reviewer can walk plan, interrupt, approve, and reject.
 *
 * <p>When to use: {@link LabScenarioController} and the static dashboard cards.
 *
 * <p>Example: step 1 {@code interrupt-only} is the fastest way to see the HITL pause.
 */
public final class LabScenarioCatalog {

    private LabScenarioCatalog() {}

    /**
     * Returns the ordered lab cards, step 1 first.
     *
     * @return immutable recipes
     */
    public static List<LabScenario> all() {
        return List.of(
                scenario(
                        "interrupt-only",
                        1,
                        "Pause before Cursor",
                        "Run plan and stop at the interrupt. The implementer is not called until you decide.",
                        FeatureBriefPlanner.DEFAULT_BRIEF,
                        null,
                        null,
                        TaskStatus.WAITING_APPROVAL.name(),
                        List.of(
                                "Status is WAITING_APPROVAL",
                                "The planned prompt names PingService.status() and ./gradlew test",
                                "Approve and Reject buttons appear",
                                "Implementer mode is still empty or STUB until you approve"),
                        true,
                        "instant",
                        "amber"),
                scenario(
                        "approve-ping",
                        2,
                        "Approve happy path",
                        "Approve the planned prompt. Stub patches PingService; CLI asks the local agent.",
                        FeatureBriefPlanner.DEFAULT_BRIEF,
                        Decision.APPROVE,
                        null,
                        TaskStatus.DONE.name(),
                        List.of(
                                "Status becomes DONE",
                                "Steps include approve, implement, and verify: passed",
                                "verifyPassed is true",
                                "CLI mode takes longer and needs CURSOR_API_KEY"),
                        true,
                        "a few seconds in stub; minutes in CLI",
                        "teal"),
                scenario(
                        "reject-ping",
                        3,
                        "Reject after plan",
                        "Reject the planned prompt so Cursor never runs.",
                        FeatureBriefPlanner.DEFAULT_BRIEF,
                        Decision.REJECT,
                        null,
                        TaskStatus.REJECTED.name(),
                        List.of(
                                "Status is REJECTED",
                                "Steps include approve: human rejected",
                                "No implement: step",
                                "verifyPassed stays false"),
                        true,
                        "instant",
                        "rose"),
                scenario(
                        "override-prompt",
                        4,
                        "Approve with prompt override",
                        "Replace the planned prompt at the interrupt, then implement.",
                        FeatureBriefPlanner.DEFAULT_BRIEF,
                        Decision.APPROVE,
                        """
                                Implement this Feature Brief in the ping-service-java seed.

                                Brief:
                                Override: PingService.status() must still return the exact string ok.

                                Constraints:
                                - Only change files under src/
                                - Keep the existing unit test; make ./gradlew test pass
                                - Do not invent extra endpoints, dependencies, or features
                                """
                                .strip(),
                        TaskStatus.DONE.name(),
                        List.of(
                                "The stored prompt starts with Override:",
                                "Status becomes DONE in stub",
                                "Steps still include implement and verify: passed"),
                        true,
                        "a few seconds in stub",
                        "indigo"),
                scenario(
                        "default-brief",
                        5,
                        "Empty brief fallback",
                        "Start with a blank brief. The planner fills the default Feature Brief.",
                        "",
                        null,
                        null,
                        TaskStatus.WAITING_APPROVAL.name(),
                        List.of(
                                "Status is WAITING_APPROVAL",
                                "Prompt still requires PingService.status() to return ok",
                                "You decide Approve or Reject yourself"),
                        true,
                        "instant",
                        "sky"));
    }

    /**
     * Looks up a recipe by id.
     *
     * <pre>{@code
     * LabScenario scenario = LabScenarioCatalog.require("reject-ping");
     * }</pre>
     *
     * @param id catalog id such as {@code interrupt-only}
     * @return matching recipe
     * @throws IllegalArgumentException when the id is unknown
     */
    public static LabScenario require(String id) {
        return all().stream()
                .filter(scenario -> scenario.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown lab scenario"));
    }

    private static LabScenario scenario(
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
            String accent) {
        return new LabScenario(
                id,
                step,
                title,
                purpose,
                brief,
                autoDecision,
                promptOverride,
                expectedStatus,
                watchFor,
                worksInStub,
                durationHint,
                accent);
    }
}
