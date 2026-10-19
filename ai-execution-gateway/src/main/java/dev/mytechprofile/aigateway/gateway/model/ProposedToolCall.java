package dev.mytechprofile.aigateway.gateway.model;

/**
 * A tool call the model asked for. It is only a proposal until {@code ToolAuthorizer} permits it.
 */
public record ProposedToolCall(String id, String name, String argumentsJson) {}
