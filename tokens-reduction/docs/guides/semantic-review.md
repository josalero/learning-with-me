# Optional semantic review with OpenRouter

The default `token-audit scan` is deterministic, offline, and makes no network calls.
Semantic review is an explicit second pass that sends a bounded, redacted evidence
bundle to an external model and merges the response as `AI-INFERRED` findings.

## Data flow

```text
Local deterministic scan
        |
        v
Relevant source selection
        |
        v
Secret redaction + hard character limits
        |
        v
OpenRouter chat completion (strict JSON Schema)
        |
        v
Schema, area, severity, and evidence-location validation
        |
        v
AI-INFERRED findings + provider token usage
```

An external request happens only when `--llm-review` is present.

## Run it

Set the API key in the environment; there is deliberately no API-key command-line
option because shell arguments are often retained in history and process listings.

```bash
cd engine
OPENROUTER_API_KEY='your-key' \
OPENROUTER_MODEL='openrouter/auto' \
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai --llm-review'
```

`OPENROUTER_MODEL` is optional. Resolution order is:

1. `--llm-model`
2. `OPENROUTER_MODEL`
3. `openrouter/auto`

Use a concrete model slug when repeatable evaluation matters. Router and `latest`
aliases can resolve to different concrete models over time.

## Evidence limits

Default limits:

| Option | Default | Meaning |
| --- | ---: | --- |
| `--llm-max-files` | 20 | Maximum eligible files sent |
| `--llm-max-file-chars` | 8,000 | Maximum characters from one file |
| `--llm-max-evidence-chars` | 40,000 | Hard total source-evidence limit |
| `--llm-max-findings` | 8 | Maximum AI findings accepted |

The collector considers Java, Kotlin, properties, YAML, JSON, text, and common prompt
template files. It prioritizes content containing AI-framework, prompt, tool, RAG,
memory, token, and agent terms. It excludes build output, hidden directories,
`node_modules`, and `src/test`.

Every selected excerpt is line-numbered. A model finding is rejected if its location
does not point to a file included in the evidence bundle.

## Security and privacy

Before sending evidence, the collector replaces common labeled credentials, bearer
tokens, OpenRouter/OpenAI-style keys, passwords, and access-token shapes. This is a
best-effort safety layer, not a general secret-scanning guarantee. Review organizational
policy before sending proprietary source code to any external provider.

OpenRouter provider routing uses:

```json
{
  "require_parameters": true,
  "data_collection": "deny"
}
```

This restricts routing to providers that support the requested structured-output
parameters and, by default, providers that do not collect prompts. It does not replace
OpenRouter account privacy settings or govern OpenRouter's own retention. Passing
`--llm-allow-provider-data-collection` changes the provider preference to `allow`.

The application never logs the API key or includes it in the evidence prompt.

## Output and trust boundary

Deterministic findings retain their normal output. Semantic findings are printed as:

```text
[MEDIUM] [AI-INFERRED] AI-DUPLICATED-POLICY-CONTEXT (...) @ Demo.java:42
```

The summary also reports:

- Concrete model returned by OpenRouter.
- Evidence files included.
- Redaction count and truncation status.
- Provider-reported prompt and completion tokens.

AI findings are suggestions requiring human review and behavioral regression tests.
They are not promoted to deterministic rules until a repeatable source shape and
positive/negative fixtures exist.

## Architecture

- `token-audit-llm-reviewer` owns provider-neutral contracts, evidence selection,
  redaction, structured parsing, and finding provenance.
- `token-audit-openrouter` owns only HTTP protocol mapping and OpenRouter configuration.
- `token-audit-cli` composes those modules behind the explicit opt-in flag.
- `token-audit-core` remains usable without LLM or HTTP dependencies.

Additional providers implement `LlmReviewClient`; they do not change the review service
or core finding model.

## Official OpenRouter references

- [API quickstart](https://openrouter.ai/docs/quickstart)
- [Structured outputs](https://openrouter.ai/docs/guides/features/structured-outputs)
- [Provider routing and data policy](https://openrouter.ai/docs/guides/routing/provider-selection)
- [Models and supported parameters](https://openrouter.ai/docs/guides/overview/models)
