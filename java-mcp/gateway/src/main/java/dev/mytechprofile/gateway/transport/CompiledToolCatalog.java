package dev.mytechprofile.gateway.transport;

import java.util.List;

import tools.jackson.databind.JsonNode;

import dev.mytechprofile.gateway.model.CompiledTool;

/**
 * Immutable catalog of tools ready for MCP publication.
 *
 * <p><strong>When to use:</strong> provide as a Spring bean consumed by
 * {@link ToolRegistry}. The YAML {@code ToolCompiler} builds the producer.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * @Bean
 * CompiledToolCatalog catalog() {
 *     return new CompiledToolCatalog(List.of(searchCandidatesTool));
 * }
 * }</pre>
 *
 * @param tools compiled tools; names must be unique
 */
public record CompiledToolCatalog(List<CompiledTool> tools) {

    /**
     * Validates the catalog is non-null.
     *
     * @param tools tool list
     */
    public CompiledToolCatalog {
        if (tools == null) {
            throw new IllegalArgumentException("tools must not be null");
        }
        tools = List.copyOf(tools);
    }

    /**
     * Convenience factory.
     *
     * @param tools tools to publish
     * @return catalog
     */
    public static CompiledToolCatalog of(CompiledTool... tools) {
        return new CompiledToolCatalog(List.of(tools));
    }

    /**
     * Returns whether the catalog contains a tool with the given MCP name.
     *
     * @param name MCP tool name
     * @return {@code true} when present
     */
    public boolean contains(String name) {
        return tools.stream().anyMatch(tool -> tool.name().equals(name));
    }

    /**
     * Finds a tool by name.
     *
     * @param name MCP tool name
     * @return tool definition
     * @throws IllegalArgumentException when missing
     */
    public CompiledTool require(String name) {
        return tools.stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown tool: " + name));
    }

    /**
     * Returns the published input schema for a tool, for tests and diagnostics.
     *
     * @param name MCP tool name
     * @return JSON Schema node
     */
    public JsonNode inputSchema(String name) {
        return require(name).inputSchema();
    }
}
