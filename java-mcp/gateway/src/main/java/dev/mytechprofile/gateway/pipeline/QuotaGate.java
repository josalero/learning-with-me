package dev.mytechprofile.gateway.pipeline;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.model.GatewayError;
import dev.mytechprofile.gateway.model.GatewayException;
import dev.mytechprofile.gateway.model.QuotaPolicy;

/**
 * In-memory per-subject/tool quotas (accepted POC limitation: per-instance).
 */
@Component
public class QuotaGate {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    /**
     * Consumes one token for the subject/tool pair when a quota is configured.
     *
     * @param tool compiled tool
     * @param context invocation context
     */
    public void consume(CompiledTool tool, ToolExecutionContext context) {
        QuotaPolicy policy = tool.quotaPolicy();
        if (policy == null || policy.unlimited()) {
            return;
        }
        String key = context.identity().subject() + '|' + tool.name();
        Bucket bucket = buckets.get(key, ignored -> newBucket(policy));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new GatewayException(
                    new GatewayError.QuotaExceeded(Duration.ofNanos(probe.getNanosToWaitForRefill())));
        }
    }

    /**
     * Snapshot remaining tokens for tests (does not consume).
     *
     * @param tool tool name
     * @param subject subject id
     * @return remaining tokens or {@code -1} when no bucket exists
     */
    public long remaining(String subject, String tool) {
        Bucket bucket = buckets.getIfPresent(subject + '|' + tool);
        return bucket == null ? -1L : bucket.getAvailableTokens();
    }

    private static Bucket newBucket(QuotaPolicy policy) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(policy.capacity())
                .refillIntervally(policy.capacity(), policy.window())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
