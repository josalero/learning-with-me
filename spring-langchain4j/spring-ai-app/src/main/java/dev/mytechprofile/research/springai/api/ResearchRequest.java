package dev.mytechprofile.research.springai.api;

import dev.mytechprofile.research.springai.domain.ResearchCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for starting a research pipeline run.
 *
 * <p>Example:
 * <pre>{@code
 * POST /api/v1/research
 * { "topic": "Java virtual threads", "depth": 3 }
 * }</pre>
 */
public record ResearchRequest(
        @NotBlank @Size(max = 500) String topic,
        @Min(1) @Max(5) Integer depth
) {
    public ResearchCommand toCommand() {
        return new ResearchCommand(topic, depth);
    }
}
