package dev.mytechprofile.aigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External settings for audit hashing, the live model, demo keys, and the dispatcher pool.
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        Audit audit,
        Model model,
        Pricing pricing,
        DemoKeys demoKeys,
        Dispatcher dispatcher) {

    public record Audit(String hmacKey) {}

    public record Model(int maxOutputTokens) {}

    public record Pricing(Live live) {
        public record Live(String modelId, int inputMicrosPer1k, int outputMicrosPer1k) {}
    }

    public record DemoKeys(String recruiter, String ats, String globex) {}

    public record Dispatcher(int corePoolSize, int maxPoolSize, int queueCapacity) {}
}
