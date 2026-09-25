# Jev clasifica un currículum

| | |
|---|---|
| **Qué hace** | Carga un currículum y decide si es junior, intermedio o senior |
| **Stack** | Java 26 (`jenv shell 26`), Spring Boot 4.1, WebClient, LangChain4j 1.18.1 |
| **Decisión** | [Jev](https://openrouter.ai/docs/guides/community/jev) en OpenRouter (`typesafe/jev-1.13`) |
| **Texto** | Un modelo de chat en OpenRouter solo redacta la explicación |

Jev no genera texto. Recibe el currículum como `state` y una pregunta `choice`. Devuelve la etiqueta, la confianza y la probabilidad de cada opción. LangChain4j escribe dos frases en español **después** de que el nivel ya está fijado.

Sin `OPENROUTER_API_KEY` el lab usa una rúbrica local por años de experiencia (`offline-years`) para que `./gradlew check` y `bootRun` funcionen sin red.

## Quick start

```bash
cd jenv-langchaing4j
jenv shell 26
java -version          # debe decir 26
./gradlew check
./gradlew bootRun
```

Al arrancar imprime las tres muestras de `src/main/resources/resumes/`. Abre [http://localhost:8094](http://localhost:8094) para pegar un currículum, cargar un `.txt`, o usar las tres muestras. El API queda en el mismo puerto.

```bash
curl -s http://localhost:8094/api/v1/samples

curl -s -X POST http://localhost:8094/api/v1/seniority \
  -H 'Content-Type: application/json' \
  -d '{"resume":"4 years of experience. Delivers features independently."}'
```

También puedes pasar un archivo `.txt` dentro del directorio de trabajo:

```bash
curl -s -X POST http://localhost:8094/api/v1/seniority \
  -H 'Content-Type: application/json' \
  -d '{"path":"src/main/resources/resumes/senior.txt"}'
```

## Con OpenRouter

```bash
cp .env.example .env   # pon OPENROUTER_API_KEY
set -a && source .env && set +a
jenv shell 26
./gradlew bootRun
```

El campo `decider` pasa de `offline-years` a `jev`. Si la confianza queda bajo `0.70`, `needsHumanReview` es `true`.

La llamada real es `POST https://openrouter.ai/api/alpha/decisions`. `questions` es un **mapa** con el nombre de la pregunta, no un arreglo:

```json
{
  "model": "typesafe/jev-1.13",
  "state": { "resume": "..." },
  "questions": {
    "seniority": {
      "type": "choice",
      "instructions": "What is the seniority of this software-engineering resume?",
      "criteria": {
        "JUNIOR": "Under 3 years, internships, or guided work.",
        "INTERMEDIATE": "3 to 6 years, independent delivery.",
        "SENIOR": "7 or more years, architecture, or mentoring."
      }
    }
  }
}
```

La respuesta útil está en `answers.seniority.choice`, `confidence` y `probabilities`.

## Rúbrica offline

| Años en el texto | Nivel |
|---|---|
| menos de 3, o ninguno | `JUNIOR` |
| 3 a 6 | `INTERMEDIATE` |
| 7 o más | `SENIOR` |

Acepta `years` y `años`. Sirve para las pruebas. Con la API key, manda Jev.

## Job fit

El mismo cliente sirve para un fit contra una vacante: otra pregunta `choice` con `STRONG_MATCH`, `POSSIBLE_MATCH` y `WEAK_MATCH`, y el puesto dentro de `state`. Este lab se queda en la antigüedad del currículum.

## Documentation

| Guide | What it covers |
|---|---|
| [docs/README.md](docs/README.md) | Index |
| [docs/architecture.md](docs/architecture.md) | Decider, explainer, beans, startup demo |
| [docs/api.md](docs/api.md) | `POST /api/v1/seniority`, `GET /api/v1/samples`, errors |
| [docs/jev-decisions.md](docs/jev-decisions.md) | Decisions request and the fields the parser reads |
| [docs/rubric.md](docs/rubric.md) | Year count versus the Jev criteria |
| [docs/configuration.md](docs/configuration.md) | Port, key, models, thresholds |
| [docs/samples.md](docs/samples.md) | The three fixture resumes |
| [AGENTS.md](AGENTS.md) | Invariants for later edits |

## Verify

```bash
jenv shell 26
./gradlew check
```
