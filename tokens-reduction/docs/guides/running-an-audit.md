# Running an audit

For the full verification matrix, see [TESTING.md](../TESTING.md).

## Deterministic scan (default)

```bash
cd engine
jenv shell 25
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai'
```

Replace the path with any Java project root. Framework hint is optional but helps
routing for Spring AI sources.

Interpret IDs with [finding-catalog.md](../reference/finding-catalog.md).

## Saving reports to files

By default the scan only prints to the console. Add `--out <file>` (repeatable) to
write a report; the format is inferred from the extension (`.json`, `.md`, `.txt`)
or forced with `--format`:

```bash
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai \
    --out ../reports/audit.md --out ../reports/audit.json'
```

From the repo root, `make audit` (or `make scan PROJECT=…`) does this for you and
drops `token-audit-<name>.{md,json}` into the git-ignored `reports/` directory.

## Skill-assisted report

1. Run the CLI scan (above) when available.
2. Open [skills/token-efficiency-auditor/SKILL.md](../../skills/token-efficiency-auditor/SKILL.md).
3. Apply matching `rules/*.md` checklists.
4. Fill [templates/audit-report.md](../../skills/token-efficiency-auditor/templates/audit-report.md).

Sample filled report:
[examples/spring-ai-support-assistant/audit-report.md](../../examples/spring-ai-support-assistant/audit-report.md).

## Optional semantic review

See [semantic-review.md](semantic-review.md). Requires `OPENROUTER_API_KEY` and
`--llm-review`. AI findings are labeled `AI-INFERRED` and are not catalog IDs
until promoted.
