# Agent instructions — tokens-reduction (POC)

## Layout

| Path | Contents |
| --- | --- |
| [skills/token-efficiency-auditor](skills/token-efficiency-auditor/) | Canonical skill |
| [engine/](engine/) | `token-audit-core`, CLI, optional LLM review |
| [examples/spring-ai-support-assistant](examples/spring-ai-support-assistant/) | Fixture |
| [docs/TESTING.md](docs/TESTING.md) | How to verify |

## Audit workflow

1. Run CLI scan when possible (stable finding IDs).
2. Apply skill `rules/` for judgment.
3. Fill `skills/token-efficiency-auditor/templates/audit-report.md`.

```bash
cd engine && jenv shell 25
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai'
```

## Verify before claiming green

```bash
cd engine && jenv shell 25 && ./gradlew check
```

See [docs/TESTING.md](docs/TESTING.md).
