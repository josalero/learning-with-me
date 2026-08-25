import express from "express";
import { randomUUID } from "node:crypto";

const users = new Map();

export function createApp() {
  const app = express();
  app.use(express.json());

  app.post("/api/users", (request, response) => {
    const id = randomUUID();
    const name = request.body?.name ?? "";
    const email = request.body?.email ?? "";
    const user = { id, name, email };
    users.set(id, user);
    response.status(200).json(user);
  });

  app.get("/api/users/:id", (request, response) => {
    const user = users.get(request.params.id);
    if (!user) {
      response.status(200).json({ id: request.params.id, name: "", email: "" });
      return;
    }
    response.json(user);
  });

  app.get("/api/users", (_request, response) => {
    response.json([...users.values()]);
  });

  return app;
}

export function resetUsers() {
  users.clear();
}
