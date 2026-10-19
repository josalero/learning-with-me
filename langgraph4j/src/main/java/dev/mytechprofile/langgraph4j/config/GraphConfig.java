package dev.mytechprofile.langgraph4j.config;

import java.nio.file.Path;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

import dev.mytechprofile.langgraph4j.cursor.CliCursorImplementer;
import dev.mytechprofile.langgraph4j.cursor.CloudCursorImplementer;
import dev.mytechprofile.langgraph4j.cursor.DefaultCursorCliExecutor;
import dev.mytechprofile.langgraph4j.cursor.ImplementationPort;
import dev.mytechprofile.langgraph4j.cursor.StubCursorImplementer;
import dev.mytechprofile.langgraph4j.graph.ImplementationGraphFactory;
import dev.mytechprofile.langgraph4j.graph.TaskOrchestrator;
import dev.mytechprofile.langgraph4j.graph.TaskState;

/**
 * Wires the compiled graph and the stub or local CLI implementer.
 */
@Configuration
@EnableConfigurationProperties(CursorProperties.class)
public class GraphConfig {

    @Bean
    MemorySaver memorySaver() {
        return new MemorySaver();
    }

    @Bean
    ImplementationPort implementationPort(
            CursorProperties properties, ObjectProvider<RestClient.Builder> restClientBuilders) {
        Path root = Path.of("").toAbsolutePath();
        Path seedDir = root.resolve(properties.seedDir());
        Path workspaceDir = root.resolve(properties.workspaceDir());
        Path gradleWrapper = root.resolve("gradlew");
        if (properties.mode() == CursorProperties.Mode.CLI) {
            return new CliCursorImplementer(
                    properties.cliCommand(),
                    properties.apiKey(),
                    properties.model(),
                    properties.implementTimeout(),
                    seedDir,
                    workspaceDir,
                    gradleWrapper,
                    new DefaultCursorCliExecutor());
        }
        if (properties.mode() == CursorProperties.Mode.CLOUD) {
            if (properties.apiKey() == null || properties.apiKey().isBlank()) {
                throw new IllegalStateException(
                        "cursor.mode=cloud requires CURSOR_API_KEY. Prefer cursor.mode=cli for the local agent.");
            }
            RestClient.Builder restClientBuilder =
                    restClientBuilders.getIfAvailable(RestClient::builder);
            RestClient client = restClientBuilder
                    .clone()
                    .baseUrl(properties.apiBaseUrl())
                    .defaultHeaders(headers -> headers.setBasicAuth(properties.apiKey(), ""))
                    .build();
            return new CloudCursorImplementer(
                    client,
                    properties.model(),
                    properties.pollInterval(),
                    properties.pollTimeout(),
                    properties.autoCreatePr());
        }
        return new StubCursorImplementer(seedDir, workspaceDir, gradleWrapper);
    }

    @Bean
    CompiledGraph<TaskState> compiledGraph(ImplementationPort port, MemorySaver saver) throws GraphStateException {
        return ImplementationGraphFactory.compile(port, saver);
    }

    @Bean
    TaskOrchestrator taskOrchestrator(CompiledGraph<TaskState> compiledGraph) {
        return new TaskOrchestrator(compiledGraph);
    }
}
