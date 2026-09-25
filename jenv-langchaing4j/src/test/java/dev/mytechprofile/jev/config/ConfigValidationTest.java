package dev.mytechprofile.jev.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class ConfigValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(ClientConfig.class);

    @Test
    void humanReviewThresholdAboveOneFailsStartup() {
        runner.withPropertyValues(properties("1.5"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void humanReviewThresholdInsideTheRangeStarts() {
        runner.withPropertyValues(properties("0.70"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static String[] properties(String humanReviewBelow) {
        return new String[] {
                "app.demo=false",
                "app.human-review-below=" + humanReviewBelow,
                "openrouter.api-key=",
                "openrouter.base-url=https://openrouter.ai/api",
                "openrouter.decisions-path=/alpha/decisions",
                "openrouter.jev-model=typesafe/jev-1.13",
                "openrouter.chat-base-url=https://openrouter.ai/api/v1",
                "openrouter.chat-model=openai/gpt-4o-mini",
                "openrouter.explain=false"
        };
    }
}
