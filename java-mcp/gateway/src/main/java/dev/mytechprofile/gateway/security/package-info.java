/**
 * Identity resolution and HTTP security profiles for the gateway.
 *
 * <p>Local development uses a synthetic {@code CallerIdentity}. The JWT profile
 * swaps the resolver without changing tools or connectors.
 */
package dev.mytechprofile.gateway.security;
