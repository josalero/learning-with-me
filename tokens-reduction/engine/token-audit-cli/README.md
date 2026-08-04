# token-audit-cli

Picocli entry point. POC ships one command: `scan`.

```bash
cd engine
jenv shell 25
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai'
```

Write report files (console output is unchanged). Format is inferred from the
extension (`.json`, `.md`, `.txt`); `--out` is repeatable and `--format` forces one:

```bash
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai \
    --out ../reports/audit.md --out ../reports/audit.json'
```

Optional semantic pass:

```bash
OPENROUTER_API_KEY='your-key' ./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai --llm-review'
```

Depends on `token-audit-core`, `token-audit-llm-reviewer`, `token-audit-openrouter`.
See [docs/TESTING.md](../../docs/TESTING.md).
