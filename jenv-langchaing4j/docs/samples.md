# Sample resumes

Classpath files under `src/main/resources/resumes/`. `SampleResumes.all()` loads them in this order. `DemoRunner` and `GET /api/v1/samples` use that list.

The names are fictional. The files contain no email, phone, or address.

## junior.txt

Alex Rivera, junior software developer. The summary says `1 year of professional experience` after a bootcamp. The body is an internship at Harborline Retail (guided API work, reviewed pull requests) plus a bootcamp capstone. No other `N years` phrase.

Offline result: `JUNIOR`.

## intermediate.txt

Jordan Lee, software engineer. The summary says `4 years of experience`. Current role owns the appointment-reminder service at Brightpath Health; the previous role shipped checkout work at Lumen Cart. Dates describe tenure so they do not add a higher year count.

Offline result: `INTERMEDIATE`.

## senior.txt

Sam Patel, staff software engineer. The summary says `10 years of experience`. Fieldnote Systems covers architecture, design reviews, and mentoring; earlier roles cover an event-driven migration and Java APIs. No phrase above 10 years.

Offline result: `SENIOR`.

## no-years.txt

Riley Chen. Projects and dates, and no `N years` or `N años` phrase.

Offline result: `JUNIOR`. Explanation uses `sin años explícitos`.

## three-years.txt

Morgan Ellis. The summary says `3 years of experience`, the lower bound of intermediate.

Offline result: `INTERMEDIATE`. Explanation cites `3 años`.

## seven-years.txt

Avery Shah. The summary says `7 years of experience`, the lower bound of senior, plus design reviews and mentoring.

Offline result: `SENIOR`. Explanation cites `7 años`.

## mixed-years.txt

Quinn Brooks. The summary says `9 years of experience` and an early role says `2 years`. The counter keeps 9.

Offline result: `SENIOR`. Explanation cites `9 años`.

## cinco-anos.txt

Camila Soto. The summary says `5 años de experiencia` and does not use the English word "years".

Offline result: `INTERMEDIATE`. Explanation cites `5 años`.

## What a sample can change

Offline, every file shares the same confidence, probability shape, review flag, and decider. The resume text changes the level and the explanation.

| Field | Offline value | What changes it |
|---|---|---|
| `level` | from the highest year phrase | the sample file |
| `confidence` | `0.99` | a live Jev response, not the file |
| `probabilities` | `0.99` on the chosen level, `0.005` on the other two | which level was chosen; live Jev can return another distribution |
| `needsHumanReview` | `false` | live confidence strictly below `app.human-review-below` (default `0.70`) |
| `explanation` | Spanish year sentence, including `sin años explícitos` | the year phrase in the file; a live chat model may replace the sentence |
| `decider` | `offline-years` | a non-blank `OPENROUTER_API_KEY` switches every sample to `jev` |

## Adding a fixture

1. Add a UTF-8 `.txt` file under `src/main/resources/resumes/`.
2. Include an explicit `N years` or `N años` phrase if the offline tests should pin a level.
3. Register it in `SampleResumes.all()` if the demo and `GET /api/v1/samples` should classify it.
4. Extend `YearsSeniorityDeciderTest` or `SeniorityControllerTest` when the new file changes an expected level.

A one-off file does not need a classpath entry. Put it in the project directory and post `{"path":"relative/file.txt"}`. The path cannot leave the working directory, and the file cannot exceed 64KB.

## What the startup log shows

With `app.demo=true` and no API key:

```text
junior.txt -> JUNIOR confidence=0.99 review=false decider=offline-years
intermediate.txt -> INTERMEDIATE confidence=0.99 review=false decider=offline-years
senior.txt -> SENIOR confidence=0.99 review=false decider=offline-years
no-years.txt -> JUNIOR confidence=0.99 review=false decider=offline-years
three-years.txt -> INTERMEDIATE confidence=0.99 review=false decider=offline-years
seven-years.txt -> SENIOR confidence=0.99 review=false decider=offline-years
mixed-years.txt -> SENIOR confidence=0.99 review=false decider=offline-years
cinco-anos.txt -> INTERMEDIATE confidence=0.99 review=false decider=offline-years
```

The resume body is not logged.
