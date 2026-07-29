# Known limitations

Review these constraints before using the reference distribution outside local
development.

| Area | Operational impact | Production action |
|---|---|---|
| Quotas | Bucket4j state is per JVM and resets on restart, so replicas do not share usage. | Add a shared quota store before horizontal scaling. |
| Approvals | Approval-gated writes fail closed when the MCP client cannot perform elicitation. | Verify client elicitation support or keep the operation unavailable to that client. |
| Local identity | The default profile uses a synthetic identity. | Never expose the local profile publicly; enable authenticated inbound identity. |
| JWT validation | The demo profile uses an HS256 shared secret. | Use issuer and JWKS validation, key rotation, and organization-specific claim mapping. |
| Outbound credentials | Compose examples contain local-only credentials. | Resolve credentials from an approved secret manager and rotate them independently. |
| Audit argument hashes | A default development salt can make hashes predictable across installations. | Set a private, installation-specific `gateway.observability.argument-hash-salt`. |
| Catalog lifecycle | Catalogs compile only at startup. | Promote signed, reviewed catalog files and restart the gateway in a controlled rollout. |
| OpenAPI requests | Generic execution supports path, query, header, and cookie parameters plus JSON bodies. Multipart, form, and streaming bodies are unsupported. | Build a connector for unsupported request or response semantics. |
| JSON Schema | Validation covers the vocabulary emitted by bundled connectors, not advanced composition or custom formats. | Keep tool schemas within the supported vocabulary or extend validation with tests. |
| SQL | Only named, parameterized `SELECT` operations are accepted. | Retain database roles as the primary mutation control and use a dedicated connector for other behavior. |
| Audit persistence | The bundled application writes audit records synchronously through JDBC. | Replace it with an asynchronous audit adapter if latency or audit-system availability requires isolation. |
| Resilience | The gateway applies timeouts and stable failure mapping but no automatic retries or circuit breakers. | Add connector-specific policies only after operation idempotency is modeled. |
| Write timeouts | A timeout after dispatch can leave the downstream outcome unknown. | Use backend idempotency keys or reconciliation; do not automatically retry writes. |
| Tool discovery | JWT mode publishes the compiled catalog, then enforces caller authorization during invocation. | Do not treat discovery as an authorization boundary; enforce least privilege at invocation. |

These limitations describe the checked-in reference distribution. A production
distribution may replace adapters without changing the connector SPI or
governance pipeline.
