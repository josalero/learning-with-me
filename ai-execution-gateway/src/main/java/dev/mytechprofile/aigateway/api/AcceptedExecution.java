package dev.mytechprofile.aigateway.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

import dev.mytechprofile.aigateway.domain.Decision;
import dev.mytechprofile.aigateway.domain.ExecutionStatus;
import dev.mytechprofile.aigateway.domain.PrincipalKind;
import dev.mytechprofile.aigateway.domain.ReasonCode;
import dev.mytechprofile.aigateway.domain.StepType;
import dev.mytechprofile.aigateway.domain.Verdict;

public record AcceptedExecution(UUID executionId, String traceId, ExecutionStatus status) {}
