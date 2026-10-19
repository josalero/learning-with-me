package dev.mytechprofile.aigateway.shared.security;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Principals by id, read once per request by the key filter and again when the worker starts a run.
 */
public interface PrincipalRepository extends JpaRepository<PrincipalEntity, String> {}
