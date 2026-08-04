# token-audit-core

Static audit API, analyzer SPI, and built-in Java source heuristics.

Finding IDs: [docs/reference/finding-catalog.md](../../docs/reference/finding-catalog.md).

```bash
cd engine && jenv shell 25
./gradlew :token-audit-core:test
```

`SpringAiSupportAssistantScanTest` asserts the five catalog IDs against
`examples/spring-ai-support-assistant`.
