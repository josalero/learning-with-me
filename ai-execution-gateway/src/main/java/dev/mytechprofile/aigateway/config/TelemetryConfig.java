package dev.mytechprofile.aigateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

@Configuration
public class TelemetryConfig {

    @Bean
    @ConditionalOnMissingBean
    Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("dev.mytechprofile.aigateway");
    }
}
