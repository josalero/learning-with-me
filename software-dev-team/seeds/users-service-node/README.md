# users-service-node seed

Copy into `workspace/users-service-node` with `./scripts/seed-workspace.sh users-service-node` or the dashboard Seed action.

Happy-path tests pass. Deliberate gaps the demo feature fills:

- unknown user id does **not** return an RFC 9457 404 (returns an empty placeholder instead)
- create accepts a blank name

```bash
npm install
npm test
```
