package dev.mytechprofile.aigateway.gateway.budget;

import org.springframework.stereotype.Component;

/**
 * Cost in micro-USD (1,000,000 = 1 USD) from token counts and per-1K prices.
 *
 * {@snippet :
 * long cost = CostCalculator.costMicros(600, 150, 500, 1_500);   // 300 + 225 = 525, one stub turn
 * }
 */
@Component
public class CostCalculator {

    private final ModelPricingRepository pricing;

    public CostCalculator(ModelPricingRepository pricing) {
        this.pricing = pricing;
    }

    /** Cost in micro-USD at the stored price of {@code modelId}. Throws if the model has no price row. */
    public long costMicros(String modelId, int inputTokens, int outputTokens) {
        var row = pricing.findById(modelId).orElseThrow(() -> new IllegalStateException("Missing model price"));
        return costMicros(inputTokens, outputTokens, row.getInputMicrosPer1k(), row.getOutputMicrosPer1k());
    }

    /** Prices are per 1,000 tokens; each side is rounded down separately. */
    public static long costMicros(int inputTokens, int outputTokens, int inputPer1k, int outputPer1k) {
        return (long) inputTokens * inputPer1k / 1000L + (long) outputTokens * outputPer1k / 1000L;
    }
}
