# Integrations

This folder contains applications that consume the gateway. They demonstrate
two integration boundaries without bypassing gateway governance.

| Folder | Gradle project | Pattern |
|---|---|---|
| [`mcp-client`](mcp-client/README.md) | `:example-mcp-client` | Direct Streamable HTTP MCP client |
| [`rest-facade`](rest-facade/README.md) | `:example-orchestrator-api` | Domain REST API backed by MCP tools |

Start the default stack before running either integration:

```bash
docker compose up --build -d --wait
```

To build a client from scratch, follow
[Build an MCP client](../docs/guides/build-mcp-client.md). To choose an
integration boundary, read
[Application integration patterns](../docs/guides/integration-patterns.md).
