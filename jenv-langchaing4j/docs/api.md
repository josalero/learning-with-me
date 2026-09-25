# HTTP API

Base URL when you run `./gradlew bootRun`: `http://localhost:8094`.

There is no auth. Do not expose this port beyond your machine. Resume text is personal data; the API echoes an explanation that may quote it, and error bodies do not.

## POST /api/v1/seniority

Classifies one resume.

Send **either** `resume` or `path`. Sending both, or neither, is `400` with message `Send either resume text or a path, not both`.

### Pasted text

```bash
curl -s -X POST http://localhost:8094/api/v1/seniority \
  -H 'Content-Type: application/json' \
  -d '{"resume":"4 years of experience. Delivers features independently."}'
```

```json
{
  "level": "INTERMEDIATE",
  "confidence": 0.99,
  "probabilities": {
    "JUNIOR": 0.005,
    "INTERMEDIATE": 0.99,
    "SENIOR": 0.005
  },
  "needsHumanReview": false,
  "explanation": "Perfil intermedio (4 años): entre 3 y 6 años, entrega con poca supervisión.",
  "decider": "offline-years"
}
```

With a live key, `decider` is `jev`, `confidence` and `probabilities` come from OpenRouter, and `explanation` is the chat model's two Spanish sentences. The numbers above are the offline rubric, which always reports confidence `0.99`.

### File inside the working directory

`bootRun` uses the project directory as the working directory. The path is resolved against that directory and must stay inside it.

```bash
curl -s -X POST http://localhost:8094/api/v1/seniority \
  -H 'Content-Type: application/json' \
  -d '{"path":"src/main/resources/resumes/senior.txt"}'
```

| Check | Result |
|---|---|
| Path escapes the working directory (`../`) | `400` `Resume path must stay inside the working directory` |
| Symlink inside the directory whose target is outside it | `400` `Resume path must stay inside the working directory` |
| Path Java cannot parse (for example a NUL character) | `400` `Resume path is invalid` |
| File missing | `400` `Resume file not found` |
| Not a regular file | `400` `Resume file not found` |
| File larger than 64KB | `400` `Resume file is larger than 64KB` |
| Pasted text larger than 64KB (UTF-8 bytes) | `400` `Resume text is larger than 64KB` |
| Empty or whitespace file | `400` `Resume file is empty` |
| Unreadable | `400` `Resume file could not be read` |
| Blank `resume` string | `400` `Resume text is empty` |
| Body is missing or not JSON | `400` `Request body must be JSON with resume or path` |

The file must be UTF-8 text. PDF bytes are not parsed.

## Lab page

`GET /` serves `src/main/resources/static/index.html`. Paste text, load a `.txt` in the browser, or fill the box from `GET /api/v1/sample-resumes`, then classify with `POST /api/v1/seniority`. Loading a sample does not call Jev.

## GET /api/v1/sample-resumes

Fixture text only. No classification.

```bash
curl -s http://localhost:8094/api/v1/sample-resumes
```

```json
[{ "name": "junior.txt", "text": "Alex Rivera\n..." }]
```

Order: `junior.txt`, `intermediate.txt`, `senior.txt`, `no-years.txt`, `three-years.txt`, `seven-years.txt`, `mixed-years.txt`, `cinco-anos.txt`.

## GET /api/v1/samples

Classifies every classpath fixture in that same order. With a live key this calls Jev once per file.

```bash
curl -s http://localhost:8094/api/v1/samples
```

```json
[
  {
    "name": "junior.txt",
    "assessment": { "level": "JUNIOR", "decider": "offline-years" }
  }
]
```

Each `assessment` has the same fields as `POST /api/v1/seniority`. With a live key this endpoint calls Jev once per file and the chat model once per file.

## Errors

| Status | When | Body |
|---|---|---|
| `400` | Bad or empty input, illegal path, or a body that is not JSON. Only `InvalidResumeException` and an unreadable body map to 400 | `{ "message": "<reason>" }` |
| `502` | OpenRouter HTTP error, connection failure, timeout, empty body, invalid JSON, a choice outside `JUNIOR` / `INTERMEDIATE` / `SENIOR`, or a confidence outside 0 to 1 | `{ "message": "Seniority decision failed" }` |

`502` does not include the upstream body or the resume. The log line for a chat-model failure records the exception class name only.

A chat-model failure after a successful Jev call is still `200`. The label stands; the explanation falls back to the year template.

## Fields

| Field | Type | Meaning |
|---|---|---|
| `level` | `JUNIOR`, `INTERMEDIATE`, `SENIOR` | The choice |
| `confidence` | number from 0 to 1 | Jev's confidence, or `0.99` offline |
| `probabilities` | object with the three labels | Missing upstream keys are stored as `0` |
| `needsHumanReview` | boolean | `true` when confidence is strictly below `app.human-review-below` |
| `explanation` | string | Spanish. Does not override `level` |
| `decider` | `jev` or `offline-years` | Which decider ran |

Offline probabilities put `0.99` on the chosen label and `0.005` on each of the other two.
