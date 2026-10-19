package dev.mytechprofile.aigateway.gateway.budget;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CostCalculatorTest {

    @Test
    void stubTurnCostsFiveHundredTwentyFiveMicroUsd() {
        assertThat(CostCalculator.costMicros(600, 150, 500, 1500)).isEqualTo(525);
    }

    @Test
    void looksUpTheStoredPrice() {
        ModelPricingRepository pricing = mock(ModelPricingRepository.class);
        ModelPricingEntity row = new ModelPricingEntity();
        row.setInputMicrosPer1k(500);
        row.setOutputMicrosPer1k(1500);
        when(pricing.findById("stub-model")).thenReturn(Optional.of(row));

        assertThat(new CostCalculator(pricing).costMicros("stub-model", 600, 150)).isEqualTo(525);
    }
}
