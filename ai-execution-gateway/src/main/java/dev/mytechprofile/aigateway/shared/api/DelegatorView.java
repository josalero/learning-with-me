package dev.mytechprofile.aigateway.shared.api;

import dev.mytechprofile.aigateway.shared.security.PrincipalKind;

/**
 * The principal that delegated the work: a {@code USER} such as {@code alice} or a {@code SERVICE} such as {@code ats-nightly}.
 */
public record DelegatorView(String id, PrincipalKind kind) {}
