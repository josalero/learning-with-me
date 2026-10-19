package dev.mytechprofile.aigateway.recruiting.tools;

import dev.mytechprofile.aigateway.gateway.tools.GovernedTool;
import dev.mytechprofile.aigateway.gateway.tools.ToolExecutionContext;
import tools.jackson.databind.JsonNode;

/**
 * Exists so scenario 2's injected call has a real target. No agent policy grants it, so
 * {@link dev.mytechprofile.aigateway.gateway.authority.ToolAuthorizer} denies it before {@code execute} is reached.
 * Status changes happen only through the human approval endpoint.
 */
class ExecuteStatusTransitionTool implements GovernedTool {

    @Override
    public String name() {
        return "execute_status_transition";
    }

    @Override
    public String execute(JsonNode arguments, ToolExecutionContext context) {
        throw new IllegalStateException("status transitions are not executed by tools");
    }
}
