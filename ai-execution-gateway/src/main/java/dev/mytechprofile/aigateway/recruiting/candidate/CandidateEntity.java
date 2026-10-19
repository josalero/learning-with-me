package dev.mytechprofile.aigateway.recruiting.candidate;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Row of {@code candidate}. Name and email live here for the PII canary test and are never returned by a tool.
 */
@Entity
@Table(name = "candidate")
public class CandidateEntity {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "requisition_id", nullable = false)
    private String requisitionId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CandidateStatus status;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> skills = new ArrayList<>();

    @Column(name = "years_experience", nullable = false)
    private int yearsExperience;

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getRequisitionId() {
        return requisitionId;
    }

    public CandidateStatus getStatus() {
        return status;
    }

    public void setStatus(CandidateStatus status) {
        this.status = status;
    }

    public List<String> getSkills() {
        return skills;
    }

    public int getYearsExperience() {
        return yearsExperience;
    }
}
