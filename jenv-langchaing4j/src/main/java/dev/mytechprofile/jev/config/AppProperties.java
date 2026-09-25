package dev.mytechprofile.jev.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Lab switches bound from {@code app.*}.
 *
 * <p>A {@code humanReviewBelow} outside 0.0 to 1.0 fails startup.
 *
 * @param demo when {@code true}, {@code DemoRunner} classifies the classpath samples at startup
 * @param humanReviewBelow confidence strictly below this value sets {@code needsHumanReview}; default {@code 0.70}
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        boolean demo,
        @DecimalMin("0.0") @DecimalMax("1.0") double humanReviewBelow) {
}
