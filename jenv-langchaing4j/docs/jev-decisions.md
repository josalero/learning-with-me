# Jev Decisions call

`OpenRouterJevDecider` posts to `openrouter.base-url` + `openrouter.decisions-path`.

Default URL: `https://openrouter.ai/api/alpha/decisions`.

Header: `Authorization: Bearer <OPENROUTER_API_KEY>`. The key is not logged.

Timeouts: 5 seconds to connect, 20 seconds for the response. A non-2xx status becomes `JevClientException` with `OpenRouter decisions HTTP <status>`. The response body is discarded.

## Request

`questions` is an object keyed by question name. It is not an array of `{ "name", "type", "options" }`.

```json
{
  "model": "typesafe/jev-1.13",
  "state": {
    "resume": "<resume text>"
  },
  "questions": {
    "seniority": {
      "type": "choice",
      "instructions": "What is the seniority of this software-engineering resume? Use only the resume field.",
      "criteria": {
        "JUNIOR": "Under 3 years of professional experience, internships, bootcamps, or guided tasks.",
        "INTERMEDIATE": "3 to 6 years, delivers features with limited supervision.",
        "SENIOR": "7 or more years, or clear architecture, mentoring, or technical leadership."
      }
    }
  }
}
```

| Field | Source |
|---|---|
| `model` | `openrouter.jev-model`, default `typesafe/jev-1.13`. `~typesafe/jev-latest` also works if you set it |
| `state.resume` | The text from the HTTP body or the file |
| `questions.seniority.type` | Always `choice` |
| `questions.seniority.instructions` | `src/main/resources/jev/instructions.txt` |
| Criteria text | `src/main/resources/jev/criteria/JUNIOR.txt`, `INTERMEDIATE.txt`, and `SENIOR.txt` |
| Criteria keys | Must stay `JUNIOR`, `INTERMEDIATE`, `SENIOR`. The parser maps `choice` onto the `Seniority` enum |

The criteria strings are the rubric Jev sees. The offline decider does not read them; it counts years. If you change one, change the other only when you want both modes to mean the same thing. See [rubric.md](rubric.md).

## Response the parser reads

```json
{
  "answers": {
    "seniority": {
      "choice": "SENIOR",
      "confidence": 0.91,
      "probabilities": {
        "JUNIOR": 0.02,
        "INTERMEDIATE": 0.07,
        "SENIOR": 0.91
      },
      "type": "choice"
    }
  },
  "id": "gen-dec-...",
  "model": "typesafe/jev-1.13-20260917",
  "provider": "TypeSafe",
  "usage": {
    "cost": 0.00002,
    "input_tokens": 400,
    "output_tokens": 70
  }
}
```

| JSON path | Used as |
|---|---|
| `answers.seniority.choice` | `level`, uppercased. Anything else throws and the API returns `502` |
| `answers.seniority.confidence` | `confidence`. Missing becomes `0`, which marks human review |
| `answers.seniority.probabilities.<LABEL>` | `probabilities`. A missing label becomes `0` |
| `id`, `model`, `provider`, `usage` | Ignored |

`usage.cost` is the OpenRouter cost of that call in USD. This lab does not surface it. Output tokens for Jev are priced at zero on the model page; you still pay for input tokens. Check the current price on the Jev model page rather than hard-coding it.

## What Jev will not return

No rationale, no chain of thought, no rewritten resume. If you need sentences, that is `LangChain4jExplainer`, which calls the chat completions API with a prompt that includes the already chosen level and the resume. That second call can quote the resume. Jev's call only classifies it.

## Adding a job-fit question

The Decisions API accepts several questions in one `questions` object. A fit question would look like this and is **not** sent today:

```json
"fit": {
  "type": "choice",
  "instructions": "How well does this resume meet the job?",
  "criteria": {
    "STRONG_MATCH": "Meets the required skills and the experience bar.",
    "POSSIBLE_MATCH": "Partial overlap; a recruiter should look.",
    "WEAK_MATCH": "Missing the core skills or the experience bar."
  }
}
```

`state` would also need a `job` field. The parser currently reads only `answers.seniority`.

Other Jev primitives, unused here:

| Type | Returns |
|---|---|
| `choice` | one option, a probability per option, a confidence |
| `noul` | probability that a yes/no condition holds |
| `score` | a position on an ordered scale, plus per-level probabilities |
