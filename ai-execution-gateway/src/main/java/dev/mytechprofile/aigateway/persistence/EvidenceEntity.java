package dev.mytechprofile.aigateway.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "evidence")
public class EvidenceEntity {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    @Column(nullable = false)
    private String snippet;

    public String getId() {
        return id;
    }

    public String getSnippet() {
        return snippet;
    }
}
