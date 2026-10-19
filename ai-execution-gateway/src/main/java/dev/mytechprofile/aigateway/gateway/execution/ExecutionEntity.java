package dev.mytechprofile.aigateway.gateway.execution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.mytechprofile.aigateway.gateway.evaluation.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Row of {@code execution}: who asked, for which agent and candidate, the status, and the verdict once terminal.
 *
 * <p>Lifecycle: {@code QUEUED -> RUNNING -> COMPLETED | BUDGET_EXHAUSTED | FAILED}, or {@code QUEUED -> FAILED} when
 * the pool rejects the work. {@code failureReason} is {@code MAX_TURNS}, {@code UNEXPECTED}, or {@code DISPATCH_REJECTED}.
 */
@Entity
@Table(name = "execution")
public class ExecutionEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "principal_id", nullable = false)
    private String principalId;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Enumerated(EnumType.STRING)
    private Verdict verdict;

    private Boolean successful;

    @Column(name = "failure_reason")
    private String failureReason;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "retrieved_evidence_ids", nullable = false, columnDefinition = "text[]")
    private List<String> retrievedEvidenceIds = new ArrayList<>();

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "completed_at", columnDefinition = "timestamptz")
    private Instant completedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public void setVerdict(Verdict verdict) {
        this.verdict = verdict;
    }

    public Boolean getSuccessful() {
        return successful;
    }

    public void setSuccessful(Boolean successful) {
        this.successful = successful;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public List<String> getRetrievedEvidenceIds() {
        return retrievedEvidenceIds;
    }

    public void setRetrievedEvidenceIds(List<String> retrievedEvidenceIds) {
        this.retrievedEvidenceIds = retrievedEvidenceIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
