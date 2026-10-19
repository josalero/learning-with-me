package dev.mytechprofile.aigateway.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

@TestConfiguration
public class TestTelemetryConfig {

    @Bean
    @Primary
    InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }

    @Bean
    @Primary
    OpenTelemetry openTelemetry(InMemorySpanExporter exporter) {
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        return OpenTelemetrySdk.builder().setTracerProvider(provider).build();
    }
}
