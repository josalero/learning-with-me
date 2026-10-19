package dev.mytechprofile.aigateway.gateway.budget;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Reserves budget in Redis before each model or tool call and rolls the increment back when a limit is exceeded.
 *
 * <p>Counters are {@code budget:{executionId}:tokens|modelCalls|toolCalls|costMicros}, with a 24 h TTL.
 * {@snippet :
 * try {
 *     budget.reserveToolCall(executionId, policy);   // INCRBY toolCalls 1
 * } catch (BudgetExhaustedException ex) {
 *     // DECRBY already ran, so the counter still equals the limit; the run ends BUDGET_EXHAUSTED
 * }
 * }
 */
@Component
public class BudgetGuard {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public BudgetGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Reserves one model call, the estimated tokens, and the estimated cost. If any counter would pass its
     * limit, the counters already incremented are rolled back and {@link BudgetExhaustedException} is thrown.
     */
    public void reserveModelCall(UUID executionId, BudgetPolicy policy, int tokens, long costMicros) {
        String prefix = prefix(executionId);
        reserve(prefix + "modelCalls", 1, policy.maxModelCalls(), BudgetDimension.MODEL_CALLS);
        try {
            reserve(prefix + "tokens", tokens, policy.maxTokens(), BudgetDimension.TOKENS);
        } catch (BudgetExhaustedException ex) {
            adjust(prefix + "modelCalls", -1);
            throw ex;
        }
        try {
            reserve(prefix + "costMicros", costMicros, policy.maxCostMicros(), BudgetDimension.COST);
        } catch (BudgetExhaustedException ex) {
            adjust(prefix + "tokens", -tokens);
            adjust(prefix + "modelCalls", -1);
            throw ex;
        }
    }

    /** Replaces the estimate with what the model reported. May go up or down; it never throws. */
    public void reconcileModelUsage(
            UUID executionId, int reservedTokens, int actualTokens, long reservedCost, long actualCost) {
        String prefix = prefix(executionId);
        adjust(prefix + "tokens", (long) actualTokens - reservedTokens);
        adjust(prefix + "costMicros", actualCost - reservedCost);
    }

    /** Reserves one tool call before authorization, so denied calls spend budget too. */
    public void reserveToolCall(UUID executionId, BudgetPolicy policy) {
        reserve(prefix(executionId) + "toolCalls", 1, policy.maxToolCalls(), BudgetDimension.TOOL_CALLS);
    }

    /** Current value of one counter ({@code tokens}, {@code modelCalls}, {@code toolCalls}, {@code costMicros}). */
    public long current(UUID executionId, String counter) {
        String value = redis.opsForValue().get(prefix(executionId) + counter);
        return value == null ? 0 : Long.parseLong(value);
    }

    private void reserve(String key, long amount, long limit, BudgetDimension dimension) {
        Long updated = redis.opsForValue().increment(key, amount);
        redis.expire(key, TTL);
        if (updated != null && updated > limit) {
            redis.opsForValue().increment(key, -amount);
            throw new BudgetExhaustedException(dimension);
        }
    }

    private void adjust(String key, long delta) {
        if (delta != 0) {
            redis.opsForValue().increment(key, delta);
            redis.expire(key, TTL);
        }
    }

    private static String prefix(UUID executionId) {
        return "budget:" + executionId + ":";
    }
}
