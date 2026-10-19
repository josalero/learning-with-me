package dev.mytechprofile.aigateway.recruiting.recommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Row of {@code recommendation_draft}: the agent's suggestion, rationale, and the evidence ids it cites.
 */
@Entity
@Table(name = "recommendation_draft")
public class RecommendationDraftEntity {

    @Id
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Recommendation recommendation;

    @Column(nullable = false)
    private String rationale;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cited_evidence_ids", nullable = false, columnDefinition = "text[]")
    private List<String> citedEvidenceIds = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(Recommendation recommendation) {
        this.recommendation = recommendation;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public List<String> getCitedEvidenceIds() {
        return citedEvidenceIds;
    }

    public void setCitedEvidenceIds(List<String> citedEvidenceIds) {
        this.citedEvidenceIds = citedEvidenceIds;
    }
}
