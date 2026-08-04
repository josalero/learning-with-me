# Examples

Executable fixtures used to demonstrate and regression-test the auditor.

| Example | Framework shape | Purpose |
| --- | --- | --- |
| [spring-ai-support-assistant](spring-ai-support-assistant/) | Real Spring AI 2.0.0 (`ChatClient`, `@Tool`, `VectorStore`) | Canonical demo: five intentional token-waste patterns in a realistic support service |

Scan the canonical example:

```bash
cd engine
jenv shell 25
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai'
```

Expected finding IDs: [docs/reference/finding-catalog.md](../docs/reference/finding-catalog.md).  
How to test: [docs/TESTING.md](../docs/TESTING.md).
