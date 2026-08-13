import assert from "node:assert/strict";
import test from "node:test";
import { createApp, resetUsers } from "../src/app.js";

test("createThenGet_whenNameIsPresent_returnsTheUser", async () => {
  resetUsers();
  const app = createApp();
  const server = app.listen(0);
  const { port } = server.address();
  try {
    const created = await fetch(`http://127.0.0.1:${port}/api/users`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: "Ada", email: "ada@example.com" }),
    });
    assert.equal(created.status, 200);
    const body = await created.json();
    assert.equal(body.name, "Ada");
    const fetched = await fetch(`http://127.0.0.1:${port}/api/users/${body.id}`);
    assert.equal(fetched.status, 200);
    const user = await fetched.json();
    assert.equal(user.name, "Ada");
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
