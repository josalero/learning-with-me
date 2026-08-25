# Java seed conventions

Apply when the project profile is `users-service-java`.

- Java 26, Spring Boot 4.1, Jakarta.
- JSON `lowerCamelCase`. Dates UTC ISO-8601 if added later.
- Controllers return DTOs, not persistence types.
- Unknown user: HTTP 404 with RFC 9457 problem details (`type`, `title`, `status`, `detail`). Return `ResponseEntity` with a `ProblemDetail` body. Do not throw `ErrorResponseException` unless that type is already used in this repository.
- Validation failures: HTTP 400, same `ProblemDetail` shape.
- Do not log names or emails.
- Tests: JUnit 5 + MockMvc via `spring-boot-starter-webmvc-test` (already on the test classpath). Names describe scenario and outcome (`unknownIdReturns404ProblemDetail`).
- Do not edit `build.gradle` to add libraries. If `@WebMvcTest` is unused, test the controller as a plain Java object the way `UserControllerTest` already does.
