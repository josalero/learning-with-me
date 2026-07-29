# Build an MCP client for the gateway

Use this guide to add the governed gateway to a Spring Boot application. The
client connects over Streamable HTTP MCP, discovers the published tool catalog,
and invokes tools by name.

The working implementation is
[`integrations/mcp-client`](../../integrations/mcp-client/README.md). It uses a
synchronous client because the example performs sequential calls and handles
approval elicitation in the same process.

## 1. Add the client dependency

```gradle
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation 'org.springframework.ai:spring-ai-starter-mcp-client-webflux'
    implementation 'org.springframework.boot:spring-boot-starter'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

The repository's Spring AI dependency management supplies the version. In an
external project, import a Spring AI BOM or specify the version consistently
with the gateway's supported MCP stack.

## 2. Configure the gateway connection

```yaml
spring:
  application:
    name: my-gateway-client
  ai:
    mcp:
      client:
        type: SYNC
        request-timeout: 30s
        annotation-scanner:
          enabled: true
        streamable-http:
          connections:
            gateway:
              url: ${GATEWAY_MCP_URL:http://127.0.0.1:8080}
              endpoint: /mcp
```

`gateway` is the connection name. Elicitation handlers and client
customizations use that name to select this connection.

Set the client request timeout above the gateway's downstream read timeout. The
default example uses 30 seconds for a gateway whose downstream read timeout is
five seconds.

## 3. Discover tools

Spring AI exposes discovered MCP tools as `ToolCallback` instances:

```java
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

@Component
final class GatewayTools {

    private final Map<String, ToolCallback> tools;

    GatewayTools(ToolCallbackProvider provider) {
        this.tools = Arrays.stream(provider.getToolCallbacks())
                .collect(Collectors.toUnmodifiableMap(
                        callback -> callback.getToolDefinition().name(),
                        Function.identity()));
    }

    ToolCallback require(String name) {
        ToolCallback callback = tools.get(name);
        if (callback == null) {
            throw new IllegalStateException(
                    "Required gateway tool is unavailable: " + name);
        }
        return callback;
    }
}
```

Fail application startup or readiness when a required tool is absent or its
schema is incompatible. Do not silently switch to an ungoverned backend client.

For optional tools, expose capability state to the application and disable only
the affected feature.

## 4. Invoke a tool

`ToolCallback.call(...)` accepts a JSON object that conforms to the tool's
discovered input schema:

```java
String result = gatewayTools.require("search_candidates").call("""
        {
          "skill": "Java",
          "location": "Austin",
          "limit": 5
        }
        """);
```

Do not send fields such as `tenantId`, subject, scopes, or roles as tool
arguments. The gateway injects trusted identity context after validating the
model-visible input.

For business applications, put the MCP call behind a domain port:

```java
interface CandidateSearch {
    CandidatePage search(SearchCriteria criteria);
}
```

The MCP adapter owns the tool name, JSON serialization, response decoding, and
error translation. Domain services should not depend directly on Spring AI or
MCP types.

## 5. Decode results and errors

The MCP callback result contains text content whose payload is the gateway's
stable JSON envelope.

Success:

```json
{
  "ok": true,
  "data": []
}
```

Governed failure:

```json
{
  "ok": false,
  "error": "access_denied",
  "message": "caller is not authorized"
}
```

Decode the MCP text content first, then deserialize the gateway envelope.
Translate the `error` value to application exceptions or outcomes. Supported
categories include:

- `validation_error`;
- `access_denied`;
- `quota_exceeded`;
- `timeout`;
- `temporary_unavailable`;
- `connector_failure`.

Treat transport, protocol, and session exceptions separately from a valid
gateway failure envelope. Do not parse exception messages or downstream
response bodies to make business decisions.

When `retryAfterSeconds` is present, the caller may delay a read retry. Do not
automatically retry writes: a timeout after dispatch can leave the downstream
outcome unknown.

## 6. Handle approval elicitation

Approval-gated writes require an MCP client that can answer elicitation
requests. Enable annotation scanning and add a handler:

```java
import java.util.Map;

import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

@Component
final class ApprovalHandler {

    private final ApprovalService approvalService;

    ApprovalHandler(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @McpElicitation(clients = "gateway")
    ElicitResult handle(ElicitRequest request) {
        boolean approved = approvalService.requestApproval(request.message());
        if (approved) {
            return new ElicitResult(
                    ElicitResult.Action.ACCEPT,
                    Map.of("approved", true));
        }
        return new ElicitResult(ElicitResult.Action.DECLINE, Map.of());
    }
}
```

The handler must obtain a real user or workflow decision. Never approve a write
automatically based only on model output. If the client cannot provide
elicitation, approval-gated writes fail closed.

## 7. Authenticate the client

The repository's default `local` profile is intentionally unauthenticated and
uses a synthetic gateway identity. It is suitable only for local development.

In JWT mode, attach `Authorization: Bearer <token>` at the Streamable HTTP
transport boundary. With Spring AI's WebFlux transport, a client customizer can
supply an authenticated `WebClient.Builder`:

```java
import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class GatewayMcpSecurity {

    @Bean
    McpClientCustomizer<WebClientStreamableHttpTransport.Builder>
            gatewayBearerToken(
                    @Value("${gateway.mcp.url}") String gatewayUrl,
                    @Value("${gateway.mcp.access-token}") String accessToken) {
        return (connectionName, transport) -> {
            if ("gateway".equals(connectionName)) {
                transport.webClientBuilder(WebClient.builder()
                        .baseUrl(gatewayUrl)
                        .defaultHeader(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken));
            }
        };
    }
}
```

Reference the same URL from the MCP connection:

```yaml
gateway:
  mcp:
    url: ${GATEWAY_MCP_URL:http://127.0.0.1:8080}
    access-token: ${GATEWAY_MCP_ACCESS_TOKEN}

spring:
  ai:
    mcp:
      client:
        streamable-http:
          connections:
            gateway:
              url: ${gateway.mcp.url}
              endpoint: /mcp
```

The fixed-token example shows where authentication belongs. In production, use
an OAuth2 client or token provider that refreshes credentials before expiry;
never commit access tokens. The gateway derives tenant, subject, roles, and
scopes from the validated JWT.

## 8. Integrate with an AI model

An AI orchestration service can give its `ChatClient` the MCP-backed callback
provider:

```java
ChatClient chat = chatClientBuilder
        .defaultToolCallbacks(toolCallbackProvider)
        .build();
```

The model sees the schemas published by the gateway. Authorization and
trusted-context injection still occur at invocation time, so tool discovery
must not be treated as an authorization decision.

If the application connects to several MCP servers, filter the callbacks given
to each model or workflow. Avoid exposing unrelated domain tools merely because
they are discoverable.

## 9. Test the client

Test at three levels:

1. Unit-test the domain adapter with a mocked `ToolCallback` or
   `ToolCallbackProvider`.
2. Contract-test required tool names, input schemas, success decoding, and
   every stable error mapping.
3. Run against the packaged gateway to validate MCP sessions, identity,
   elicitation, and transport behavior.

Run the repository example:

```bash
docker compose up --build -d --wait
docker compose up -d --wait --force-recreate --no-deps gateway
docker compose --profile client run --rm --no-deps mcp-client
```

The gateway recreation resets the example's in-memory quota. See the
[MCP client integration](../../integrations/mcp-client/README.md) for the
expected
checks.

## Production checklist

- Use Streamable HTTP MCP and the `/mcp` endpoint.
- Authenticate the transport and refresh tokens before expiry.
- Keep tenant, subject, scope, and role values out of tool arguments.
- Verify required tools and schemas during startup or readiness.
- Put MCP calls behind domain-facing interfaces.
- Decode stable gateway errors instead of parsing message text.
- Set client timeouts above downstream gateway timeouts.
- Require a real approval decision for governed writes.
- Do not automatically retry writes with unknown outcomes.
- Test discovery, authorization, quota, approval, timeout, and unavailable-tool
  behavior.

See [application integration patterns](integration-patterns.md) for choosing
between a direct client, REST facade, AI orchestrator, or scheduled workflow.
