package dev.mytechprofile.gateway.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import dev.mytechprofile.gateway.model.OutputPolicy;

class OutputProjectorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OutputProjector projector = new OutputProjector(mapper);

    @Test
    void apply_stripsFieldsNotOnAllowlist() {
        ArrayNode candidates = mapper.createArrayNode();
        ObjectNode candidate = candidates.addObject();
        candidate.put("candidateReference", "CAND-1001");
        candidate.put("displayName", "Alex Rivera");
        candidate.put("personalEmail", "alex.rivera@example.com");
        candidate.put("phoneNumber", "+1-555-0101");
        candidate.put("governmentIdentifier", "GOV-998877");
        candidate.put("experienceYears", 8);

        OutputPolicy policy = new OutputPolicy(
                List.of("candidateReference", "displayName", "experienceYears"), List.of(), 25);

        OutputProjector.ProjectionResult result = projector.apply(policy, candidates);

        assertThat(result.payload()).hasSize(1);
        JsonNode projected = result.payload().get(0);
        assertThat(projected.propertyNames())
                .containsExactlyInAnyOrder("candidateReference", "displayName", "experienceYears");
        assertThat(projected.has("personalEmail")).isFalse();
        assertThat(projected.has("phoneNumber")).isFalse();
        assertThat(projected.has("governmentIdentifier")).isFalse();
        assertThat(result.removedFieldCount()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void apply_masksAllowlistedSensitiveFields() {
        ObjectNode source = mapper.createObjectNode();
        source.put("displayName", "Alex");
        source.put("personalEmail", "alex.rivera@example.com");

        OutputPolicy policy = new OutputPolicy(List.of("displayName", "personalEmail"), List.of("personalEmail"), null);

        OutputProjector.ProjectionResult result = projector.apply(policy, source);

        assertThat(result.payload().get("personalEmail").asString()).isEqualTo("a***@example.com");
        assertThat(result.maskedFieldCount()).isEqualTo(1);
    }

    @Test
    void apply_capsArrayLength() {
        ArrayNode source = mapper.createArrayNode();
        for (int i = 0; i < 5; i++) {
            source.addObject().put("id", i);
        }

        OutputPolicy policy = new OutputPolicy(List.of("id"), List.of(), 2);
        OutputProjector.ProjectionResult result = projector.apply(policy, source);

        assertThat(result.payload()).hasSize(2);
    }
}
