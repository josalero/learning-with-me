package dev.mytechprofile.statefulagent.business;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobOfferRepository extends JpaRepository<JobOfferEntity, UUID> {

    Optional<JobOfferEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<JobOfferEntity> findByTenantIdAndApplicationId(String tenantId, String applicationId);

    long countByIdempotencyKey(String idempotencyKey);
}
