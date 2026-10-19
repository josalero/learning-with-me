package dev.mytechprofile.aigateway.shared.api;

import java.util.UUID;

/**
 * A transition the agent requested, without candidate text. Its id is what a user approves.
 */
public record TransitionRequestView(UUID transitionRequestId, String status, String from, String to) {}
