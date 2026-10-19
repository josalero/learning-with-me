package dev.mytechprofile.langgraph4j.domain;

/**
 * Turns a Feature Brief into a bounded Cursor prompt. Deterministic so the graph
 * does not need an LLM to demonstrate interrupt and resume.
 */
public final class FeatureBriefPlanner {

    public static final String DEFAULT_BRIEF =
            "Add a ping status of ok to the Java ping-service seed. "
                    + "PingService.status() must return the exact string ok. "
                    + "Do not add extra features.";

    private FeatureBriefPlanner() {
    }

    /**
     * Builds the Cursor prompt that the human must approve before implement runs.
     *
     * <pre>{@code
     * String prompt = FeatureBriefPlanner.plan("Return ok from PingService");
     * }</pre>
     *
     * @param brief user-written Feature Brief; blank falls back to {@link #DEFAULT_BRIEF}
     * @return prompt that names the seed, allowed files, and verify command
     */
    public static String plan(String brief) {
        String body = brief == null || brief.isBlank() ? DEFAULT_BRIEF : brief.trim();
        return """
                Implement this Feature Brief in the ping-service-java seed.

                Brief:
                %s

                Constraints:
                - Only change files under src/
                - PingService.status() must return the exact string ok
                - Keep the existing unit test; make ./gradlew test pass
                - Do not invent extra endpoints, dependencies, or features
                """.formatted(body);
    }
}
