package dev.mytechprofile.gateway.connector.sql;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.connector.Connector;
import dev.mytechprofile.gateway.connector.ConnectorCapabilities;
import dev.mytechprofile.gateway.connector.ExecutionResult;
import dev.mytechprofile.gateway.connector.OperationDescriptor;
import dev.mytechprofile.gateway.connector.OperationAccess;
import dev.mytechprofile.gateway.connector.OperationInvocation;
import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.connector.sql.SqlOperationRegistry.RegisteredSqlOperation;

/**
 * Named, parameterized, read-only SQL connector.
 *
 * <p>Operations are declared in YAML and registered at compile time — never discovered
 * from information_schema.
 */
public class SqlConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(SqlConnector.class);

    private final SqlOperationRegistry registry;
    private final SqlOperationValidator validator;
    private final Map<String, DataSource> dataSources;
    private final ObjectMapper objectMapper;

    public SqlConnector(
            SqlOperationRegistry registry,
            SqlOperationValidator validator,
            Map<String, DataSource> dataSources,
            ObjectMapper objectMapper) {
        this.registry = registry;
        this.validator = validator;
        this.dataSources = Map.copyOf(dataSources);
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "sql";
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return ConnectorCapabilities.declaredOnly();
    }

    @Override
    public ConnectionDefinition configure(
            ConnectionDefinition connection, JsonNode configuration) {
        JsonNode declared = configuration.path("operations");
        if (!declared.isObject() || declared.isEmpty()) {
            throw new IllegalArgumentException(
                    "SQL connection '%s' must declare at least one operation"
                            .formatted(connection.name()));
        }
        Map<String, RegisteredSqlOperation> operations = new LinkedHashMap<>();
        declared.propertyNames().forEach(name -> {
            JsonNode operation = declared.get(name);
            String sql = operation.path("sql").asString(null);
            Integer maxRows = operation.has("maxRows") && !operation.get("maxRows").isNull()
                    ? operation.get("maxRows").asInt()
                    : null;
            validator.validate(name, sql, maxRows);

            JsonNode parameterDefinitions = operation.path("parameters");
            ObjectNode parameterSchema = objectMapper.createObjectNode();
            parameterSchema.put("type", "object");
            parameterSchema.put("additionalProperties", false);
            ObjectNode properties = parameterSchema.putObject("properties");
            var required = parameterSchema.putArray("required");
            List<String> parameterNames = new ArrayList<>();
            if (parameterDefinitions.isObject()) {
                parameterDefinitions.propertyNames().forEach(parameterName -> {
                    parameterNames.add(parameterName);
                    JsonNode definition = parameterDefinitions.get(parameterName);
                    ObjectNode field = definition instanceof ObjectNode objectNode
                            ? objectNode.deepCopy()
                            : objectMapper.createObjectNode().put("type", "string");
                    boolean isRequired = !field.has("required") || field.path("required").asBoolean();
                    field.remove("required");
                    properties.set(parameterName, field);
                    if (isRequired) {
                        required.add(parameterName);
                    }
                });
            }
            if (required.isEmpty()) {
                parameterSchema.remove("required");
            }
            Duration timeout = duration(operation.path("timeout"), connection.readTimeout());
            operations.put(
                    name,
                    new RegisteredSqlOperation(
                            connection.name(),
                            name,
                            operation.path("description").asString(name),
                            sql.strip(),
                            parameterSchema,
                            List.copyOf(parameterNames),
                            maxRows,
                            timeout));
        });
        registry.replaceAll(connection.name(), operations);
        return connection;
    }

    @Override
    public List<OperationDescriptor> discover(ConnectionDefinition connection) {
        List<OperationDescriptor> descriptors = new ArrayList<>();
        for (RegisteredSqlOperation operation : registry.forConnection(connection.name())) {
            descriptors.add(new OperationDescriptor(
                    operation.name(),
                    operation.description(),
                    operation.parameterSchema(),
                    null,
                    OperationAccess.READ));
        }
        return List.copyOf(descriptors);
    }

    @Override
    public ExecutionResult execute(OperationInvocation invocation, ToolExecutionContext context) {
        RegisteredSqlOperation operation =
                registry.require(invocation.connection().name(), invocation.operationId());
        DataSource dataSource = resolveDataSource(invocation.connection());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.setMaxRows(operation.maxRows());
        long timeoutMillis = Math.max(1, operation.timeout().toMillis());
        jdbc.setQueryTimeout((int) Math.max(1, TimeUnit.MILLISECONDS.toSeconds(timeoutMillis + 999)));
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        for (String parameterName : operation.parameterNames()) {
            parameters.addValue(parameterName, invocation.arguments().get(parameterName));
        }

        try {
            List<Map<String, Object>> rows =
                    new ArrayList<>(namedJdbc.queryForList(operation.sql(), parameters));
            // Normalize to LinkedHashMap rows for stable JSON
            List<Map<String, Object>> normalized = rows.stream()
                    .map(LinkedHashMap::new)
                    .map(map -> (Map<String, Object>) map)
                    .toList();
            JsonNode payload = objectMapper.valueToTree(normalized);
            return new ExecutionResult.Success(payload, 200);
        } catch (QueryTimeoutException ex) {
            log.warn("SQL operation '{}' timed out", operation.name(), ex);
            return new ExecutionResult.Failure(
                    ExecutionResult.FailureKind.TIMEOUT,
                    operation.name(),
                    "query exceeded " + operation.timeout());
        } catch (DataAccessException ex) {
            log.warn("SQL operation '{}' failed", operation.name(), ex);
            return new ExecutionResult.Failure(
                    ExecutionResult.FailureKind.PERMANENT, operation.name(), "query failed");
        }
    }

    private DataSource resolveDataSource(ConnectionDefinition connection) {
        String requested = connection.attributes().get("datasource");
        if (requested != null && dataSources.containsKey(requested)) {
            return dataSources.get(requested);
        }
        if (dataSources.containsKey("dataSource")) {
            return dataSources.get("dataSource");
        }
        if (dataSources.size() == 1) {
            return dataSources.values().iterator().next();
        }
        throw new IllegalArgumentException(
                "no DataSource bean for SQL connection '%s' (requested '%s')"
                        .formatted(connection.name(), requested));
    }

    private Duration duration(JsonNode node, Duration fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (node.isNumber()) {
            double seconds = node.asDouble();
            return Duration.ofMillis(Math.max(1, Math.round(seconds * 1000)));
        }
        String value = node.asString();
        try {
            return Duration.parse(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
