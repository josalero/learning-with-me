package dev.mytechprofile.aigateway.gateway.authority;

/**
 * Why a tool call was permitted or denied. The denial codes are listed in check order.
 */
public enum ReasonCode {
    PERMITTED,
    DELEGATION_MISSING,
    TOOL_NOT_GRANTED,
    SCOPE_MISSING,
    INVALID_ARGUMENTS,
    CANDIDATE_NOT_FOUND,
    INVALID_TRANSITION
}
