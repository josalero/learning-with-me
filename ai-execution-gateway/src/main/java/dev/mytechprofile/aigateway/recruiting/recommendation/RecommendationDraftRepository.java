package dev.mytechprofile.aigateway.recruiting.recommendation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The draft of one execution, read by the evaluator to check its citations.
 */
public interface RecommendationDraftRepository extends JpaRepository<RecommendationDraftEntity, UUID> {
    /** The draft written during this execution, if the agent wrote one. */
    Optional<RecommendationDraftEntity> findByExecutionId(UUID executionId);
}
