package dev.mytechprofile.aigateway.gateway.budget;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetGuardTest {

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static BudgetGuard guard;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        guard = new BudgetGuard(redis);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        REDIS.stop();
    }

    @Test
    void toolReservationRollsBackWhenTheLimitIsExceeded() {
        UUID executionId = UUID.randomUUID();
        BudgetPolicy policy = new BudgetPolicy(2, 10, 1000, 1000);
        guard.reserveToolCall(executionId, policy);
        guard.reserveToolCall(executionId, policy);

        assertThatThrownBy(() -> guard.reserveToolCall(executionId, policy))
                .isInstanceOf(BudgetExhaustedException.class)
                .extracting("dimension")
                .isEqualTo(BudgetDimension.TOOL_CALLS);
        assertThat(guard.current(executionId, "toolCalls")).isEqualTo(2);
    }

    @Test
    void tokenFailureRollsBackTheModelCallReservation() {
        UUID executionId = UUID.randomUUID();
        BudgetPolicy policy = new BudgetPolicy(10, 10, 100, 10_000);
        guard.reserveModelCall(executionId, policy, 80, 10);

        assertThatThrownBy(() -> guard.reserveModelCall(executionId, policy, 80, 10))
                .isInstanceOf(BudgetExhaustedException.class)
                .extracting("dimension")
                .isEqualTo(BudgetDimension.TOKENS);
        assertThat(guard.current(executionId, "modelCalls")).isEqualTo(1);
        assertThat(guard.current(executionId, "tokens")).isEqualTo(80);
    }

    @Test
    void costFailureRollsBackTokensAndModelCalls() {
        UUID executionId = UUID.randomUUID();
        BudgetPolicy policy = new BudgetPolicy(10, 10, 10_000, 100);
        guard.reserveModelCall(executionId, policy, 10, 80);

        assertThatThrownBy(() -> guard.reserveModelCall(executionId, policy, 10, 80))
                .isInstanceOf(BudgetExhaustedException.class)
                .extracting("dimension")
                .isEqualTo(BudgetDimension.COST);
        assertThat(guard.current(executionId, "modelCalls")).isEqualTo(1);
        assertThat(guard.current(executionId, "tokens")).isEqualTo(10);
        assertThat(guard.current(executionId, "costMicros")).isEqualTo(80);
    }
}
