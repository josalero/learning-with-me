package dev.mytechprofile.aigateway.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {
    Optional<ApiKeyEntity> findByKeyHashAndRevokedAtIsNull(String keyHash);

    void deleteByPrincipalId(String principalId);
}
