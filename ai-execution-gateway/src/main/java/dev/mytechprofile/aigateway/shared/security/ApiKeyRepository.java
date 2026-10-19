package dev.mytechprofile.aigateway.shared.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks up keys by hash. Revoked keys ({@code revoked_at} set) are never returned.
 */
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {
    /** The active key with this SHA-256, if any. */
    Optional<ApiKeyEntity> findByKeyHashAndRevokedAtIsNull(String keyHash);

    /**
     * Removes a principal's keys; the demo bootstrap uses it to replace keys on startup.
     * Runs in its own transaction because the bootstrap calls it outside one.
     */
    @Transactional
    @Modifying
    @Query("delete from ApiKeyEntity k where k.principalId = :principalId")
    void deleteByPrincipalId(@Param("principalId") String principalId);
}
