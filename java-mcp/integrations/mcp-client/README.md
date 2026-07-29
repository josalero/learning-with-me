# MCP client example

This one-shot Spring AI client connects to the gateway over Streamable HTTP,
discovers tools, calls them, handles approval elicitation, and exits.

To build the same integration in another application, follow
[Build an MCP client](../../docs/guides/build-mcp-client.md). This page focuses
on running and verifying the checked-in example.

It verifies:

- four bundled tools are discoverable;
- candidate output excludes sensitive field names;
- the configured search quota is enforced;
- approved writes execute and declined writes do not;
- outbound client-credentials authentication succeeds;
- the SQL tool returns projected inventory rows.

## Run

Run from the repository root. Start the default stack:

```bash
docker compose up --build -d --wait
docker compose up -d --wait --force-recreate --no-deps gateway
```

Run the client:

```bash
docker compose --profile client run --rm --no-deps mcp-client
```

The output uses PII as an abbreviation for personally identifiable
information. Look for:

```text
CHECK A — discovered 4 tool(s)
CHECK I3 — search_candidates output has no PII field names
CHECK I4 — quota exceeded after 3 successful search calls
CHECK I5 — write approved and executed
CHECK I5 — write declined without downstream mutation
CHECK I5 — outbound bearer protected call succeeded
CHECK I3 — SQL tool returned projected inventory rows
```

The gateway recreation resets the in-memory demo quota, making the client
results repeatable. Recreate it again before another run:

```bash
docker compose up -d --wait --force-recreate --no-deps gateway
```

## Run from Gradle

With the gateway already available at `http://127.0.0.1:8080`:

```bash
./gradlew :example-mcp-client:bootRun
```

This example uses a synthetic local identity. For inbound JWT testing, follow
the [JWT authentication example](../../docs/HOW-TO-USE.md#test-jwt-authentication).
