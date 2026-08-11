package dev.mytechprofile.research.langchain4j.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Shared infrastructure beans (Jackson, SSE executor).
 *
 * <p>Boot 4.1 does not always expose an {@link ObjectMapper} bean with the web starter alone.
 */
@Configuration
public class AppConfig {

    @Bean
    ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    @Bean(destroyMethod = "close")
    ExecutorService researchSseExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
