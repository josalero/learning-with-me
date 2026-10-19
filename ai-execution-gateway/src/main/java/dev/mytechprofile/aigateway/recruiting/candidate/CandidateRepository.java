package dev.mytechprofile.aigateway.recruiting.candidate;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Candidates, always looked up together with the tenant.
 */
public interface CandidateRepository extends JpaRepository<CandidateEntity, String> {
    /** Empty for a candidate in another tenant. */
    Optional<CandidateEntity> findByIdAndTenantId(String id, String tenantId);
}
