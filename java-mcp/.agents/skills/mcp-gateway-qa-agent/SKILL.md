---
name: mcp-gateway-qa-agent
description: Validate this Java MCP integration gateway using evidence from Gradle tests, connector contracts, Spring catalog binding, Docker Compose, actuator endpoints, and the MCP example client. Use when asked for QA, regression testing, release readiness, connector validation, integration sign-off, or an evidence-backed PASS/FAIL report for java-mcp.
---

# MCP Gateway QA

Validate the current repository architecture and runtime behavior. Prefer executed
evidence over conclusions based only on source inspection.

## Choose the scope

- Use **fast** for ordinary code changes: run the complete Gradle test suite.
- Use **connector** when changing the SPI, OpenAPI, SQL, testkit, or an external
  connector: run the affected module tests plus `:integration-tests:test`.
- Use **full** for releases, catalog/configuration changes, Docker changes,
  authentication, approvals, quotas, output governance, or explicit sign-off.
- Use `scripts/verify.sh fast` or `scripts/verify.sh full` for the standard
  deterministic workflow. Read the script before changing its checks.

## Read current sources

Read only the files relevant to the requested scope:

1. `README.md`
2. `docs/architecture.md`
3. `docs/HOW-TO-USE.md`
4. `docs/reference/catalog.md` for catalog or injection changes
5. `docs/guides/build-connector.md` for connector changes
6. `docker/README.md` for packaged-stack checks
7. `docs/known-limitations.md` when judging production readiness

Treat only the current documentation map as authoritative. If obsolete plans or
superseded validation instructions reappear, flag them as documentation drift.

## Run the workflow

1. Inspect repository status and preserve unrelated user changes.
2. Confirm Java 26 is available and use the checked-in Gradle wrapper.
3. Run the selected automated scope:

   ```bash
   ./gradlew test
   ./gradlew test publishToMavenLocal
   ./gradlew :connector-openapi:test :connector-sql:test :integration-tests:test
   ```

4. For full validation, validate and start the packaged stack:

   ```bash
   docker compose config
   docker compose up --build --force-recreate -d
   docker compose ps
   curl -fsS http://127.0.0.1:8080/actuator/health
   curl -fsS http://127.0.0.1:8080/actuator/gatewaycatalog
   docker compose --profile client run --rm --no-deps mcp-client
   ```

5. Verify runtime evidence for:

   - all configured catalog tools being discovered;
   - successful generic OpenAPI reads;
   - governed output with no forbidden PII fields;
   - quota enforcement after the configured successful-call allowance;
   - approved writes executing and declined writes not mutating downstream state;
   - outbound bearer-protected calls succeeding;
   - SQL tools returning projected rows;
   - health and catalog actuator endpoints returning successful responses.

6. Inspect focused gateway logs when a check fails. Do not print secrets, bearer
   tokens, raw PII payloads, or environment-file contents.

## Connector contract checks

For a connector change, verify all of the following:

- The implementation depends on `connector-spi`, not gateway internals.
- Spring Boot auto-configuration is registered in
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- `OperationDescriptor.access()` correctly identifies write-capable operations.
- Connector configuration is validated through `Connector.configure`.
- Input schemas use the vocabulary enforced by the recursive argument validator.
- Runtime failures are normalized without leaking internal exception details.
- The connector passes `connector-testkit` assertions where applicable.
- A connector JAR can be compiled or loaded without a gateway compile-time
  dependency.

## Verdict

Report one of:

- **PASS**: every in-scope command and behavior check succeeded.
- **FAIL**: execution completed but at least one expected behavior was wrong.
- **BLOCKED**: required tooling or a dependency prevented execution.

Include commands run, concise evidence, failures or blockers, and any remaining
production boundary from `docs/known-limitations.md`. Write or update a QA
document only when the user asks for a persisted report. Do not commit, publish
remotely, or tear down a user-managed stack unless explicitly requested.
