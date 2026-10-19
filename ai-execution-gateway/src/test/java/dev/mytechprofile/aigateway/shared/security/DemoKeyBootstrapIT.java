package dev.mytechprofile.aigateway.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.mytechprofile.aigateway.shared.config.GatewayProperties;
import dev.mytechprofile.aigateway.support.GatewayIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;

class DemoKeyBootstrapIT extends GatewayIntegrationTest {

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void restartReplacesAnExistingDemoKeyInsteadOfFailingStartup() {
        String raw = "gw_" + "b".repeat(43);
        DemoKeyBootstrap bootstrap = new DemoKeyBootstrap(propertiesWithRecruiterKey(raw), apiKeys);

        bootstrap.run(new DefaultApplicationArguments(new String[0]));
        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        assertThat(jdbc.queryForList(
                        "select key_hash from api_key where principal_id = 'recruiter-alice'", String.class))
                .containsExactly(ApiKeyHasher.sha256(raw));
    }

    private static GatewayProperties propertiesWithRecruiterKey(String raw) {
        return new GatewayProperties(
                new GatewayProperties.Audit("hmac"),
                new GatewayProperties.Model(256),
                new GatewayProperties.Pricing(new GatewayProperties.Pricing.Live("", 150, 600)),
                new GatewayProperties.DemoKeys(raw, "", ""),
                new GatewayProperties.Dispatcher(4, 8, 50));
    }
}
