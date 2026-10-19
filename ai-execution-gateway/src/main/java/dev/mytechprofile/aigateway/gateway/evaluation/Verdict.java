package dev.mytechprofile.aigateway.gateway.evaluation;

/**
 * Quality of a finished run, separate from its status.
 */
public enum Verdict {
    PASSED,
    POLICY_VIOLATION_ATTEMPTED,
    UNSUPPORTED_CLAIM,
    NOT_EVALUATED
}
