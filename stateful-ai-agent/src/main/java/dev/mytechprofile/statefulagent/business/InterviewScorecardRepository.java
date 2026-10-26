package dev.mytechprofile.statefulagent.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewScorecardRepository extends JpaRepository<InterviewScorecardEntity, UUID> {

    Optional<InterviewScorecardEntity> findByIdempotencyKey(String idempotencyKey);

    List<InterviewScorecardEntity> findByTenantIdAndApplicationIdOrderByCreatedAtDesc(String tenantId, String applicationId);

    Optional<InterviewScorecardEntity> findFirstByTenantIdAndApplicationIdAndRoundOrderByCreatedAtDesc(
            String tenantId, String applicationId, InterviewRound round);

    long countByIdempotencyKey(String idempotencyKey);
}
