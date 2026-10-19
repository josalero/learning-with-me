package dev.mytechprofile.aigateway.shared.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/v1/executions}. All fields are required; the tenant is never part of the body.
 *
 * {@snippet lang=json :
 * {"agentId":"screening-agent","taskType":"CANDIDATE_EVALUATION","candidateId":"C-101","requisitionId":"R-7"}
 * }
 */
public record CreateExecutionRequest(
        @NotBlank String agentId,
        @NotBlank String taskType,
        @NotBlank String candidateId,
        @NotBlank String requisitionId) {}
