# Claude Code — tokens-reduction (POC)

Skill: [.claude/skills/token-efficiency-auditor](.claude/skills/token-efficiency-auditor/)
→ [skills/token-efficiency-auditor](skills/token-efficiency-auditor/).

```bash
./scripts/link-skill-targets.sh   # repair symlink if needed

cd engine && jenv shell 25
./gradlew check
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai'
```

Testing: [docs/TESTING.md](docs/TESTING.md).  
Findings: [docs/reference/finding-catalog.md](docs/reference/finding-catalog.md).
