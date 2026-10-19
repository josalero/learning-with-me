package dev.mytechprofile.aigateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import dev.mytechprofile.aigateway.config.GatewayProperties;
import dev.mytechprofile.aigateway.persistence.ApiKeyEntity;
import dev.mytechprofile.aigateway.persistence.ApiKeyRepository;

class DemoKeyBootstrapTest {

    @Test
    void storesOnlyTheHashOfAConfiguredKey() {
        ApiKeyRepository apiKeys = mock(ApiKeyRepository.class);
        String raw = "gw_" + "a".repeat(43);
        GatewayProperties properties = new GatewayProperties(
                new GatewayProperties.Audit("hmac"),
                new GatewayProperties.Model(256),
                new GatewayProperties.Pricing(new GatewayProperties.Pricing.Live("", 150, 600)),
                new GatewayProperties.DemoKeys(raw, "", ""),
                new GatewayProperties.Dispatcher(4, 8, 50));
        new DemoKeyBootstrap(properties, apiKeys).run(new DefaultApplicationArguments(new String[0]));

        verify(apiKeys).deleteByPrincipalId("recruiter-alice");
        ArgumentCaptor<ApiKeyEntity> saved = ArgumentCaptor.forClass(ApiKeyEntity.class);
        verify(apiKeys).save(saved.capture());
        assertThat(saved.getValue().getKeyHash()).isEqualTo(ApiKeyHasher.sha256(raw));
        assertThat(saved.getValue().getKeyHash()).doesNotContain(raw);
    }

    @Test
    void skipsAMissingKey() {
        ApiKeyRepository apiKeys = mock(ApiKeyRepository.class);
        GatewayProperties properties = new GatewayProperties(
                new GatewayProperties.Audit("hmac"),
                new GatewayProperties.Model(256),
                new GatewayProperties.Pricing(new GatewayProperties.Pricing.Live("", 150, 600)),
                new GatewayProperties.DemoKeys("", "", ""),
                new GatewayProperties.Dispatcher(4, 8, 50));
        new DemoKeyBootstrap(properties, apiKeys).run(new DefaultApplicationArguments(new String[0]));
        verifyNoInteractions(apiKeys);
    }
}
