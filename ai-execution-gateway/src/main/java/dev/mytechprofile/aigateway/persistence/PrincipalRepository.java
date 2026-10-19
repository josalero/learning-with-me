package dev.mytechprofile.aigateway.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrincipalRepository extends JpaRepository<PrincipalEntity, String> {}
