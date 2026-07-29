package com.example.mcpgateway.echo;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.connector.Connector;
import dev.mytechprofile.gateway.connector.ConnectorCapabilities;
import dev.mytechprofile.gateway.connector.ExecutionResult;
import dev.mytechprofile.gateway.connector.OperationAccess;
import dev.mytechprofile.gateway.connector.OperationDescriptor;
import dev.mytechprofile.gateway.connector.OperationInvocation;
import dev.mytechprofile.gateway.connector.ToolExecutionContext;

/**
 * Third-party-style connector kept outside the gateway package namespace.
 *
 * <p>It demonstrates that a connector JAR can register itself through Spring
 * Boot auto-configuration and consume custom {@code settings} without changing
 * gateway code.
 */
public final class EchoConnector implements Connector {

    @Override
    public String type() {
        return "echo";
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return ConnectorCapabilities.declaredOnly();
    }

    @Override
    public ConnectionDefinition configure(
            ConnectionDefinition connection, JsonNode configuration) {
        String prefix = configuration.path("settings").path("prefix").asString("");
        return new ConnectionDefinition(
                connection.name(),
                connection.type(),
                connection.baseUri(),
                connection.specificationLocation(),
                connection.connectTimeout(),
                connection.readTimeout(),
                Map.of("prefix", prefix));
    }

    @Override
    public List<OperationDescriptor> discover(ConnectionDefinition connection) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putObject("properties").putObject("message").put("type", "string");
        schema.putArray("required").add("message");
        return List.of(new OperationDescriptor(
                "echo",
                "Echo a message",
                schema,
                null,
                OperationAccess.READ));
    }

    @Override
    public ExecutionResult execute(
            OperationInvocation invocation, ToolExecutionContext context) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put(
                "message",
                invocation.connection().attributes().getOrDefault("prefix", "")
                        + invocation.arguments().get("message"));
        return new ExecutionResult.Success(payload, 200);
    }
}
