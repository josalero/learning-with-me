package dev.mytechprofile.aigateway.recruiting.candidate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Row of {@code evidence}: a snippet the agent can retrieve and cite. Scenario 2's snippet carries the injection text.
 */
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
