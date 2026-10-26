package dev.mytechprofile.statefulagent.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HiringActionRepository extends JpaRepository<HiringActionEntity, UUID> {

    Optional<HiringActionEntity> findByIdempotencyKey(String idempotencyKey);

    List<HiringActionEntity> findByTenantIdAndApplicationIdOrderByCreatedAtDesc(String tenantId, String applicationId);

    long countByIdempotencyKey(String idempotencyKey);

    @Query("select count(entry) > 0 from HiringActionEntity entry where entry.idempotencyKey like concat(:prefix, '%')")
    boolean existsWithKeyPrefix(String prefix);
}
