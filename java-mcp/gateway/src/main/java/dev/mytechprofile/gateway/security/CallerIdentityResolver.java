package dev.mytechprofile.gateway.security;

import dev.mytechprofile.gateway.connector.CallerIdentity;

/**
 * Resolves the caller for the current MCP invocation.
 *
 * <p><strong>When to use:</strong> inject into the pipeline. Provide one bean per
 * security profile ({@code local} synthetic identity, {@code jwt} claim mapping).
 *
 * <p><strong>Example — local profile:</strong>
 * <pre>{@code
 * @Bean
 * CallerIdentityResolver localIdentityResolver() {
 *     return () -> new CallerIdentity(
 *         "local-developer", "demo-tenant",
 *         Set.of("RECRUITER"), Set.of("tools.read"),
 *         AuthenticationType.NONE);
 * }
 * }</pre>
 *
 * <p>Identity must never be taken from model-supplied tool arguments.
 */
@FunctionalInterface
public interface CallerIdentityResolver {

    /**
     * Resolves the current caller.
     *
     * @return non-null identity including tenant id
     */
    CallerIdentity resolve();
}
