package dev.mytechprofile.aigateway.shared.security;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import dev.mytechprofile.aigateway.shared.config.GatewayProperties;

/**
 * Stores hashes for demo keys supplied by the environment. Raw keys are not written.
 */
@Component
@Profile({"stub", "live"})
public class DemoKeyBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoKeyBootstrap.class);

    private final GatewayProperties properties;
    private final ApiKeyRepository apiKeys;

    public DemoKeyBootstrap(GatewayProperties properties, ApiKeyRepository apiKeys) {
        this.properties = properties;
        this.apiKeys = apiKeys;
    }

    @Override
    public void run(ApplicationArguments args) {
        register("recruiter-alice", properties.demoKeys().recruiter());
        register("ats-nightly", properties.demoKeys().ats());
        register("recruiter-bob", properties.demoKeys().globex());
    }

    private void register(String principalId, String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            log.warn("Demo API key for {} is not set; skipping", principalId);
            return;
        }
        String hash = ApiKeyHasher.sha256(rawKey);
        apiKeys.deleteByPrincipalId(principalId);
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(UUID.randomUUID());
        entity.setPrincipalId(principalId);
        entity.setKeyHash(hash);
        entity.setCreatedAt(Instant.now());
        apiKeys.save(entity);
        log.info("Demo API key registered for {}", principalId);
    }
}
