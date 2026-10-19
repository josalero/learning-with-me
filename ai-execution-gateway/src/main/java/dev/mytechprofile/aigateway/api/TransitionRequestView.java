package dev.mytechprofile.aigateway.api;

import java.util.UUID;

public record TransitionRequestView(UUID transitionRequestId, String status, String from, String to) {}
