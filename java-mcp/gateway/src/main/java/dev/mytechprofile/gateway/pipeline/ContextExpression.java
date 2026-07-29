package dev.mytechprofile.gateway.pipeline;

import dev.mytechprofile.gateway.connector.ToolExecutionContext;

/**
 * Closed set of trusted-context expressions resolved from {@link ToolExecutionContext}.
 *
 * <p><strong>When to use:</strong> parsed from YAML {@code context-mappings} values such as
 * {@code ${identity.tenantId}}. Not a general expression language.
 */
public sealed interface ContextExpression {

    /**
     * Resolves the expression against the current invocation context.
     *
     * @param context invocation context
     * @return injected value
     */
    Object resolve(ToolExecutionContext context);

    /** Injects {@code identity.tenantId}. */
    record TenantId() implements ContextExpression {
        @Override
        public Object resolve(ToolExecutionContext context) {
            return context.identity().tenantId();
        }
    }

    /** Injects {@code identity.subject}. */
    record Subject() implements ContextExpression {
        @Override
        public Object resolve(ToolExecutionContext context) {
            return context.identity().subject();
        }
    }

    /** Injects the invocation correlation id. */
    record CorrelationId() implements ContextExpression {
        @Override
        public Object resolve(ToolExecutionContext context) {
            return context.correlationId();
        }
    }
}
