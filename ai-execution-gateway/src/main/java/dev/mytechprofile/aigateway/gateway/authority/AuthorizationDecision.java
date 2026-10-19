package dev.mytechprofile.aigateway.gateway.authority;

/**
 * Outcome of {@link ToolAuthorizer#authorize}: {@code PERMITTED} or the first failing check.
 */
public record AuthorizationDecision(ReasonCode reason) {
    /** True only for {@code PERMITTED}; any other reason means the tool must not run. */
    public boolean permitted() {
        return reason == ReasonCode.PERMITTED;
    }
}
