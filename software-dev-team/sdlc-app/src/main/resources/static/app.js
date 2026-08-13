const DEMO =
  "Return 404 with an RFC 9457 problem detail when a user id does not exist, and reject blank names on create.";

const LAST_SCENARIO_KEY = "sdlc.lastScenarioId";

let kinds = ["DEVELOPER"];
let prompts = [];
let teamsById = {};
let projectsById = {};
let scenariosById = {};
let currentRunId = null;
let eventSource = null;
let busy = false;

const $ = (id) => document.getElementById(id);

function setStatus(message) {
  $("status").textContent = message;
}

function showBanner(kind, message) {
  const el = $("banner");
  el.classList.remove(
    "hidden",
    "border-red-300",
    "bg-red-50",
    "text-red-900",
    "border-emerald-300",
    "bg-emerald-50",
    "text-emerald-900"
  );
  if (kind === "error") {
    el.classList.add("border-red-300", "bg-red-50", "text-red-900");
  } else {
    el.classList.add("border-emerald-300", "bg-emerald-50", "text-emerald-900");
  }
  el.textContent = message;
}

function hideBanner() {
  $("banner").classList.add("hidden");
}

async function api(path, options) {
  const response = await fetch(path, options);
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = { detail: text };
  }
  if (!response.ok) {
    throw new Error(body.detail || body.title || response.statusText);
  }
  return body;
}

function showTab(name) {
  document.querySelectorAll("[role='tab']").forEach((btn) => {
    btn.setAttribute("aria-selected", String(btn.dataset.tab === name));
  });
  document.querySelectorAll("[role='tabpanel']").forEach((panel) => {
    const on = panel.id === "panel-" + name;
    panel.hidden = !on;
    panel.classList.toggle("hidden", !on);
  });
  if (name === "results") {
    loadRuns();
  }
}

document.querySelectorAll("[role='tab']").forEach((btn) => {
  btn.addEventListener("click", () => showTab(btn.dataset.tab));
});

function optionList(values, selected) {
  return values
    .map(
      (value) =>
        `<option value="${escapeHtml(value)}" ${value === selected ? "selected" : ""}>${escapeHtml(value)}</option>`
    )
    .join("");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function emptyState(text) {
  return `<p class="p-4 text-sm text-slate-600">${escapeHtml(text)}</p>`;
}

async function loadCatalog() {
  setStatus("Loading catalog");
  try {
    const [teams, projects, promptList, seedList, convList, roleKinds, status, scenarios] = await Promise.all([
      api("/api/v1/teams"),
      api("/api/v1/projects"),
      api("/api/v1/prompts"),
      api("/api/v1/seeds"),
      api("/api/v1/conventions"),
      api("/api/v1/role-kinds"),
      api("/api/v1/status"),
      api("/api/v1/scenarios"),
    ]);
    prompts = promptList;
    kinds = roleKinds.length ? roleKinds : kinds;
    teamsById = Object.fromEntries(teams.map((team) => [team.id, team]));
    projectsById = Object.fromEntries(projects.map((project) => [project.id, project]));
    scenariosById = Object.fromEntries(scenarios.map((scenario) => [scenario.id, scenario]));
    $("run-team").innerHTML = optionList(teams.map((t) => t.id));
    $("run-project").innerHTML = optionList(projects.map((p) => p.id));
    renderTeams(teams);
    renderProjects(projects);
    renderMode(status);
    renderScenarios(scenarios);
    $("proj-seed").innerHTML = optionList(seedList.map((id) => "seeds/" + id));
    $("proj-conv").innerHTML = '<option value="">(none)</option>' + optionList(convList);
    hideBanner();
    if (status.mode === "MISSING_KEY") {
      showBanner(
        "error",
        "No OpenRouter key. Set SDLC_OFFLINE=true for canned runs, or add OPENROUTER_API_KEY for live LLM."
      );
    }
    setStatus("Catalog loaded");
  } catch (error) {
    showBanner("error", error.message);
    $("teams-list").innerHTML = emptyState("Could not load teams.");
    $("projects-list").innerHTML = emptyState("Could not load projects.");
    $("lab-scenarios").innerHTML = `<li>${emptyState("Could not load lab samples.")}</li>`;
  }
}

function renderMode(status) {
  const chip = $("mode-chip");
  chip.className = "chip";
  if (status.mode === "OFFLINE") {
    chip.classList.add("bg-slate-200", "text-slate-800");
    chip.textContent = "Offline · canned model";
  } else if (status.mode === "LIVE") {
    chip.classList.add("bg-emerald-100", "text-emerald-900");
    chip.textContent = "Live · OpenRouter";
  } else {
    chip.classList.add("bg-amber-100", "text-amber-950");
    chip.textContent = "Missing API key";
  }
}

function lastScenarioId() {
  try {
    return sessionStorage.getItem(LAST_SCENARIO_KEY);
  } catch {
    return null;
  }
}

function rememberScenario(id) {
  try {
    sessionStorage.setItem(LAST_SCENARIO_KEY, id);
  } catch {
    /* private browsing */
  }
}

function orderedScenarios() {
  return Object.values(scenariosById).sort((a, b) => a.step - b.step);
}

function highlightLoadedSample() {
  const id = lastScenarioId();
  document.querySelectorAll(".step-card").forEach((card) => {
    if (card.dataset.scenarioId === id) {
      card.setAttribute("aria-current", "step");
    } else {
      card.removeAttribute("aria-current");
    }
  });
  document.querySelectorAll("#lab-progress a").forEach((link) => {
    if (link.dataset.scenarioId === id) {
      link.setAttribute("aria-current", "step");
      link.classList.add("bg-blue-50", "text-blue-800", "border-blue-200");
    } else {
      link.removeAttribute("aria-current");
      link.classList.remove("bg-blue-50", "text-blue-800", "border-blue-200");
    }
  });
  renderSampleGuide();
}

function renderSampleGuide() {
  const el = $("sample-next");
  if (!el) {
    return;
  }
  const current = scenariosById[lastScenarioId()];
  if (!current) {
    el.classList.add("hidden");
    el.textContent = "";
    return;
  }
  const next = orderedScenarios().find((scenario) => scenario.step === current.step + 1);
  el.classList.remove("hidden");
  el.textContent = next
    ? "You ran step " + current.step + " — " + current.title + ". Next: step " + next.step + " — " + next.title + "."
    : "You ran step " + current.step + " — " + current.title + ". That is the last sample.";
}

function renderScenarios(scenarios) {
  const progress = $("lab-progress");
  if (!scenarios.length) {
    $("lab-scenarios").innerHTML = `<li>${emptyState("No lab samples.")}</li>`;
    if (progress) {
      progress.innerHTML = "";
    }
    return;
  }
  const sorted = [...scenarios].sort((a, b) => a.step - b.step);
  if (progress) {
    progress.innerHTML = sorted
      .map(
        (scenario) => `
      <li>
        <a
          class="chip border border-slate-200 bg-white text-slate-800 hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-600"
          href="#sample-${escapeHtml(scenario.id)}"
          data-scenario-id="${escapeHtml(scenario.id)}"
        >${scenario.step} · ${escapeHtml(scenario.track)}</a>
      </li>`
      )
      .join("");
  }
  $("lab-scenarios").innerHTML = sorted
    .map((scenario) => {
      const watch = (scenario.watchFor || [])
        .map((item) => `<li>${escapeHtml(item)}</li>`)
        .join("");
      const startHere =
        scenario.step === 1
          ? `<p class="text-xs font-semibold uppercase tracking-wide text-sky-800">Start here</p>`
          : "";
      return `
    <li>
      <article
        id="sample-${escapeHtml(scenario.id)}"
        class="step-card"
        data-accent="${escapeHtml(scenario.accent || "sky")}"
        data-scenario-id="${escapeHtml(scenario.id)}"
      >
        <div class="flex gap-3">
          <span class="step-num" aria-hidden="true">${scenario.step}</span>
          <div class="min-w-0">
            ${startHere}
            <p class="text-xs font-medium uppercase tracking-wide text-slate-500">${escapeHtml(scenario.track)}</p>
            <h3 class="text-lg font-semibold text-slate-900">${escapeHtml(scenario.title)}</h3>
            <p class="mt-1 text-sm text-slate-600">${escapeHtml(scenario.purpose)}</p>
          </div>
        </div>
        <p class="text-xs text-slate-600">
          <span class="font-medium">${escapeHtml(scenario.teamId)}</span>
          · ${escapeHtml(scenario.projectId)}
          · ${escapeHtml(scenario.durationHint)}
          · ${scenario.worksOffline ? "works offline" : "needs live LLM"}
          ${scenario.needsLiveLlmToChangeCode ? " · live LLM to change code" : ""}
        </p>
        <p class="text-sm text-slate-800">${escapeHtml(scenario.expect)}</p>
        <div>
          <p class="text-sm font-semibold text-slate-900">Look for this</p>
          <ul class="watch-list">${watch}</ul>
        </div>
        <div class="mt-auto flex flex-wrap gap-2">
          <button type="button" class="btn-primary" data-run-scenario="${escapeHtml(scenario.id)}">Reset &amp; run</button>
          <button type="button" class="btn-secondary" data-load-scenario="${escapeHtml(scenario.id)}">Load into custom run</button>
        </div>
      </article>
    </li>`;
    })
    .join("");
  $("lab-scenarios").querySelectorAll("[data-load-scenario]").forEach((btn) => {
    btn.addEventListener("click", () => applyScenario(scenariosById[btn.dataset.loadScenario], true));
  });
  $("lab-scenarios").querySelectorAll("[data-run-scenario]").forEach((btn) => {
    btn.addEventListener("click", () => resetAndRun(scenariosById[btn.dataset.runScenario]));
  });
  highlightLoadedSample();
}

function applyScenario(scenario, openCustom) {
  if (!scenario) {
    return;
  }
  if (!teamsById[scenario.teamId] || !projectsById[scenario.projectId]) {
    showBanner("error", "Catalog is missing " + scenario.teamId + " / " + scenario.projectId + ". Reload the page.");
    return;
  }
  $("run-team").value = scenario.teamId;
  $("run-project").value = scenario.projectId;
  $("run-feature").value = scenario.featureRequest;
  rememberScenario(scenario.id);
  const loaded = $("loaded-sample");
  if (loaded) {
    loaded.classList.remove("hidden");
    loaded.textContent = "Loaded step " + scenario.step + " — " + scenario.title;
    const details = loaded.closest("details");
    if (details && openCustom) {
      details.open = true;
    }
  }
  highlightLoadedSample();
  setStatus("Loaded step " + scenario.step + " — " + scenario.title);
}

function renderTeams(teams) {
  if (!teams.length) {
    $("teams-list").innerHTML = emptyState("No teams yet. Create one below.");
    return;
  }
  $("teams-list").innerHTML = `
    <table class="min-w-full text-left text-sm">
      <caption class="sr-only">Loaded teams</caption>
      <thead class="bg-slate-50 text-slate-700">
        <tr>
          <th class="px-3 py-2 font-medium">Id</th>
          <th class="px-3 py-2 font-medium">Roles</th>
          <th class="px-3 py-2 font-medium">Stakeholder</th>
          <th class="px-3 py-2 font-medium"><span class="sr-only">Actions</span></th>
        </tr>
      </thead>
      <tbody>
        ${teams
          .map(
            (team) => `
          <tr class="border-t border-slate-100">
            <td class="px-3 py-2 font-medium">${escapeHtml(team.id)}</td>
            <td class="px-3 py-2">${escapeHtml(team.roles.map((r) => r.kind).join(", "))}</td>
            <td class="px-3 py-2">${escapeHtml(team.policy.stakeholderMode)}</td>
            <td class="px-3 py-2"><button type="button" class="btn-secondary" data-edit-team="${escapeHtml(team.id)}">Edit</button></td>
          </tr>`
          )
          .join("")}
      </tbody>
    </table>`;
  $("teams-list").querySelectorAll("[data-edit-team]").forEach((btn) => {
    btn.addEventListener("click", () => fillTeam(teamsById[btn.dataset.editTeam]));
  });
}

function renderProjects(projects) {
  if (!projects.length) {
    $("projects-list").innerHTML = emptyState("No projects yet. Create one below.");
    return;
  }
  $("projects-list").innerHTML = `
    <table class="min-w-full text-left text-sm">
      <caption class="sr-only">Loaded projects</caption>
      <thead class="bg-slate-50 text-slate-700">
        <tr>
          <th class="px-3 py-2 font-medium">Id</th>
          <th class="px-3 py-2 font-medium">Seed</th>
          <th class="px-3 py-2 font-medium">Repo</th>
          <th class="px-3 py-2 font-medium"><span class="sr-only">Actions</span></th>
        </tr>
      </thead>
      <tbody>
        ${projects
          .map(
            (project) => `
          <tr class="border-t border-slate-100">
            <td class="px-3 py-2 font-medium">${escapeHtml(project.id)}</td>
            <td class="px-3 py-2">${escapeHtml(project.seed)}</td>
            <td class="px-3 py-2">${escapeHtml(project.repoPath)}</td>
            <td class="px-3 py-2"><button type="button" class="btn-secondary" data-edit-project="${escapeHtml(project.id)}">Edit</button></td>
          </tr>`
          )
          .join("")}
      </tbody>
    </table>`;
  $("projects-list").querySelectorAll("[data-edit-project]").forEach((btn) => {
    btn.addEventListener("click", () => fillProject(projectsById[btn.dataset.editProject]));
  });
}

function roleRow(role) {
  const wrap = document.createElement("div");
  wrap.className = "grid gap-2 rounded-md border border-slate-200 p-3 sm:grid-cols-2";
  wrap.innerHTML = `
    <label class="grid gap-1 text-sm font-medium">id <input name="role-id" class="input" value="${escapeHtml(role?.id || "")}" required /></label>
    <label class="grid gap-1 text-sm font-medium">kind
      <select name="role-kind" class="input">${optionList(kinds, role?.kind)}</select>
    </label>
    <label class="grid gap-1 text-sm font-medium">model <input name="role-model" class="input" value="${escapeHtml(role?.model || "${MODEL_FAST}")}" required /></label>
    <label class="grid gap-1 text-sm font-medium">prompt
      <select name="role-prompt" class="input">${optionList(prompts, role?.prompt)}</select>
    </label>
    <label class="grid gap-1 text-sm font-medium">temperature <input name="role-temp" type="number" step="0.1" min="0" max="2" class="input" value="${role?.temperature ?? 0.2}" /></label>
    <button type="button" class="btn-secondary self-end">Remove role</button>`;
  wrap.querySelector("button").addEventListener("click", () => wrap.remove());
  return wrap;
}

function addRole(role) {
  $("team-roles").appendChild(roleRow(role));
}

$("add-role").addEventListener("click", () =>
  addRole({ id: "", kind: "DEVELOPER", model: "${MODEL_STRONG}", prompt: prompts[0], temperature: 0 })
);

function fillTeam(team) {
  if (!team) {
    return;
  }
  $("team-id").value = team.id;
  $("p-spec").value = team.policy.maxSpecRework;
  $("p-impl").value = team.policy.maxImplementationAttempts;
  $("p-rev").value = team.policy.maxReviewCycles;
  $("p-qa").value = team.policy.maxQaCycles;
  $("p-sh").value = team.policy.maxStakeholderCycles;
  $("p-th").value = team.policy.qaPassThreshold;
  $("p-dev-tools").value = team.policy.maxDeveloperToolCalls ?? 25;
  $("p-read-tools").value = team.policy.maxReadOnlyToolCalls ?? 10;
  $("p-mode").value = team.policy.stakeholderMode;
  $("team-roles").innerHTML = "";
  team.roles.forEach(addRole);
  showTab("teams");
}

function fillProject(project) {
  if (!project) {
    return;
  }
  $("proj-id").value = project.id;
  $("proj-seed").value = project.seed;
  $("proj-repo").value = project.repoPath;
  $("proj-branch").value = project.branchPrefix;
  $("proj-conv").value = project.conventions || "";
  $("proj-timeout").value = project.timeoutSeconds;
  $("proj-globs").value = (project.sourceGlobs || []).join("\n");
  $("proj-commands").value = JSON.stringify(project.commands, null, 2);
  showTab("projects");
}

$("team-reset").addEventListener("click", () => {
  $("team-form").reset();
  $("team-roles").innerHTML = "";
  addRole({
    id: "developer",
    kind: "DEVELOPER",
    model: "${MODEL_STRONG}",
    prompt: "prompts/developer.md",
    temperature: 0,
  });
});

$("project-reset").addEventListener("click", () => $("project-form").reset());

$("demo-fill").addEventListener("click", () => {
  $("run-feature").value = DEMO;
  setStatus("Demo feature filled");
});

async function postWorkspace(path, projectId) {
  await api(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ projectId }),
  });
}

$("seed-workspace").addEventListener("click", async () => {
  try {
    await postWorkspace("/api/v1/workspace/seed", $("run-project").value);
    showBanner("ok", "Workspace seeded for " + $("run-project").value);
  } catch (error) {
    showBanner("error", error.message);
  }
});

$("reset-workspace").addEventListener("click", async () => {
  const projectId = $("run-project").value;
  if (!window.confirm("Replace workspace/" + projectId + " with a fresh seed copy?")) {
    return;
  }
  try {
    await postWorkspace("/api/v1/workspace/reset", projectId);
    showBanner("ok", "Workspace reset for " + projectId);
  } catch (error) {
    showBanner("error", error.message);
  }
});

function setBusy(next) {
  busy = next;
  document.querySelectorAll("#run-form button, [data-run-scenario]").forEach((btn) => {
    btn.disabled = next;
  });
  $("lab-scenarios").setAttribute("aria-busy", String(next));
}

async function startRun(teamId, projectId, featureRequest) {
  const outcome = await api("/api/v1/runs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ teamId, projectId, featureRequest }),
  });
  currentRunId = outcome.runId;
  showBanner("ok", "Run started: " + outcome.runId);
  showTab("results");
  listen(outcome.runId);
}

async function resetAndRun(scenario) {
  if (!scenario || busy) {
    return;
  }
  applyScenario(scenario, false);
  if (!window.confirm("Reset workspace/" + scenario.projectId + " and start '" + scenario.title + "'?")) {
    return;
  }
  setBusy(true);
  setStatus("Resetting workspace and starting run");
  try {
    await postWorkspace("/api/v1/workspace/reset", scenario.projectId);
    await startRun(scenario.teamId, scenario.projectId, scenario.featureRequest);
  } catch (error) {
    showBanner("error", error.message);
  } finally {
    setBusy(false);
  }
}

$("run-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (busy) {
    return;
  }
  setBusy(true);
  try {
    await startRun($("run-team").value, $("run-project").value, $("run-feature").value);
  } catch (error) {
    showBanner("error", error.message);
  } finally {
    setBusy(false);
  }
});

$("team-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const roles = [...$("team-roles").children].map((row) => ({
    id: row.querySelector("[name=role-id]").value,
    kind: row.querySelector("[name=role-kind]").value,
    model: row.querySelector("[name=role-model]").value,
    prompt: row.querySelector("[name=role-prompt]").value,
    temperature: Number(row.querySelector("[name=role-temp]").value),
  }));
  const body = {
    id: $("team-id").value,
    roles,
    policy: {
      maxSpecRework: Number($("p-spec").value),
      maxImplementationAttempts: Number($("p-impl").value),
      maxReviewCycles: Number($("p-rev").value),
      maxQaCycles: Number($("p-qa").value),
      maxStakeholderCycles: Number($("p-sh").value),
      qaPassThreshold: Number($("p-th").value),
      maxDeveloperToolCalls: Number($("p-dev-tools").value),
      maxReadOnlyToolCalls: Number($("p-read-tools").value),
      stakeholderMode: $("p-mode").value,
    },
  };
  try {
    const existing = await fetch("/api/v1/teams").then((r) => r.json());
    const update = existing.some((t) => t.id === body.id);
    await api(update ? "/api/v1/teams/" + body.id : "/api/v1/teams", {
      method: update ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    showBanner("ok", "Team saved: " + body.id);
    await loadCatalog();
  } catch (error) {
    showBanner("error", error.message);
  }
});

$("project-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  let commands;
  try {
    commands = JSON.parse($("proj-commands").value);
  } catch {
    showBanner("error", "Commands must be valid JSON of name to argv arrays.");
    return;
  }
  const body = {
    id: $("proj-id").value,
    seed: $("proj-seed").value,
    repoPath: $("proj-repo").value,
    branchPrefix: $("proj-branch").value,
    sourceGlobs: $("proj-globs")
      .value.split("\n")
      .map((s) => s.trim())
      .filter(Boolean),
    conventions: $("proj-conv").value,
    commands,
    timeoutSeconds: Number($("proj-timeout").value),
  };
  try {
    const existing = await fetch("/api/v1/projects").then((r) => r.json());
    const update = existing.some((p) => p.id === body.id);
    await api(update ? "/api/v1/projects/" + body.id : "/api/v1/projects", {
      method: update ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    showBanner("ok", "Project saved: " + body.id);
    await loadCatalog();
  } catch (error) {
    showBanner("error", error.message);
  }
});

async function loadRuns() {
  $("runs-list").innerHTML = emptyState("Loading runs…");
  renderSampleGuide();
  try {
    const runs = await api("/api/v1/runs");
    if (!runs.length) {
      $("runs-list").innerHTML = emptyState("No runs yet. Start one from the Samples tab.");
      return;
    }
    $("runs-list").innerHTML = `<ul class="divide-y divide-slate-100">${runs
      .map(
        (run) => `
      <li>
        <button type="button" class="w-full px-3 py-3 text-left hover:bg-slate-50 focus:bg-slate-50 focus:outline-none" data-run="${escapeHtml(run.runId)}">
          <span class="block text-sm font-medium">${escapeHtml(run.status)} · ${escapeHtml(run.projectId)}</span>
          <span class="block truncate text-xs text-slate-600">${escapeHtml(run.runId)}</span>
        </button>
      </li>`
      )
      .join("")}</ul>`;
    $("runs-list").querySelectorAll("[data-run]").forEach((btn) => {
      btn.addEventListener("click", () => {
        currentRunId = btn.dataset.run;
        listen(currentRunId);
      });
    });
  } catch (error) {
    $("runs-list").innerHTML = emptyState(error.message);
  }
}

function statusTone(status) {
  if (status === "COMPLETED") {
    return "rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-emerald-950";
  }
  if (status === "FAILED") {
    return "rounded-md border border-red-200 bg-red-50 px-3 py-2 text-red-950";
  }
  if (status === "ESCALATED" || status === "WAITING_APPROVAL") {
    return "rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-amber-950";
  }
  return "rounded-md border border-sky-200 bg-sky-50 px-3 py-2 text-sky-950";
}

const FINISHED_STATUSES = new Set(["COMPLETED", "FAILED", "ESCALATED"]);

// A finished run emits nothing more, and an open EventSource keeps auto-reconnecting to a dead stream.
function closeStreamIfFinished(status) {
  if (eventSource && FINISHED_STATUSES.has(status)) {
    eventSource.close();
    eventSource = null;
  }
}

function listen(runId) {
  if (eventSource) {
    eventSource.close();
  }
  $("run-detail").innerHTML = `<p class="text-sm text-slate-600">Loading ${escapeHtml(runId)}…</p>`;
  refreshRun(runId);
  eventSource = new EventSource("/api/v1/runs/" + runId + "/events");
  eventSource.addEventListener("step", () => refreshRun(runId));
  eventSource.onerror = () => refreshRun(runId);
}

async function refreshRun(runId) {
  try {
    const run = await api("/api/v1/runs/" + runId);
    const files = await api("/api/v1/runs/" + runId + "/artifacts");
    const waiting = run.status === "WAITING_APPROVAL";
    closeStreamIfFinished(run.status);
    const last = scenariosById[lastScenarioId()];
    renderSampleGuide();
    $("run-detail").innerHTML = `
      <div class="${statusTone(run.status)}">
        <h3 class="text-base font-semibold">${escapeHtml(run.status)}</h3>
        <p class="mt-1 text-sm">${escapeHtml(run.teamId)} / ${escapeHtml(run.projectId)}</p>
      </div>
      ${
        last
          ? `<p class="mt-3 text-sm text-slate-700">Sample step ${last.step} — ${escapeHtml(last.title)}. Match the Look for this list on that card.</p>`
          : ""
      }
      <p class="mt-2 text-sm">${escapeHtml(run.featureRequest)}</p>
      ${run.errorMessage ? `<p class="mt-2 text-sm text-red-700">${escapeHtml(run.errorMessage)}</p>` : ""}
      ${
        waiting
          ? `<form id="approve-form" class="mt-4 grid gap-2 rounded-md border border-amber-200 bg-amber-50 p-3">
              <p class="text-sm font-medium">Human stakeholder</p>
              <label class="grid gap-1 text-sm">Decision
                <select name="decision" class="input"><option>APPROVED</option><option>REJECTED</option></select>
              </label>
              <label class="grid gap-1 text-sm">Reasons (one per line)
                <textarea name="reasons" rows="2" class="input"></textarea>
              </label>
              <button type="submit" class="btn-primary w-fit">Submit decision</button>
            </form>`
          : ""
      }
      <h4 class="mt-4 text-sm font-semibold">Timeline</h4>
      <ol class="mt-2 space-y-2 text-sm">
        ${(run.steps || [])
          .map(
            (step) =>
              `<li class="rounded-md bg-slate-50 p-2"><strong>${escapeHtml(step.agent)}</strong> · ${escapeHtml(step.status)} · ${step.elapsedMs}ms<div class="mt-1 text-xs text-slate-600">${escapeHtml(step.outputSummary)}</div></li>`
          )
          .join("") || "<li class='text-slate-600'>No steps yet.</li>"}
      </ol>
      <h4 class="mt-4 text-sm font-semibold">Artifacts</h4>
      <ul class="mt-2 list-disc pl-5 text-sm">
        ${(files || [])
          .map(
            (file) =>
              `<li><a class="text-blue-700 underline" href="/api/v1/runs/${encodeURIComponent(runId)}/artifacts/${encodeURIComponent(file)}" target="_blank" rel="noreferrer">${escapeHtml(file)}</a></li>`
          )
          .join("")}
      </ul>`;
    const form = $("approve-form");
    if (form) {
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const reasons = form.reasons.value
          .split("\n")
          .map((s) => s.trim())
          .filter(Boolean);
        try {
          await api("/api/v1/runs/" + runId + "/approve", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ decision: form.decision.value, reasons, followUps: [] }),
          });
          showBanner("ok", "Decision submitted");
          refreshRun(runId);
        } catch (error) {
          showBanner("error", error.message);
        }
      });
    }
  } catch (error) {
    $("run-detail").innerHTML = `<p class="text-sm text-red-700">${escapeHtml(error.message)}</p>`;
  }
}

addRole({
  id: "developer",
  kind: "DEVELOPER",
  model: "${MODEL_STRONG}",
  prompt: "prompts/developer.md",
  temperature: 0,
});
loadCatalog();
