package dev.mytechprofile.aigateway.gateway.tools;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Every {@link GovernedTool} bean, indexed by name.
 */
@Component
public class ToolRegistry {

    private final Map<String, GovernedTool> tools;

    public ToolRegistry(List<GovernedTool> tools) {
        this.tools = tools.stream().collect(Collectors.toMap(GovernedTool::name, Function.identity()));
    }

    /** Looks up a tool by name. Call only after authorization returned {@code PERMITTED}. */
    public GovernedTool get(String name) {
        GovernedTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalStateException("Tool is not registered");
        }
        return tool;
    }
}
