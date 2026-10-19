package dev.mytechprofile.aigateway.shared.api;

import dev.mytechprofile.aigateway.gateway.audit.StepType;
import dev.mytechprofile.aigateway.gateway.authority.Decision;
import dev.mytechprofile.aigateway.gateway.authority.ReasonCode;

/**
 * One audited step. {@code tool}, {@code decision}, {@code reasonCode}, and the HMACs are {@code null} for model steps.
 *
 * {@snippet lang=json :
 * {"seq":4,"type":"TOOL","tool":"execute_status_transition","decision":"DENIED","reasonCode":"TOOL_NOT_GRANTED",
 *  "argsHmac":"9c1e...","resultHmac":"e07a...","inputTokens":0,"outputTokens":0,"spanId":"00f067aa0ba902b7"}
 * }
 */
public record StepView(
        int seq,
        StepType type,
        String tool,
        Decision decision,
        ReasonCode reasonCode,
        String argsHmac,
        String resultHmac,
        Integer inputTokens,
        Integer outputTokens,
        String spanId) {}
