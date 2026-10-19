package dev.mytechprofile.aigateway.gateway.audit;

import java.time.Instant;
import java.util.UUID;

import dev.mytechprofile.aigateway.gateway.authority.Decision;
import dev.mytechprofile.aigateway.gateway.authority.ReasonCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Row of {@code execution_step}: one model turn or one tool decision, in {@code seq} order.
 *
 * <p>Tool arguments and results are stored only as HMACs; {@code spanId} links the row to its trace span.
 */
@Entity
@Table(name = "execution_step")
public class ExecutionStepEntity {

    @Id
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepType type;

    @Column(name = "tool_name")
    private String toolName;

    @Enumerated(EnumType.STRING)
    private Decision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code")
    private ReasonCode reasonCode;

    @Column(name = "args_hmac")
    private String argsHmac;

    @Column(name = "result_hmac")
    private String resultHmac;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "cost_micros")
    private Long costMicros;

    @Column(name = "span_id")
    private String spanId;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public StepType getType() {
        return type;
    }

    public void setType(StepType type) {
        this.type = type;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(ReasonCode reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getArgsHmac() {
        return argsHmac;
    }

    public void setArgsHmac(String argsHmac) {
        this.argsHmac = argsHmac;
    }

    public String getResultHmac() {
        return resultHmac;
    }

    public void setResultHmac(String resultHmac) {
        this.resultHmac = resultHmac;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Long getCostMicros() {
        return costMicros;
    }

    public void setCostMicros(Long costMicros) {
        this.costMicros = costMicros;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
