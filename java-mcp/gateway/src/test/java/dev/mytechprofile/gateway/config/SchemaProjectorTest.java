package dev.mytechprofile.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaProjectorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private SchemaProjector projector;

    @BeforeEach
    void setUp() {
        projector = new SchemaProjector(mapper);
    }

    @Test
    void project_includesOnlySelectedFields_andStripsContextFields() {
        ObjectNode source = mapper.createObjectNode();
        source.put("type", "object");
        ObjectNode properties = source.putObject("properties");
        properties.putObject("skill").put("type", "string");
        properties.putObject("limit").put("type", "integer").put("maximum", 100);
        properties.putObject("tenantId").put("type", "string");
        source.putArray("required").add("skill").add("tenantId");

        JsonNode projected = projector.project(
                source,
                List.of("skill", "limit"),
                Set.of("tenantId"),
                Map.of("limit", Map.of("maximum", 25)));

        assertThat(projected.path("properties").propertyNames())
                .containsExactlyInAnyOrder("skill", "limit");
        assertThat(projected.path("properties").has("tenantId")).isFalse();
        assertThat(projected.path("additionalProperties").asBoolean()).isFalse();
        assertThat(projected.path("properties").path("limit").path("maximum").asInt()).isEqualTo(25);
        assertThat(projected.path("required").toString()).contains("skill");
        assertThat(projected.path("required").toString()).doesNotContain("tenantId");
    }

    @Test
    void project_rejectsWideningOverride() {
        ObjectNode source = mapper.createObjectNode();
        source.put("type", "object");
        ObjectNode properties = source.putObject("properties");
        properties.putObject("limit").put("type", "integer").put("maximum", 25);

        assertThatThrownBy(() -> projector.project(
                        source, List.of("limit"), Set.of(), Map.of("limit", Map.of("maximum", 100))))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("widens");
    }

    @Test
    void project_failsWhenRequiredFieldExcludedWithoutContextMapping() {
        ObjectNode source = mapper.createObjectNode();
        source.put("type", "object");
        ObjectNode properties = source.putObject("properties");
        properties.putObject("skill").put("type", "string");
        properties.putObject("tenantId").put("type", "string");
        source.putArray("required").add("skill").add("tenantId");

        assertThatThrownBy(() -> projector.project(source, List.of("skill"), Set.of(), Map.of()))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("tenantId");
    }
}
