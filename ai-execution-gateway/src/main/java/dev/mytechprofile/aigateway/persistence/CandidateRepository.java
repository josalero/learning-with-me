package dev.mytechprofile.aigateway.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<CandidateEntity, String> {
    Optional<CandidateEntity> findByIdAndTenantId(String id, String tenantId);
}
