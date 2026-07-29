package dev.mytechprofile.gateway.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.List;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.model.AuthorizationPolicy;
import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.model.GatewayException;
import dev.mytechprofile.gateway.model.OutputPolicy;
import dev.mytechprofile.gateway.model.QuotaPolicy;
import dev.mytechprofile.gateway.model.ToolMode;

class ArgumentValidatorTest {

    private final ArgumentValidator validator = new ArgumentValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validate_rejectsUnknownTenantIdField() {
        CompiledTool tool = toolWithSchema();
        assertThatThrownBy(() -> validator.validate(tool, Map.of("skill", "Java", "tenantId", "evil-tenant")))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void validate_mergesDefaultsAndAcceptsValidArgs() {
        CompiledTool tool = toolWithSchema();
        Map<String, Object> validated = validator.validate(tool, Map.of("skill", "Java"));
        assertThat(validated).containsEntry("skill", "Java").containsEntry("limit", 10);
    }

    @Test
    void validate_enforcesTypesEnumsStringsAndArrayItems() {
        CompiledTool tool = toolWithSchema();
        assertThatThrownBy(() -> validator.validate(
                        tool,
                        Map.of("skill", "Java", "stage", "UNKNOWN")))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("stage")
                .hasMessageContaining("must be one of");
        assertThatThrownBy(() -> validator.validate(
                        tool,
                        Map.of("skill", "J", "tags", List.of("ok", 12))))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("skill");
        assertThatThrownBy(() -> validator.validate(
                        tool,
                        Map.of("skill", "Java", "limit", "twenty")))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("must be an integer");
    }

    private CompiledTool toolWithSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("skill").put("type", "string").put("minLength", 2);
        properties.putObject("limit").put("type", "integer").put("maximum", 25);
        properties.putObject("stage")
                .put("type", "string")
                .putArray("enum")
                .add("PHONE_SCREEN")
                .add("OFFER");
        properties.putObject("tags")
                .put("type", "array")
                .putObject("items")
                .put("type", "string");
        schema.putArray("required").add("skill");

        return new CompiledTool(
                "search_candidates",
                "Search",
                new ConnectionDefinition(
                        "recruiting-api",
                        "openapi",
                        URI.create("http://127.0.0.1:9080"),
                        "classpath:/openapi/recruiting.yaml",
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5)),
                "searchCandidates",
                ToolMode.READ,
                schema,
                Map.of("limit", 10),
                Map.of(),
                OutputPolicy.none(),
                AuthorizationPolicy.none(),
                QuotaPolicy.none(),
                false);
    }
}
