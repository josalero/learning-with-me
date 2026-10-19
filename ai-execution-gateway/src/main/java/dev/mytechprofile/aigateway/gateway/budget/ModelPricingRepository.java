package dev.mytechprofile.aigateway.gateway.budget;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Prices by model id. The live profile writes its configured price at startup.
 */
public interface ModelPricingRepository extends JpaRepository<ModelPricingEntity, String> {}
