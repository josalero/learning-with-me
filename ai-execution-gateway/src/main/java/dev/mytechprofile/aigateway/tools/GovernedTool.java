package dev.mytechprofile.aigateway.tools;

import tools.jackson.databind.JsonNode;

public interface GovernedTool {
    String name();

    String execute(JsonNode arguments, ToolExecutionContext context);
}
