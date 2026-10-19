package dev.mytechprofile.aigateway.recruiting.transition;

import java.time.Instant;
import java.util.UUID;

import dev.mytechprofile.aigateway.recruiting.candidate.CandidateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Row of {@code transition_request}: a status change the agent asked for, and who approved it.
 */
@Entity
@Table(name = "transition_request")
public class TransitionRequestEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private CandidateStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private CandidateStatus toStatus;

    @Column(name = "draft_id")
    private UUID draftId;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransitionRequestStatus status;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at", columnDefinition = "timestamptz")
    private Instant decidedAt;

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

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public CandidateStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(CandidateStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public CandidateStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(CandidateStatus toStatus) {
        this.toStatus = toStatus;
    }

    public void setDraftId(UUID draftId) {
        this.draftId = draftId;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }

    public TransitionRequestStatus getStatus() {
        return status;
    }

    public void setStatus(TransitionRequestStatus status) {
        this.status = status;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
