# Seniority rubric

The live criteria and the offline counter are written to agree. They are not the same code path. Jev reads `src/main/resources/jev/criteria/*.txt`. The offline counter does not.

## Levels

| Level | Meaning used in the Jev criteria |
|---|---|
| `JUNIOR` | Under 3 years of professional experience, internships, bootcamps, or guided tasks |
| `INTERMEDIATE` | 3 to 6 years, delivers features with limited supervision |
| `SENIOR` | 7 or more years, or clear architecture, mentoring, or technical leadership |

Jev may weigh title and scope, not only the year count, because it reads the whole `state.resume` string. The offline decider does not. A resume that says "Staff engineer" and never says "N years" is junior offline and may be senior on Jev.

## Offline year count

`YearsSeniorityDecider` scans for the highest match of:

```text
(\d{1,2})\s*\+?\s*(?:years?|años?)
```

The match is case-insensitive. `10+ years` and `8 años` count. `Java 21` does not, because the number is not followed by year/año.

| Highest match | Level | Confidence | Probabilities |
|---|---|---|---|
| none, or 0–2 | `JUNIOR` | `0.99` | chosen `0.99`, others `0.005` |
| 3–6 | `INTERMEDIATE` | `0.99` | same shape |
| 7–99 | `SENIOR` | `0.99` | same shape |

`10 years` and `2 years` in the same text use `10`. Offline confidence is a constant, so `needsHumanReview` stays false at the default threshold of `0.70`.

The template explanation is Spanish and cites that count:

| Level | Sentence shape |
|---|---|
| `JUNIOR` | `Perfil junior (<n> año/años): menos de 3 años de experiencia profesional.` |
| `INTERMEDIATE` | `Perfil intermedio (<n> años): entre 3 y 6 años, entrega con poca supervisión.` |
| `SENIOR` | `Perfil senior (<n> años): 7 años o más, con diseño o mentoría.` |

If no year phrase matched, the parenthetical is `sin años explícitos`.

## Chat explanation

When the key is set and `openrouter.explain=true`, the prompt is:

```text
The seniority decision is already made: <LEVEL> (confidence <0.00>).
Do not change the level and do not invent employers, titles, or years.
Write two sentences in Spanish that cite only evidence present in the resume.

Resume:
<text>
```

Temperature is `0.2`. Max tokens is `200`. Requests and responses are not logged. A blank completion or any runtime failure uses the template sentence instead.

## Human review

`ResumeSeniorityService` sets `needsHumanReview` when `confidence < app.human-review-below`. The comparison is strict: confidence `0.70` with threshold `0.70` does not require review.

Use the flag as a routing hint (show the resume to a person). The lab does not block the response or call another model when it is true.

## Fixtures

| File | Years phrase | Expected offline level |
|---|---|---|
| `src/main/resources/resumes/junior.txt` | `1 year` | `JUNIOR` |
| `src/main/resources/resumes/intermediate.txt` | `4 years` | `INTERMEDIATE` |
| `src/main/resources/resumes/senior.txt` | `10 years` | `SENIOR` |
| `src/main/resources/resumes/no-years.txt` | none | `JUNIOR` |
| `src/main/resources/resumes/three-years.txt` | `3 years` | `INTERMEDIATE` |
| `src/main/resources/resumes/seven-years.txt` | `7 years` | `SENIOR` |
| `src/main/resources/resumes/mixed-years.txt` | `2 years` and `9 years` | `SENIOR` |
| `src/main/resources/resumes/cinco-anos.txt` | `5 años` | `INTERMEDIATE` |

The people named in those files are fictional. See [samples.md](samples.md).
