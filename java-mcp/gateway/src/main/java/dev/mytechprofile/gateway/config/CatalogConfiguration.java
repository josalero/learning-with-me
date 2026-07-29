package dev.mytechprofile.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.transport.CompiledToolCatalog;

/**
 * Wires YAML-driven catalog compilation.
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class CatalogConfiguration {

    /**
     * Shared {@link RestClient.Builder} for OpenAPI connector HTTP calls.
     *
     * @return mutable builder prototype
     */
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Compiles catalog packs into the MCP-visible tool catalog.
     *
     * @param properties bound gateway YAML
     * @param compiler tool compiler
     * @return compiled catalog
     */
    @Bean
    CompiledToolCatalog compiledToolCatalog(GatewayProperties properties, ToolCompiler compiler) {
        List<CompiledTool> tools = compiler.compile(properties);
        return new CompiledToolCatalog(tools);
    }
}
