package dev.mytechprofile.aigateway.budget;

import org.springframework.stereotype.Component;

import dev.mytechprofile.aigateway.persistence.ModelPricingRepository;

@Component
public class CostCalculator {

    private final ModelPricingRepository pricing;

    public CostCalculator(ModelPricingRepository pricing) {
        this.pricing = pricing;
    }

    public long costMicros(String modelId, int inputTokens, int outputTokens) {
        var row = pricing.findById(modelId).orElseThrow(() -> new IllegalStateException("Missing model price"));
        return costMicros(inputTokens, outputTokens, row.getInputMicrosPer1k(), row.getOutputMicrosPer1k());
    }

    public static long costMicros(int inputTokens, int outputTokens, int inputPer1k, int outputPer1k) {
        return (long) inputTokens * inputPer1k / 1000L + (long) outputTokens * outputPer1k / 1000L;
    }
}
