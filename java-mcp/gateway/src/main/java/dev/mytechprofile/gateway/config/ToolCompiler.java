package dev.mytechprofile.gateway.config;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.connector.Connector;
import dev.mytechprofile.gateway.connector.OperationDescriptor;
import dev.mytechprofile.gateway.connector.OperationAccess;
import dev.mytechprofile.gateway.model.AuthorizationPolicy;
import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.model.OutputPolicy;
import dev.mytechprofile.gateway.model.QuotaPolicy;
import dev.mytechprofile.gateway.model.ToolMode;
import dev.mytechprofile.gateway.model.ToolMetadata;
import dev.mytechprofile.gateway.pipeline.ContextExpression;
import dev.mytechprofile.gateway.pipeline.ContextExpressionParser;

/**
 * Compiles YAML catalog packs into {@link CompiledTool} instances.
 */
@Component
public class ToolCompiler {

    private static final Pattern TOOL_NAME = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final List<Connector> connectors;
    private final SchemaProjector projector;
    private final ContextExpressionParser expressions;
    private final ObjectMapper objectMapper;

    public ToolCompiler(
            List<Connector> connectors,
            SchemaProjector projector,
            ContextExpressionParser expressions,
            ObjectMapper objectMapper) {
        this.connectors = connectors;
        this.projector = projector;
        this.expressions = expressions;
        this.objectMapper = objectMapper;
    }

    public List<CompiledTool> compile(GatewayProperties properties) {
        if (properties.configurationVersion() != 1) {
            throw new ConfigurationException(
                    "unsupported configuration-version: " + properties.configurationVersion());
        }
        if (properties.tools().isEmpty()) {
            throw new ConfigurationException("gateway.tools must declare at least one tool");
        }

        Map<String, ConnectionDefinition> connections = compileConnections(properties);
        Map<String, Map<String, OperationDescriptor>> catalog = discoverAll(connections);

        return properties.tools().entrySet().stream()
                .map(entry -> compileTool(entry.getKey(), entry.getValue(), connections, catalog))
                .toList();
    }

    private Map<String, ConnectionDefinition> compileConnections(GatewayProperties properties) {
        Map<String, ConnectionDefinition> compiled = new LinkedHashMap<>();
        properties.connections().forEach((name, spec) -> {
            if (spec.type() == null || spec.type().isBlank()) {
                throw new ConfigurationException("connection '%s' is missing type".formatted(name));
            }
            Duration connect = spec.timeouts() != null && spec.timeouts().connect() != null
                    ? spec.timeouts().connect()
                    : Duration.ofSeconds(2);
            Duration read = spec.timeouts() != null && spec.timeouts().read() != null
                    ? spec.timeouts().read()
                    : Duration.ofSeconds(5);
            URI baseUri = spec.baseUrl() == null || spec.baseUrl().isBlank()
                    ? URI.create("about:blank")
                    : URI.create(spec.baseUrl());
            ConnectionDefinition common = new ConnectionDefinition(
                    name,
                    spec.type(),
                    baseUri,
                    spec.specification(),
                    connect,
                    read,
                    spec.datasource() == null
                            ? Map.of()
                            : Map.of("datasource", spec.datasource()));
            Connector connector = connectorFor(spec.type());
            compiled.put(name, connector.configure(common, objectMapper.valueToTree(spec)));
        });
        return Map.copyOf(compiled);
    }

    private Map<String, Map<String, OperationDescriptor>> discoverAll(
            Map<String, ConnectionDefinition> connections) {
        Map<String, Map<String, OperationDescriptor>> catalog = new LinkedHashMap<>();
        connections.forEach((name, connection) -> {
            Connector connector = connectorFor(connection.type());
            List<OperationDescriptor> descriptors = connector.discover(connection);
            Map<String, OperationDescriptor> byId = descriptors.stream()
                    .collect(Collectors.toUnmodifiableMap(
                            OperationDescriptor::operationId,
                            descriptor -> descriptor,
                            (a, b) -> {
                                throw new ConfigurationException(
                                        "duplicate operationId '%s' on connection '%s'"
                                                .formatted(a.operationId(), name));
                            }));
            catalog.put(name, byId);
        });
        return Map.copyOf(catalog);
    }

    private Connector connectorFor(String type) {
        return connectors.stream()
                .filter(connector -> type.equals(connector.type()))
                .findFirst()
                .orElseThrow(() -> new ConfigurationException("no connector for type: " + type));
    }

    private CompiledTool compileTool(
            String name,
            GatewayProperties.ToolProperties spec,
            Map<String, ConnectionDefinition> connections,
            Map<String, Map<String, OperationDescriptor>> catalog) {

        requireValidToolName(name);

        ConnectionDefinition connection = connections.get(spec.connection());
        if (connection == null) {
            throw new ConfigurationException(
                    "tool '%s' references unknown connection '%s'".formatted(name, spec.connection()));
        }

        OperationDescriptor operation = catalog
                .getOrDefault(spec.connection(), Map.of())
                .get(spec.operation());
        if (operation == null) {
            throw new ConfigurationException(
                    "tool '%s' references unknown operation '%s' on '%s'"
                            .formatted(name, spec.operation(), spec.connection()));
        }

        if (spec.mode() == null || spec.mode().isBlank()) {
            throw new ConfigurationException("tool '%s' is missing mode".formatted(name));
        }
        ToolMode mode = ToolMode.valueOf(spec.mode().toUpperCase(Locale.ROOT));
        if (operation.access() == OperationAccess.WRITE && mode != ToolMode.WRITE) {
            throw new ConfigurationException(
                    "tool '%s' exposes a mutating operation and must use mode: write"
                            .formatted(name));
        }
        if (mode == ToolMode.WRITE && !spec.requiresApproval()) {
            throw new ConfigurationException(
                    "tool '%s' is mode: write and must set requires-approval".formatted(name));
        }

        Map<String, ContextExpression> contextMappings = expressions.parseAll(spec.contextMappings());
        GatewayProperties.InputProperties input = spec.input();
        List<String> include = input == null || input.include() == null ? List.of() : input.include();

        for (String field : include) {
            if (contextMappings.containsKey(field)) {
                throw new ConfigurationException(
                        "tool '%s': context-mapped field '%s' must not appear in input.include"
                                .formatted(name, field));
            }
        }

        JsonNode inputSchema = projector.project(
                operation.inputSchema(),
                include,
                contextMappings.keySet(),
                input == null || input.override() == null ? Map.of() : input.override());

        assertNoContextLeak(name, inputSchema, contextMappings.keySet());

        Map<String, Object> defaults =
                input == null || input.defaults() == null ? Map.of() : Map.copyOf(input.defaults());

        String description = spec.description() == null ? operation.summary() : spec.description();

        return new CompiledTool(
                name,
                description,
                connection,
                operation.operationId(),
                mode,
                inputSchema,
                defaults,
                contextMappings,
                OutputPolicy.from(spec.output()),
                AuthorizationPolicy.from(spec.authorization()),
                QuotaPolicy.from(spec.quota()),
                spec.requiresApproval(),
                new ToolMetadata(
                        spec.owner() == null || spec.owner().isBlank() ? "unassigned" : spec.owner(),
                        spec.version() == null || spec.version().isBlank() ? "1" : spec.version(),
                        spec.deprecated()));
    }

    private void requireValidToolName(String name) {
        if (!TOOL_NAME.matcher(name).matches()) {
            throw new ConfigurationException(
                    "tool name '%s' must be snake_case starting with a letter".formatted(name));
        }
    }

    private void assertNoContextLeak(String tool, JsonNode schema, java.util.Set<String> contextFields) {
        JsonNode properties = schema.path("properties");
        for (String field : contextFields) {
            if (properties.has(field)) {
                throw new ConfigurationException(
                        "tool '%s': context-mapped field '%s' is visible in the input schema"
                                .formatted(tool, field));
            }
        }
    }
}
