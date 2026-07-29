package dev.mytechprofile.gateway.connector.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import dev.mytechprofile.gateway.connector.ConnectionBearerTokenResolver;
import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.connector.ExecutionResult;
import dev.mytechprofile.gateway.connector.OperationAccess;
import dev.mytechprofile.gateway.connector.OperationInvocation;
import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.connector.AuthenticationType;
import dev.mytechprofile.gateway.connector.CallerIdentity;
import dev.mytechprofile.gateway.connector.testing.ConnectorContractAssertions;

class OpenApiConnectorGenericExecutionTest {

    private HttpServer server;
    private OpenApiConnector connector;
    private ConnectionDefinition connection;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/widgets", this::handle);
        server.start();

        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        connector = new OpenApiConnector(
                new ObjectMapper(),
                RestClient.builder(),
                beans.getBeanProvider(ConnectionBearerTokenResolver.class));
        connection = new ConnectionDefinition(
                "widgets",
                "openapi",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "classpath:/openapi/generic.yaml",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void discoversAndExecutesOperationsWithoutBusinessSpecificBindings() {
        var operations = ConnectorContractAssertions.assertDiscoveryContract(connector, connection);
        assertThat(operations)
                .extracting(operation -> operation.operationId() + ":" + operation.access())
                .containsExactlyInAnyOrder(
                        "getWidget:" + OperationAccess.READ,
                        "createWidget:" + OperationAccess.WRITE);
        assertThat(operations.stream()
                        .filter(operation -> operation.operationId().equals("createWidget"))
                        .findFirst()
                        .orElseThrow()
                        .inputSchema()
                        .path("properties")
                        .has("name"))
                .isTrue();

        ExecutionResult get = connector.execute(
                new OperationInvocation(
                        connection,
                        "getWidget",
                        Map.of("widgetId", "W-1", "verbose", true)),
                context());
        assertThat(get).isInstanceOf(ExecutionResult.Success.class);
        assertThat(((ExecutionResult.Success) get).payload().path("path").asString())
                .isEqualTo("/widgets/W-1?verbose=true");

        ExecutionResult post = connector.execute(
                new OperationInvocation(
                        connection,
                        "createWidget",
                        Map.of("name", "Desk", "color", "blue")),
                context());
        assertThat(post).isInstanceOf(ExecutionResult.Success.class);
        assertThat(((ExecutionResult.Success) post).statusCode()).isEqualTo(201);
        assertThat(((ExecutionResult.Success) post).payload().toString())
                .contains("Desk")
                .contains("blue");
    }

    private void handle(HttpExchange exchange) {
        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String json;
            int status;
            if ("POST".equals(exchange.getRequestMethod())) {
                status = 201;
                json = "{\"body\":" + quote(requestBody) + "}";
            } else {
                status = 200;
                json = "{\"path\":" + quote(exchange.getRequestURI().toString()) + "}";
            }
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            exchange.close();
        }
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private ToolExecutionContext context() {
        return ToolExecutionContext.start(new CallerIdentity(
                "test",
                "tenant",
                java.util.Set.of(),
                java.util.Set.of(),
                AuthenticationType.NONE));
    }
}
