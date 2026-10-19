package dev.mytechprofile.aigateway.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationDraftRepository extends JpaRepository<RecommendationDraftEntity, UUID> {
    Optional<RecommendationDraftEntity> findByExecutionId(UUID executionId);
}
