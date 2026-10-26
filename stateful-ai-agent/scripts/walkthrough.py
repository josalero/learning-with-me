#!/usr/bin/env python3
"""Walk the seeded APP-1 hiring scenario against a running agent.

Expects the Compose stack (or bootRun) on http://localhost:8080 and the
passwords from the environment, defaulting to the local .env.example values.
A fresh database is required: APP-1 starts at RECRUITER_SCREEN.
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("AGENT_BASE_URL", "http://localhost:8080")
ALICE = os.environ.get("AGENT_ALICE_PASSWORD", "change-me-alice")
BOB = os.environ.get("AGENT_BOB_PASSWORD", "change-me-bob")


def request(method, path, user, password, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    token = __import__("base64").b64encode(f"{user}:{password}".encode()).decode()
    req.add_header("Authorization", f"Basic {token}")
    req.add_header("Content-Type", "application/json")
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        payload = json.loads(raw) if raw else {}
        return error.code, payload


def wait_until_healthy():
    for _ in range(60):
        try:
            with urllib.request.urlopen(BASE + "/actuator/health") as response:
                if response.status == 200:
                    return
        except Exception:
            time.sleep(2)
    sys.exit("Agent health check did not succeed")


def session(user, password):
    status, body = request("POST", "/api/v1/sessions", user, password, {"provider": "STUB"})
    if status != 201:
        sys.exit(f"Could not open a session: {status} {body}")
    return body["id"]


def say(user, password, session_id, text):
    status, body = request("POST", f"/api/v1/sessions/{session_id}/messages", user, password, {"text": text})
    print(f"{user}: {text} -> {status} {body.get('status')}")
    return status, body


def allow(user, password, session_id, paused):
    approval = paused["approval"]
    call = approval["calls"][0]
    status, body = request(
        "POST",
        f"/api/v1/sessions/{session_id}/approvals/{approval['id']}/decision",
        user,
        password,
        {"decisions": [{"toolCallKey": call["toolCallKey"], "decision": "ALLOW"}]},
        {"Idempotency-Key": str(uuid.uuid4())},
    )
    print(f"{user} allows {call['toolName']} ({approval.get('risk')}) -> {status} {body.get('status')}")
    if body.get("status") != "COMPLETED":
        sys.exit(f"Approval did not complete: {status} {body}")


def main():
    wait_until_healthy()
    alice = session("alice", ALICE)
    say("alice", ALICE, alice, "status of APP-1")
    _, paused = say("alice", ALICE, alice, "schedule tech screen for APP-1")
    allow("alice", ALICE, alice, paused)
    _, paused = say("alice", ALICE, alice, "tech screen for APP-1 was yes")
    allow("alice", ALICE, alice, paused)

    bob = session("bob", BOB)
    _, paused = say("bob", BOB, bob, "advance APP-1 to onsite")
    allow("bob", BOB, bob, paused)
    _, paused = say("bob", BOB, bob, "onsite for APP-1 was strong yes")
    allow("bob", BOB, bob, paused)
    _, paused = say("bob", BOB, bob, "advance APP-1 to offer")
    allow("bob", BOB, bob, paused)

    _, paused = say("alice", ALICE, alice, "extend offer for APP-1 L4 150000")
    allow("alice", ALICE, alice, paused)
    _, paused = say("alice", ALICE, alice, "candidate accepted offer for APP-1")
    allow("alice", ALICE, alice, paused)

    status, application = request("GET", "/api/v1/applications/APP-1", "alice", ALICE)
    print(f"APP-1 is {application.get('stage')} (HTTP {status})")
    if application.get("stage") != "HIRED":
        sys.exit("Expected APP-1 to be HIRED")


if __name__ == "__main__":
    main()
