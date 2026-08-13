package dev.mytechprofile.sdlc.api;

import java.util.List;

/**
 * Built-in Samples-tab recipes so a reviewer can walk the POC in order.
 *
 * <p><strong>When to use:</strong> {@link LabController#scenarios()} and dashboard cards.
 *
 * <p><strong>Example:</strong> step 1 {@code java-dev-only} is the fastest offline pipeline walk.
 */
public final class LabScenarioCatalog {

    /** Canonical demo feature that matches the Java and Node seed gaps. */
    public static final String DEMO_FEATURE =
            "Return 404 with an RFC 9457 problem detail when a user id does not exist, and reject blank names on create.";

    private static final String VAGUE_FEATURE = "Improve the users API.";

    private LabScenarioCatalog() {}

    /**
     * Returns the ordered lab cards, step 1 first.
     *
     * @return immutable recipes
     */
    public static List<LabScenario> all() {
        return List.of(
                sample(
                        "java-dev-only",
                        1,
                        "Smoke",
                        "Solo developer, Java seed",
                        "Prove the desk boots: seed copy, one role, canned model, allowlisted test.",
                        "dev-only",
                        "users-service-java",
                        DEMO_FEATURE,
                        false,
                        "about 30 seconds offline",
                        "Pipeline completes without a live LLM. The seed is not implemented.",
                        List.of(
                                "Results status is COMPLETED or ESCALATED",
                                "Timeline has developer then build-gate",
                                "04-build-result.json exists; extra Gradle runs should be skipped"),
                        "sky"),
                sample(
                        "java-lean",
                        2,
                        "Small team",
                        "Lean pair on Java",
                        "Add Product Owner and PR Reviewer. Still one stack.",
                        "lean-pair",
                        "users-service-java",
                        DEMO_FEATURE,
                        true,
                        "a few minutes",
                        "Three roles, then a build. Live LLM may edit UserController.",
                        List.of(
                                "Timeline includes product-owner, developer, pr-reviewer, build-gate",
                                "01-feature-brief.json has Given/When/Then",
                                "Live mode: workspace UserController.java may change"),
                        "teal"),
                sample(
                        "java-full-demo",
                        3,
                        "Full team",
                        "Six-role scrum on Java",
                        "The advertised POC. Every artifact 01–08 should appear.",
                        "default-scrum-team",
                        "users-service-java",
                        DEMO_FEATURE,
                        true,
                        "longest run",
                        "PO → Tech Lead → Developer → Review → QA → Stakeholder.",
                        List.of(
                                "Artifacts 01 through 08 are listed",
                                "Live mode: unknown id becomes RFC 9457 404",
                                "Live mode: blank name on create is rejected"),
                        "indigo"),
                sample(
                        "node-lean",
                        4,
                        "Second stack",
                        "Same feature, Node seed",
                        "Technology is project YAML, not Java. npm test is the gate.",
                        "lean-pair",
                        "users-service-node",
                        DEMO_FEATURE,
                        true,
                        "a few minutes",
                        "Identical feature text, different repo and test command.",
                        List.of(
                                "Project id is users-service-node",
                                "04-build-result.json mentions npm test",
                                "Workspace path is workspace/users-service-node"),
                        "violet"),
                sample(
                        "java-hitl",
                        5,
                        "Human in the loop",
                        "You are the stakeholder",
                        "The run pauses. Approve or reject on the Results tab.",
                        "hitl-lean",
                        "users-service-java",
                        DEMO_FEATURE,
                        true,
                        "pauses until you decide",
                        "Status WAITING_APPROVAL until you submit a decision.",
                        List.of(
                                "Status badge is WAITING_APPROVAL",
                                "An approve form appears in Results",
                                "APPROVED finishes; REJECTED can loop the Product Owner"),
                        "amber"),
                sample(
                        "java-vague",
                        6,
                        "Quality",
                        "Vague request, Product Owner",
                        "The ask is only “Improve the users API.” Watch the brief, not the code.",
                        "lean-pair",
                        "users-service-java",
                        VAGUE_FEATURE,
                        true,
                        "a few minutes",
                        "Offline uses canned criteria. Live LLM should invent Given/When/Then.",
                        List.of(
                                "Feature request text is Improve the users API",
                                "Open 01-feature-brief.json first",
                                "Acceptance criteria should be numbered, not a restatement of the sentence"),
                        "rose"));
    }

    private static LabScenario sample(
            String id,
            int step,
            String track,
            String title,
            String purpose,
            String teamId,
            String projectId,
            String featureRequest,
            boolean needsLiveLlmToChangeCode,
            String durationHint,
            String expect,
            List<String> watchFor,
            String accent) {
        return new LabScenario(
                id,
                step,
                track,
                title,
                purpose,
                teamId,
                projectId,
                featureRequest,
                true,
                needsLiveLlmToChangeCode,
                durationHint,
                expect,
                watchFor,
                accent);
    }
}
