# Integrate applications with the gateway

Choose the narrowest integration that fits the application. In every pattern,
the gateway remains the enforcement point for identity, authorization, quotas,
approvals, output policy, and audit.

| Application | Recommended pattern | Why |
|---|---|---|
| IDE, desktop assistant, or agent runtime | Direct MCP client | The application already understands MCP tools and elicitation. |
| Frontend or existing business service | Domain REST facade | The application receives a stable domain API and does not need MCP knowledge. |
| Chat or reasoning service | AI orchestrator | The model receives only the tools compiled and governed by the gateway. |
| Scheduled job or workflow engine | Named MCP tool call | Automation uses the same controls and audit trail as interactive calls. |
| Multiple domains with separate operators | Multiple gateways | Catalogs, credentials, and failure domains remain independently governed. |

## Use a direct MCP client

```text
MCP-capable application → gateway /mcp → governed connectors
```

Use Streamable HTTP MCP and pass caller identity at the transport boundary.
Follow [Build an MCP client](build-mcp-client.md) for dependencies,
configuration, discovery, invocation, authentication, and testing. The
[MCP client integration](../../integrations/mcp-client/README.md) is the
runnable
implementation.

## Add a domain REST facade

```text
frontend → domain REST API → MCP client → gateway
```

Expose endpoints and response types that match the application domain. Keep MCP
tool names and schemas inside the adapter so clients are not coupled to the
gateway protocol.

The [REST facade integration](../../integrations/rest-facade/README.md)
implements typed endpoints and delegates to named tools through
`McpToolGateway`.

Application code should depend on a domain-facing port:

```java
interface CandidateSearch {
    CandidatePage search(SearchCriteria criteria);
}
```

The MCP adapter maps the port to `search_candidates` and translates stable
gateway errors into domain exceptions. This makes tool-version changes
explicit and independently testable.

## Build an AI orchestrator

Give the model the gateway-provided `ToolCallbackProvider`:

```java
ChatClient chat = chatClientBuilder
        .defaultToolCallbacks(gatewayToolCallbackProvider)
        .build();
```

Do not let prompts or model output set trusted tenant, subject, scope, or role
values. Resolve identity from the authenticated MCP transport and treat model
arguments as untrusted tool input.

## Run scheduled automation

Call a named MCP tool through the same client adapter used by interactive
applications. Use a service identity with only the scopes and roles required by
the job. Authorization, quotas, output policy, and audit remain active.

Avoid a second direct backend client for the same operation. That creates an
ungoverned path with different security and audit behavior.

## Separate domain gateways

Use separate gateway distributions or catalogs when domains have different:

- operators or release schedules;
- backend credentials;
- data classifications;
- approval requirements;
- availability or failure-isolation requirements.

One application may connect to several MCP gateways. Avoid turning one
unrestricted catalog into an enterprise-wide trust boundary.

## Apply these rules to every pattern

- Authenticate at the MCP transport boundary.
- Pass correlation IDs end to end.
- Set client timeouts longer than the gateway's downstream timeout.
- Handle stable error categories instead of parsing exception text.
- Treat write timeouts as potentially having an unknown downstream outcome.
- Pin tool names and schemas through application tests.
- Keep approval-capable writes on clients that support MCP elicitation.
- Give service identities least-privilege scopes and roles.

See [How to use the gateway](../HOW-TO-USE.md) for runnable commands and the
[catalog reference](../reference/catalog.md) for policy configuration.
