package dev.mytechprofile.gateway.connector;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Protocol adapter that discovers and executes operations behind a connection.
 *
 * <p><strong>When to use:</strong> implement this interface when adding a new
 * backend type (for example OpenAPI/REST or named SQL). Register the
 * implementation as a Spring bean; the gateway resolves connectors by
 * {@link #type()}.
 *
 * <p><strong>Example — OpenAPI connector registration:</strong>
 * <pre>{@code
 * @Component
 * public class OpenApiConnector implements Connector {
 *     @Override public String type() { return "openapi"; }
 *     @Override public ConnectorCapabilities capabilities() {
 *         return ConnectorCapabilities.discoverable();
 *     }
 *     // discover(...) + execute(...)
 * }
 * }</pre>
 *
 * <p><strong>Contract:</strong>
 * <ul>
 *   <li>{@link #discover(ConnectionDefinition)} never publishes tools — it only
 *       returns descriptors for the configuration compiler.</li>
 *   <li>{@link #execute(OperationInvocation, ToolExecutionContext)} must not
 *       throw transport-specific exceptions to callers; normalize into
 *       {@link ExecutionResult.Failure}.</li>
 *   <li>Must not depend on Spring AI MCP classes.</li>
 * </ul>
 *
 * @see ConnectorCapabilities
 * @see OperationDescriptor
 * @see ExecutionResult
 */
public interface Connector {

    /**
     * Stable connector type key used in YAML {@code connections.*.type}.
     *
     * @return non-blank type such as {@code "openapi"} or {@code "sql"}
     */
    String type();

    /**
     * Declares whether this connector can enumerate operations from a contract.
     *
     * @return capabilities; OpenAPI returns {@link ConnectorCapabilities#discoverable()},
     *         SQL returns {@link ConnectorCapabilities#declaredOnly()}
     */
    ConnectorCapabilities capabilities();

    /**
     * Lets a connector validate and compile its own catalog configuration.
     *
     * <p>The default implementation accepts the gateway's common connection
     * metadata unchanged. Connector libraries may read custom values from
     * {@code connections.*.settings} without requiring gateway-core changes.
     *
     * @param connection common non-secret connection metadata
     * @param configuration complete connection configuration as a JSON tree
     * @return compiled connection, optionally enriched with connector attributes
     */
    default ConnectionDefinition configure(
            ConnectionDefinition connection, JsonNode configuration) {
        return connection;
    }

    /**
     * Enumerates operations available on the connection without publishing them as MCP tools.
     *
     * <p><strong>Scenario:</strong> gateway startup loads {@code recruiting.yaml},
     * calls {@code discover}, then the compiler allowlists only
     * {@code searchCandidates}.
     *
     * @param connection connection metadata including base URI and specification location
     * @return descriptors for known operations; never {@code null}
     * @throws IllegalArgumentException if the connection is invalid for this connector
     */
    List<OperationDescriptor> discover(ConnectionDefinition connection);

    /**
     * Executes one operation with already-validated, context-enriched arguments.
     *
     * <p><strong>Scenario:</strong> MCP client invokes {@code search_candidates};
     * the pipeline authorizes and injects {@code tenantId}, then calls
     * {@code execute} with the enriched argument map.
     *
     * @param invocation connection, operation id, and argument map
     * @param context invocation identity and correlation identifiers
     * @return success payload or normalized failure; never {@code null}
     */
    ExecutionResult execute(OperationInvocation invocation, ToolExecutionContext context);
}
