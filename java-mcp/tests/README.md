# Repository tests

[`integration`](integration/) contains cross-module tests for architecture
boundaries, connector registration, and external connector compilation. The
logical Gradle project name remains `:integration-tests`.

Run all repository tests:

```bash
./gradlew test
```

Run only the cross-module suite:

```bash
./gradlew :integration-tests:test
```

For packaged end-to-end verification:

```bash
.agents/skills/mcp-gateway-qa-agent/scripts/verify.sh full
```
