# Node seed conventions

Apply when the project profile is `users-service-node`.

- ESM, `node:test` for tests, Express (or equivalent) for HTTP.
- JSON `lowerCamelCase`.
- Unknown user: HTTP 404 with RFC 9457 problem details.
- Blank name on create: HTTP 400, no record stored.
- Do not log names or emails.
- Tests live next to the app and run via `npm test`.
