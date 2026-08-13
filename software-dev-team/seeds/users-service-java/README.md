# users-service-java seed

Copy into `workspace/users-service-java` with `./scripts/seed-workspace.sh users-service-java` or the dashboard Seed action.

Happy-path tests pass. Deliberate gaps the demo feature fills:

- unknown user id does **not** return an RFC 9457 404 (returns an empty placeholder instead)
- create accepts a blank name

```bash
jenv shell 26
./gradlew test
```
