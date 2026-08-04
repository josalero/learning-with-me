# Token Audit Engine (POC)

Java 25 Gradle modules that power the offline `scan` CLI and optional semantic review.

## Modules

| Module | Role |
| --- | --- |
| `token-audit-core` | API, SPI, AST/regex analyzers, jtokkit token counts |
| `token-audit-cli` | `token-audit scan` |
| `token-audit-llm-reviewer` | Bounded evidence + AI merge |
| `token-audit-openrouter` | OpenRouter adapter |
| `spring-ai-support-assistant` | Example fixture (`../examples/…`) |

## Build / test

```bash
jenv shell 25
./gradlew check
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai'
```

Full matrix: [docs/TESTING.md](../docs/TESTING.md).  
Finding IDs: [docs/reference/finding-catalog.md](../docs/reference/finding-catalog.md).
