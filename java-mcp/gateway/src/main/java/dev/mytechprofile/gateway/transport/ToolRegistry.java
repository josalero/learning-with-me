package dev.mytechprofile.gateway.transport;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.mytechprofile.gateway.model.CompiledTool;

/**
 * Runtime registry of compiled tools published to MCP clients.
 *
 * <p><strong>When to use:</strong> inject wherever you need lookup by tool name.
 * Populated once at startup from {@link CompiledToolCatalog}.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * CompiledTool tool = toolRegistry.require("search_candidates");
 * }</pre>
 *
 * <p>Duplicate tool names fail application startup.
 */
@Component
public class ToolRegistry {

    private final Map<String, CompiledTool> tools;

    /**
     * Indexes the catalog by tool name.
     *
     * @param catalog compiled catalog bean
     */
    public ToolRegistry(CompiledToolCatalog catalog) {
        this.tools = catalog.tools().stream()
                .collect(Collectors.toUnmodifiableMap(CompiledTool::name, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("duplicate tool: " + a.name());
                }));
    }

    /**
     * Returns all registered tools.
     *
     * @return unmodifiable collection
     */
    public Collection<CompiledTool> all() {
        return tools.values();
    }

    /**
     * Looks up a tool by MCP name.
     *
     * @param name tool name
     * @return compiled tool
     * @throws IllegalArgumentException if the tool is unknown
     */
    public CompiledTool require(String name) {
        CompiledTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("unknown tool: " + name);
        }
        return tool;
    }
}
