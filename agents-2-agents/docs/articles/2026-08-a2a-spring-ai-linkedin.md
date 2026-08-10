# Article: Agent2Agent with Spring AI (LinkedIn long-form)

| Meta | |
| --- | --- |
| **Type** | LinkedIn long-form (tutorial + architecture lesson) |
| **Audience** | Java / Spring engineers building LLM agents |
| **Platform** | LinkedIn article (also fine on a personal blog) |
| **Outcome** | Reader can explain A2A discovery + message flow and sketch a two-service Spring AI setup |
| **Author** | José Adrián Alemán Rojas |
| **Status** | Draft ready for publish review |

## Working title options

1. **Agent2Agent on Spring Boot: one specialist, one orchestrator, no megaprompt**
2. **I stopped stuffing every hiring skill into one agent. A2A made the split real.**
3. **Spring AI + A2A in practice: Agent Cards, JSON-RPC, and a skills matcher you can trust**

**Recommended:** option 1 (clear outcome; less product-forward in the title).

## Outline (one-liners)

1. Hook — single-agent prompts rot when hiring workflows grow
2. Context — why agent interoperability showed up in my stack now
3. Thesis — A2A is discovery + messages; keep scoring in code where it belongs
4. Mental model — Card → Message → Task → Artifact
5. Walkthrough — orchestrator + skills-agent on Java 26 / Boot 4.1 / Spring AI 2
6. Failure mode — Agent Card `url` lies in Docker if you leave `localhost`
7. Takeaways — actionable next steps
8. Close — comment prompt (no default promo CTA)

## Suggested LinkedIn feed hook (first ~210 chars)

> I used to dump skills matching, salary checks, and summary writing into one Spring AI agent. It worked in demos. It fell apart the moment I needed a second team to own one capability. Agent2Agent fixed the boundary, not the model.

## Pull quotes

- "A2A is not another ChatClient wrapper. It is how agents find each other and exchange work."
- "If the score matters to a recruiter, compute it in Java and let the LLM narrate."
- "In Docker, the Agent Card URL is part of your API contract. `localhost` is a bug."

---

# Agent2Agent on Spring Boot: one specialist, one orchestrator, no megaprompt

I used to dump skills matching, salary checks, and summary writing into one Spring AI agent.

It worked in demos. It fell apart the moment I needed a second team (or a second service) to own one capability without inheriting the whole prompt.

That is the problem Agent2Agent (A2A) is meant to solve: a standard way for agents to **discover** each other and **exchange work**, even when they run as separate apps.

## Why this showed up in my work

At ITJobOpportunities I keep building screening and assessment flows: résumé signals, job fit, Code Training Lab exercises, recruiter-facing summaries. The temptation is always the same. One fat system prompt. One `ChatClient`. Every new rule bolted on until nobody wants to touch it.

I wanted the opposite shape: small specialists with clear ownership, and an orchestrator that only decides whom to call. A2A gave me a protocol for that shape instead of another private REST convention between "agent" microservices.

So I built a short exercise on **Java 26**, **Spring Boot 4.1**, **Spring AI 2**, and the Spring AI Community **spring-ai-a2a** server starter, with **OpenRouter** behind the OpenAI-compatible client. Two processes. One happy path a recruiter can hit with curl.

## The thesis

A2A is discovery plus messages. Your product still decides what is deterministic and what is generative.

In my skills matcher, overlap scoring lives in plain Java. The LLM calls a tool, then writes a short summary. The orchestrator never invents a fit percentage. That split is the architecture lesson. The protocol is just how the two Spring apps talk.

## Mental model (four nouns)

| Concept | Role |
| --- | --- |
| **Agent Card** | JSON metadata: name, skills, public URL |
| **Message** | Natural-language task from a client agent |
| **Task** | Server-side unit of work created from that message |
| **Artifact** | Result payload (usually text parts) returned on the task |

```text
Recruiter HTTP
  → Screening orchestrator (A2A client + ChatClient + tool)
    → Skills matcher (A2A server + ChatClient + match-skills tool)
      → Artifact text
  → Short screening verdict
```

Notice the double hop through an LLM. That is normal. One model plans the delegation. The specialist model (or the same provider with a different system prompt) decides when to call tools. Cost and latency follow. Do not pretend this is a free abstraction.

## What I actually wired

### Skills agent (A2A server)

Spring AI Community autoconfiguration needs three beans you own:

1. `AgentCard` — who you are and where clients should call you  
2. `AgentExecutor` — how an inbound A2A message becomes a reply  
3. `ChatClient` — Spring AI entry point with tools registered  

I keep packages boring on purpose:

- `domain` — `SkillsMatcher` computes score / matched / missing  
- `tools` — `@Tool` adapter only  
- `config` — ChatClient, Agent Card, AgentExecutor  

The card is served at `/.well-known/agent-card.json` under context path `/a2a`. Clients discover first. Then they send work.

Minimal executor shape:

```java
@Bean
AgentExecutor agentExecutor(ChatClient skillsChatClient) {
  return new DefaultAgentExecutor(skillsChatClient, (client, ctx) -> {
    String userMessage = DefaultAgentExecutor.extractTextFromMessage(ctx.getMessage());
    return client.prompt().user(userMessage).call().content();
  });
}
```

### Screening orchestrator (A2A client + REST)

On startup, `AgentRegistry` loads cards from configured base URLs. If the skills agent is down, the orchestrator fails fast. I prefer that over a silent empty registry.

The LLM gets one tool: `send-message-to-agent(agentName, task)`. The system prompt lists discovered agents by name and tells the model not to score candidates itself.

The public API stays thin:

```http
POST /api/v1/screenings
Content-Type: application/json

{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "jobTitle": "Backend Developer",
  "requiredSkills": "Java, Spring Boot, AWS, Kafka",
  "candidateSkills": "Java, Spring Boot, Azure, Kafka",
  "expectedSalary": 110000
}
```

For that sample, deterministic scoring lands around **75%** with `aws` missing. The verdict prose can vary by model. The number should not.

## The failure mode that burns people in Docker

Discovery URL and Agent Card `url` are not the same concern.

Your client can fetch the card from `http://skills-agent:8081/a2a/.well-known/agent-card.json` and still fail on send if the card advertises `http://localhost:8081/a2a/`. Inside a container, `localhost` is the container itself.

Treat the published card URL as part of the contract. In Compose I set:

- `A2A_AGENT_PUBLIC_URL=http://skills-agent:8081/a2a/`
- `SKILLS_AGENT_URL=http://skills-agent:8081/a2a`

Same lesson shows up in any registry-driven system. Metadata that points at the wrong network view is worse than no metadata.

## OpenRouter note (Spring AI 2)

I route chat through OpenRouter with the OpenAI starter:

- `spring.ai.openai.base-url=https://openrouter.ai/api/v1` (include `/v1`)  
- `spring.ai.openai.api-key=${OPENROUTER_API_KEY}`  
- model id like `openai/gpt-4o-mini`  

Spring AI 2’s OpenAI Java SDK appends `/chat/completions` to the base URL. Dropping `/v1` is a quiet 404.

## What I would not claim yet

This exercise is not a production hiring mesh. No auth on the A2A surface. No streaming. No multi-tenant isolation. No eval harness for the orchestrator’s tool choices.

It is enough to prove the boundary: specialist ownership, protocol discovery, deterministic scoring where trust matters, generative text where language helps.

When I look at recruiter AI assessments and screening on ITJobOpportunities, that boundary is the design I want to keep as capabilities multiply. Protocols come and go. Owning the score in code ages better than hoping the prompt remembers your rubric.

## Takeaways

- Start with **one** specialist agent and one orchestrator. Add agents only after discovery and Docker networking are boring.
- Put **Agent Card URL** in config per environment. Never hardcode `localhost` for anything that other containers must call.
- Keep high-stakes numbers in **domain services**; expose them with `@Tool`.
- Fail startup if remote cards cannot load. Empty registries create worse bugs later.
- Budget for **two LLM hops** and measure them before you celebrate the architecture diagram.

## Close

If you are splitting agent capabilities across services, what is the first capability you would refuse to keep inside the orchestrator prompt?

---

#SpringAI #A2A #Java #SpringBoot #SoftwareArchitecture

## Pre-publish checklist

| Item | Status |
| --- | --- |
| Title + hook work as feed preview | Done (see hook variants) |
| Technical claims match the exercise (Boot 4.1, Java 26, spring-ai-a2a 0.3.0, OpenRouter `/v1`) | Done |
| Code snippets minimal + language tagged | Done |
| No credentials / PII | Done |
| CTA = comment question (no default promo link) | Done |
| Hashtags 3–5 at end | Done |
| ITJobOpportunities tie is light builder lesson, not pitch | Done |
| No em dashes / banned AI filler | Done (review once in LinkedIn editor) |

## Open questions for you

1. Prefer title option 1, 2, or 3 for the LinkedIn editor?
2. Want a Part 2 that adds a second A2A specialist (salary or background) and shows multi-tool orchestration?
3. Should the published version link the public GitHub repo once it is pushed, or stay link-free?
