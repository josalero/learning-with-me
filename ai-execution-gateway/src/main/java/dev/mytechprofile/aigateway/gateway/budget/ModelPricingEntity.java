package dev.mytechprofile.aigateway.gateway.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Row of {@code model_pricing}: micro-USD per 1,000 input and output tokens for one model id.
 */
@Entity
@Table(name = "model_pricing")
public class ModelPricingEntity {

    @Id
    @Column(name = "model_id")
    private String modelId;

    @Column(name = "input_micros_per_1k", nullable = false)
    private int inputMicrosPer1k;

    @Column(name = "output_micros_per_1k", nullable = false)
    private int outputMicrosPer1k;

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public int getInputMicrosPer1k() {
        return inputMicrosPer1k;
    }

    public void setInputMicrosPer1k(int inputMicrosPer1k) {
        this.inputMicrosPer1k = inputMicrosPer1k;
    }

    public int getOutputMicrosPer1k() {
        return outputMicrosPer1k;
    }

    public void setOutputMicrosPer1k(int outputMicrosPer1k) {
        this.outputMicrosPer1k = outputMicrosPer1k;
    }
}
