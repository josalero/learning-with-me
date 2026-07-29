package dev.mytechprofile.gateway.connector.sql;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import tools.jackson.databind.JsonNode;

/**
 * In-memory registry of validated named SQL operations, keyed by connection.
 */
public class SqlOperationRegistry {

    private final ConcurrentHashMap<String, Map<String, RegisteredSqlOperation>> byConnection =
            new ConcurrentHashMap<>();

    /**
     * Replaces all operations for a connection (called during catalog compile).
     *
     * @param connectionName connection key from YAML
     * @param operations validated operations for that connection
     */
    public void replaceAll(String connectionName, Map<String, RegisteredSqlOperation> operations) {
        byConnection.put(connectionName, Map.copyOf(operations));
    }

    public List<RegisteredSqlOperation> forConnection(String connectionName) {
        return List.copyOf(byConnection.getOrDefault(connectionName, Map.of()).values());
    }

    public RegisteredSqlOperation require(String connectionName, String operationId) {
        RegisteredSqlOperation operation =
                byConnection.getOrDefault(connectionName, Map.of()).get(operationId);
        if (operation == null) {
            throw new IllegalArgumentException(
                    "unknown SQL operation '%s' on connection '%s'".formatted(operationId, connectionName));
        }
        return operation;
    }

    /**
     * Validated named SQL operation ready for discovery and execution.
     *
     * @param connectionName owning connection
     * @param name operation id
     * @param description human description
     * @param sql parameterized SELECT
     * @param parameterSchema JSON Schema for MCP / validation
     * @param parameterNames ordered parameter names
     * @param maxRows hard row cap
     * @param timeout query timeout
     */
    public record RegisteredSqlOperation(
            String connectionName,
            String name,
            String description,
            String sql,
            JsonNode parameterSchema,
            List<String> parameterNames,
            int maxRows,
            Duration timeout) {}
}
