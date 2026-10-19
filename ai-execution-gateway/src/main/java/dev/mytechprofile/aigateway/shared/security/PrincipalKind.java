package dev.mytechprofile.aigateway.shared.security;

/**
 * {@code USER} is a person (only users may approve transitions); {@code SERVICE} is a system such as a nightly batch.
 */
public enum PrincipalKind {
    USER,
    SERVICE
}
