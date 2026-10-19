package dev.mytechprofile.aigateway.shared.config;

import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Shared beans: the UTC clock and the bounded {@code executionExecutor} pool.
 *
 * <p>The pool aborts when its queue is full; {@code ExecutionService} turns that into {@code 503}. Its max size must
 * stay below the Hikari pool size, because each running execution holds one connection.
 */
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
