package dev.mytechprofile.aigateway.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<EvidenceEntity, String> {
    List<EvidenceEntity> findByCandidateIdAndTenantId(String candidateId, String tenantId);
}
