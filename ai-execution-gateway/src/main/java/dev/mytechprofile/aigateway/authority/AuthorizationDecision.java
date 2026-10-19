package dev.mytechprofile.aigateway.authority;

import dev.mytechprofile.aigateway.domain.ReasonCode;

public record AuthorizationDecision(ReasonCode reason) {
    public boolean permitted() {
        return reason == ReasonCode.PERMITTED;
    }
}
