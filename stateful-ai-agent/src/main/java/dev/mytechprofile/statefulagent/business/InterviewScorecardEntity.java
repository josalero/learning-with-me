package dev.mytechprofile.statefulagent.business;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "interview_scorecard")
public class InterviewScorecardEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "application_id", nullable = false)
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "round", nullable = false)
    private InterviewRound round;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Recommendation recommendation;

    /** Scores are 1–4. The columns are {@code smallint}; a plain {@code int} validates as {@code integer}. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "problem_solving", nullable = false)
    private int problemSolving;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "code_quality", nullable = false)
    private int codeQuality;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int communication;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "role_depth", nullable = false)
    private int roleDepth;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return this.id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getApplicationId() {
        return this.applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public InterviewRound getRound() {
        return this.round;
    }

    public void setRound(InterviewRound round) {
        this.round = round;
    }

    public Recommendation getRecommendation() {
        return this.recommendation;
    }

    public void setRecommendation(Recommendation recommendation) {
        this.recommendation = recommendation;
    }

    public int getProblemSolving() {
        return this.problemSolving;
    }

    public void setProblemSolving(int problemSolving) {
        this.problemSolving = problemSolving;
    }

    public int getCodeQuality() {
        return this.codeQuality;
    }

    public void setCodeQuality(int codeQuality) {
        this.codeQuality = codeQuality;
    }

    public int getCommunication() {
        return this.communication;
    }

    public void setCommunication(int communication) {
        this.communication = communication;
    }

    public int getRoleDepth() {
        return this.roleDepth;
    }

    public void setRoleDepth(int roleDepth) {
        this.roleDepth = roleDepth;
    }

    public String getIdempotencyKey() {
        return this.idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
