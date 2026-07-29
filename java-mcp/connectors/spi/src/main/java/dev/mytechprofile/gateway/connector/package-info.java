/**
 * Connector SPI — the extension point for protocol adapters.
 *
 * <p>Implement {@link Connector} to add a new backend (REST, SQL, …). Governance
 * (validation, authorization, quotas, approval, masking, audit) lives above this
 * package and must not branch on connector type.
 *
 * <p><strong>Key scenarios:</strong>
 * <ul>
 *   <li>Discover operations from a machine-readable contract (OpenAPI).</li>
 *   <li>Declare named operations when no contract exists (SQL).</li>
 *   <li>Execute a normalized {@link OperationInvocation} and return
 *       {@link ExecutionResult} without MCP transport types.</li>
 * </ul>
 *
 * <p>Connectors must not depend on {@code org.springframework.ai.mcp} or
 * {@code io.modelcontextprotocol}.
 *
 * @see Connector
 * @see ConnectorCapabilities
 */
package dev.mytechprofile.gateway.connector;
