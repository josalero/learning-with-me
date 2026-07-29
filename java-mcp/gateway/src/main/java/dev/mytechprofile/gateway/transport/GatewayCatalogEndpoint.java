package dev.mytechprofile.gateway.transport;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import dev.mytechprofile.gateway.model.CompiledTool;

/**
 * Sanitized operational view of the compiled catalog.
 *
 * <p>It deliberately excludes connection URLs, credentials, schemas, and policy
 * internals. Expose the actuator endpoint only to gateway operators.
 */
@Component
@Endpoint(id = "gatewaycatalog")
public class GatewayCatalogEndpoint {

    private final ToolRegistry registry;

    public GatewayCatalogEndpoint(ToolRegistry registry) {
        this.registry = registry;
    }

    @ReadOperation
    public List<Map<String, Object>> catalog() {
        return registry.all().stream()
                .sorted(Comparator.comparing(CompiledTool::name))
                .map(tool -> Map.<String, Object>of(
                        "name", tool.name(),
                        "description", tool.description(),
                        "connection", tool.connection().name(),
                        "connector", tool.connection().type(),
                        "mode", tool.mode().name(),
                        "owner", tool.metadata().owner(),
                        "version", tool.metadata().version(),
                        "deprecated", tool.metadata().deprecated()))
                .toList();
    }
}
