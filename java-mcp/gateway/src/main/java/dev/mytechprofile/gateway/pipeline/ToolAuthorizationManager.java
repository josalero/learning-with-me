package dev.mytechprofile.gateway.pipeline;

import java.util.Collections;

import org.springframework.stereotype.Component;

import dev.mytechprofile.gateway.connector.CallerIdentity;
import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.model.AuthorizationPolicy;
import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.model.GatewayError;
import dev.mytechprofile.gateway.model.GatewayException;

/**
 * Enforces tool authorization against the caller's scopes and roles.
 */
@Component
public class ToolAuthorizationManager {

    /**
     * Returns whether the identity may see/call the tool.
     *
     * @param tool compiled tool
     * @param identity caller
     * @return true when scopes and roles satisfy the policy
     */
    public boolean isAllowed(CompiledTool tool, CallerIdentity identity) {
        AuthorizationPolicy policy = tool.authorizationPolicy();
        if (policy == null) {
            return true;
        }
        if (!identity.scopes().containsAll(policy.requiredScopes())) {
            return false;
        }
        if (!policy.requiredRoles().isEmpty()
                && Collections.disjoint(identity.roles(), policy.requiredRoles())) {
            return false;
        }
        return true;
    }

    /**
     * Denies with {@link GatewayError.AccessDenied} when unauthorized.
     *
     * @param tool compiled tool
     * @param context invocation context
     */
    public void authorize(CompiledTool tool, ToolExecutionContext context) {
        if (isAllowed(tool, context.identity())) {
            return;
        }
        AuthorizationPolicy policy = tool.authorizationPolicy();
        if (!context.identity().scopes().containsAll(policy.requiredScopes())) {
            throw new GatewayException(new GatewayError.AccessDenied("missing required scope"));
        }
        throw new GatewayException(new GatewayError.AccessDenied("missing required role"));
    }
}
