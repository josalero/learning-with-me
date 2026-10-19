package dev.mytechprofile.aigateway.config;

import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(name = "executionExecutor")
    ThreadPoolTaskExecutor executionExecutor(GatewayProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.dispatcher().corePoolSize());
        executor.setMaxPoolSize(properties.dispatcher().maxPoolSize());
        executor.setQueueCapacity(properties.dispatcher().queueCapacity());
        executor.setThreadNamePrefix("ai-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
