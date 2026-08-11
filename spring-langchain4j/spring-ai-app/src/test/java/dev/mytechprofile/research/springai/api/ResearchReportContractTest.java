package dev.mytechprofile.research.springai.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.mytechprofile.research.springai.domain.Critique;
import dev.mytechprofile.research.springai.domain.Finding;
import dev.mytechprofile.research.springai.domain.ResearchPlan;
import dev.mytechprofile.research.springai.domain.ResearchReport;
import dev.mytechprofile.research.springai.domain.StepEvent;

class ResearchReportContractTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void researchReport_serializesToSharedContractShape() throws Exception {
        ResearchReport report = new ResearchReport(
                "Java virtual threads",
                new ResearchPlan(List.of(
                        "What problem do virtual threads solve?",
                        "How do virtual threads differ from platform threads?",
                        "What are common pitfalls when adopting virtual threads?"
                )),
                List.of(new Finding(
                        "What problem do virtual threads solve?",
                        "They reduce the cost of blocking I/O concurrency.")),
                "# Draft report\n\nVirtual threads make blocking concurrency cheaper.",
                new Critique(8, "Solid overview; add one concrete pitfall."),
                "# Final report\n\nVirtual threads make blocking concurrency cheaper.\n\nPitfall: avoid long-lived ThreadLocal with pooling assumptions.",
                List.of(
                        new StepEvent(
                                "planner",
                                "completed",
                                "topic: Java virtual threads\ndepth: 3",
                                "1. What problem do virtual threads solve?\n2. How do virtual threads differ from platform threads?\n3. What are common pitfalls when adopting virtual threads?",
                                12),
                        new StepEvent(
                                "researcher",
                                "completed",
                                "1. What problem do virtual threads solve?",
                                "Q: What problem do virtual threads solve?\nA: They reduce the cost of blocking I/O concurrency.",
                                34),
                        new StepEvent(
                                "writer",
                                "completed",
                                "topic: Java virtual threads\n\nfindings:\nQ: What problem do virtual threads solve?\nA: They reduce the cost of blocking I/O concurrency.\n\ncritique notes:\nNone — write the first draft.",
                                "# Draft report\n\nVirtual threads make blocking concurrency cheaper.",
                                20),
                        new StepEvent(
                                "critic",
                                "completed",
                                "# Draft report\n\nVirtual threads make blocking concurrency cheaper.",
                                "score: 8\nnotes: Solid overview; add one concrete pitfall.",
                                15),
                        new StepEvent(
                                "writer",
                                "completed",
                                "topic: Java virtual threads\n\nfindings:\nQ: What problem do virtual threads solve?\nA: They reduce the cost of blocking I/O concurrency.\n\ncritique notes:\nSolid overview; add one concrete pitfall.",
                                "# Final report\n\nVirtual threads make blocking concurrency cheaper.\n\nPitfall: avoid long-lived ThreadLocal with pooling assumptions.",
                                18)
                ),
                "spring-ai",
                "openai/gpt-4o-mini",
                99
        );

        JsonNode actual = mapper.readTree(mapper.writeValueAsString(report));
        Path fixture = Path.of("..", "shared-fixtures", "research-report.contract.json").normalize();
        JsonNode expected = mapper.readTree(Files.readString(fixture));

        List<String> expectedFields = new ArrayList<>();
        expected.fieldNames().forEachRemaining(expectedFields::add);
        assertThat(actual.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(expectedFields);
        assertThat(actual.get("topic").asText()).isEqualTo(expected.get("topic").asText());
        assertThat(actual.get("plan").get("questions").size())
                .isEqualTo(expected.get("plan").get("questions").size());
        assertThat(actual.get("steps").size()).isEqualTo(expected.get("steps").size());
        assertThat(actual.get("steps").get(0).has("input")).isTrue();
        assertThat(actual.get("steps").get(0).has("output")).isTrue();
        assertThat(actual.get("critique").get("score").asInt())
                .isEqualTo(expected.get("critique").get("score").asInt());
        assertThat(actual.get("critique").has("passThreshold")).isFalse();
        assertThat(actual.get("engine").asText()).isEqualTo("spring-ai");
    }
}
