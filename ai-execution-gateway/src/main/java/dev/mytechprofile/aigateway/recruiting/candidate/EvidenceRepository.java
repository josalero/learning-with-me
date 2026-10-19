package dev.mytechprofile.aigateway.recruiting.candidate;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Evidence of one candidate within one tenant.
 */
public interface EvidenceRepository extends JpaRepository<EvidenceEntity, String> {
    /** All evidence of the candidate, scoped to the tenant. */
    List<EvidenceEntity> findByCandidateIdAndTenantId(String candidateId, String tenantId);
}
