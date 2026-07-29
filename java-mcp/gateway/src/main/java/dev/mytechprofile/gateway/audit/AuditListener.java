package dev.mytechprofile.gateway.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists {@link ToolInvocationCompleted} rows and increments low-cardinality metrics.
 */
@Component
@ConditionalOnProperty(
        prefix = "gateway.observability",
        name = "audit-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AuditListener {

    private static final Logger log = LoggerFactory.getLogger(AuditListener.class);

    private final JdbcClient jdbc;
    private final MeterRegistry meterRegistry;

    public AuditListener(JdbcClient jdbc, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Writes one audit row per invocation in a separate transaction.
     *
     * @param event completed invocation metadata
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ToolInvocationCompleted event) {
        try {
            jdbc.sql(
                            """
                            insert into tool_invocation_audit (
                                invocation_id, correlation_id, session_id, tool_name,
                                connection_name, connector_type, subject, tenant_id,
                                authentication_type, authorization_decision, approval_outcome,
                                argument_hash, started_at, completed_at, result_category,
                                removed_field_count, error_reference)
                            values (
                                :invocationId, :correlationId, :sessionId, :toolName,
                                :connectionName, :connectorType, :subject, :tenantId,
                                :authenticationType, :authorizationDecision, :approvalOutcome,
                                :argumentHash, :startedAt, :completedAt, :resultCategory,
                                :removedFieldCount, :errorReference)
                            """)
                    .param("invocationId", event.invocationId())
                    .param("correlationId", event.correlationId())
                    .param("sessionId", event.sessionId())
                    .param("toolName", event.toolName())
                    .param("connectionName", event.connectionName())
                    .param("connectorType", event.connectorType())
                    .param("subject", event.subject())
                    .param("tenantId", event.tenantId())
                    .param("authenticationType", event.authenticationType().name())
                    .param("authorizationDecision", event.authorizationDecision())
                    .param("approvalOutcome", event.approvalOutcome())
                    .param("argumentHash", event.argumentHash())
                    .param("startedAt", toOffset(event.startedAt()))
                    .param("completedAt", toOffset(event.completedAt()))
                    .param("resultCategory", event.resultCategory())
                    .param("removedFieldCount", event.removedFieldCount())
                    .param("errorReference", event.errorReference())
                    .update();

            meterRegistry
                    .counter(
                            "gateway.tool.invocations",
                            "tool",
                            event.toolName(),
                            "result",
                            event.resultCategory())
                    .increment();
        } catch (RuntimeException ex) {
            // Audit must never fail the MCP tool response.
            log.error(
                    "failed to persist audit for tool='{}' invocationId='{}'",
                    event.toolName(),
                    event.invocationId(),
                    ex);
        }
    }

    private static OffsetDateTime toOffset(java.time.Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
