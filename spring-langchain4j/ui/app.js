(() => {
  const ENGINE_BASE = {
    "spring-ai": "/spring-ai",
    langchain4j: "/langchain4j",
  };

  const ENGINE_LABEL = {
    "spring-ai": "Spring AI",
    langchain4j: "LangChain4j",
    compare: "Compare",
  };

  const form = document.getElementById("research-form");
  const runBtn = document.getElementById("run-btn");
  const resetBtn = document.getElementById("reset-btn");
  const statusEl = document.getElementById("status");
  const errorEl = document.getElementById("error");
  const timelineRoot = document.getElementById("timeline-root");
  const timelineHelp = document.getElementById("timeline-help");
  const timelineEl = document.getElementById("timeline");
  const timelineSpringEl = document.getElementById("timeline-spring");
  const timelineChainEl = document.getElementById("timeline-chain");
  const compareEl = document.getElementById("compare");
  const reportEl = document.getElementById("report");
  const metaEl = document.getElementById("meta");
  const pipelineCompare = document.getElementById("pipeline-compare");

  const timelineColumns = {
    single: timelineRoot.querySelector('[data-engine="single"]'),
    "spring-ai": timelineRoot.querySelector('[data-engine="spring-ai"]'),
    langchain4j: timelineRoot.querySelector('[data-engine="langchain4j"]'),
  };

  const livePassCount = {
    "spring-ai": { writer: 0, critic: 0 },
    langchain4j: { writer: 0, critic: 0 },
  };

  function resetLivePassCounts() {
    livePassCount["spring-ai"] = { writer: 0, critic: 0 };
    livePassCount.langchain4j = { writer: 0, critic: 0 };
  }

  function selectedEngine() {
    return form.querySelector('input[name="engine"]:checked')?.value || "spring-ai";
  }

  function syncPipelineHighlight() {
    if (!pipelineCompare) {
      return;
    }
    pipelineCompare.dataset.engine = selectedEngine();
  }

  function setStatus(message) {
    statusEl.textContent = message;
  }

  function setError(message) {
    if (!message) {
      errorEl.textContent = "";
      errorEl.classList.add("hidden");
      return;
    }
    errorEl.textContent = message;
    errorEl.classList.remove("hidden");
  }

  function setTimelineMode(mode) {
    const compare = mode === "compare";
    timelineRoot.dataset.mode = compare ? "compare" : "single";
    timelineColumns.single.hidden = compare;
    timelineColumns["spring-ai"].hidden = !compare;
    timelineColumns.langchain4j.hidden = !compare;
    timelineHelp.textContent = compare
      ? "Spring AI and LangChain4j steps stream side by side."
      : "Each step shows the agent input and output (expand/collapse as needed).";
  }

  function clearTimeline(mode = "single") {
    setTimelineMode(mode);
    resetLivePassCounts();
    timelineEl.innerHTML = "";
    timelineSpringEl.innerHTML = "";
    timelineChainEl.innerHTML = "";
  }

  function timelineTarget(engine) {
    if (timelineRoot.dataset.mode === "compare") {
      return engine === "langchain4j" ? timelineChainEl : timelineSpringEl;
    }
    return timelineEl;
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;");
  }

  /**
   * Models sometimes wrap the entire report in a single ```markdown fence.
   * That makes marked render raw markdown inside a dark <pre> — unwrap first.
   */
  function normalizeReportMarkdown(markdown) {
    let source = String(markdown || "")
      .replace(/^\uFEFF/, "")
      .trim();
    if (!source) {
      return "";
    }

    const fullFence = source.match(/^```([^\n`]*)\r?\n([\s\S]*?)\r?\n```$/);
    if (fullFence) {
      const lang = (fullFence[1] || "").trim().toLowerCase();
      const inner = fullFence[2].trim();
      if (
        !lang ||
        lang === "markdown" ||
        lang === "md" ||
        lang === "gfm" ||
        /^#{1,6}\s/m.test(inner)
      ) {
        source = inner;
      }
    }

    return source;
  }

  function markdownToHtml(markdown) {
    const source = normalizeReportMarkdown(markdown);
    if (!source) {
      return '<p class="report-placeholder">(empty report)</p>';
    }
    if (window.marked && typeof window.marked.parse === "function") {
      let raw = window.marked.parse(source, {
        gfm: true,
        breaks: true,
      });
      // Safety net: if the only top-level node is a <pre>, re-parse its text as markdown.
      const preOnly = String(raw)
        .trim()
        .match(/^<pre(?:\s[^>]*)?><code(?:\s[^>]*)?>([\s\S]*?)<\/code><\/pre>$/i);
      if (preOnly) {
        const decoded = preOnly[1]
          .replaceAll("&lt;", "<")
          .replaceAll("&gt;", ">")
          .replaceAll("&amp;", "&")
          .replaceAll("&quot;", '"')
          .trim();
        if (/^#{1,6}\s/m.test(decoded) || /\*\*[^*]+\*\*/.test(decoded)) {
          raw = window.marked.parse(decoded, {
            gfm: true,
            breaks: true,
          });
        }
      }
      if (window.DOMPurify && typeof window.DOMPurify.sanitize === "function") {
        return window.DOMPurify.sanitize(raw);
      }
      return raw;
    }
    return `<pre class="step-io-body">${escapeHtml(source)}</pre>`;
  }

  function chip(label, className, attrs = "") {
    return `<span class="report-chip ${className}" ${attrs}>${escapeHtml(label)}</span>`;
  }

  function renderMeta(report, engine) {
    const engineKey = report.engine || engine || "spring-ai";
    const score = report.critique?.score;
    const hasScore = Number.isFinite(Number(score));
    const passLikely = hasScore ? Number(score) >= 9 : null;
    const iters = reviewIterations(report);

    metaEl.innerHTML = [
      chip(ENGINE_LABEL[engineKey] || engineKey, "report-chip--engine", `data-engine="${escapeHtml(engineKey)}"`),
      chip(report.model || "model n/a", "report-chip--model"),
      chip(`${Number(report.elapsedMs || 0)} ms`, "report-chip--time"),
      chip(formatIterations(report), iters > 1 ? "report-chip--score" : "report-chip--muted"),
      hasScore
        ? chip(
            `Critic ${score}/10`,
            "report-chip--score",
            `data-pass="${passLikely}"`
          )
        : chip("No critic score", "report-chip--muted"),
    ].join("");
  }

  function renderReportArticle(markdown) {
    return `<article class="prose-report">${markdownToHtml(markdown)}</article>`;
  }

  function setReportHelp(message) {
    const reportHelp = document.getElementById("report-help");
    if (reportHelp) {
      reportHelp.textContent = message;
    }
  }

  function renderReport(engine, report) {
    setReportHelp("Markdown from the writer agent, rendered for reading.");
    renderMeta(report, engine);
    const markdown = report.finalReport || report.draft || "";
    reportEl.innerHTML = `
      <div class="report-card" data-engine="${escapeHtml(report.engine || engine)}">
        <div class="report-card__title">
          <span>${escapeHtml(ENGINE_LABEL[report.engine || engine] || engine)}</span>
          <span class="text-slate-500 font-medium">${Number(report.elapsedMs || 0)} ms · ${escapeHtml(formatIterations(report))}</span>
        </div>
        <div class="report-card__content">
          ${renderReportArticle(markdown)}
        </div>
      </div>
    `;
  }

  function reportMarkdown(report) {
    return normalizeReportMarkdown(report.finalReport || report.draft || "");
  }

  function wordCount(text) {
    const parts = String(text || "")
      .trim()
      .split(/\s+/)
      .filter(Boolean);
    return parts.length;
  }

  function planQuestions(report) {
    const questions = report.plan?.questions;
    return Array.isArray(questions) ? questions.map((q) => String(q)) : [];
  }

  function findingsCount(report) {
    return Array.isArray(report.findings) ? report.findings.length : 0;
  }

  function compareWinner(springValue, chainValue, { lowerWins = false, numeric = true } = {}) {
    if (!numeric) {
      return "tie";
    }
    const a = Number(springValue);
    const b = Number(chainValue);
    if (!Number.isFinite(a) || !Number.isFinite(b)) {
      return "tie";
    }
    if (a === b) {
      return "tie";
    }
    if (lowerWins) {
      return a < b ? "spring-ai" : "langchain4j";
    }
    return a > b ? "spring-ai" : "langchain4j";
  }

  function winnerLabel(winner) {
    if (winner === "spring-ai") {
      return "Spring AI";
    }
    if (winner === "langchain4j") {
      return "LangChain4j";
    }
    return "Tie";
  }

  function metricCard(label, springValue, chainValue, winner) {
    const winClass =
      winner === "spring-ai"
        ? "metric-card--spring-wins"
        : winner === "langchain4j"
          ? "metric-card--chain-wins"
          : "metric-card--tie";
    return `
      <div class="metric-card ${winClass}">
        <p class="metric-card__label">${escapeHtml(label)}</p>
        <div class="metric-card__values">
          <div data-engine="spring-ai">
            <span class="metric-card__engine">Spring AI</span>
            <strong>${escapeHtml(String(springValue))}</strong>
          </div>
          <div data-engine="langchain4j">
            <span class="metric-card__engine">LangChain4j</span>
            <strong>${escapeHtml(String(chainValue))}</strong>
          </div>
        </div>
        <p class="metric-card__winner">${escapeHtml(winnerLabel(winner))}</p>
      </div>
    `;
  }

  function questionList(questions, emptyLabel) {
    if (!questions.length) {
      return `<p class="report-empty">${escapeHtml(emptyLabel)}</p>`;
    }
    return `<ol class="compare-questions">${questions
      .map((q) => `<li>${escapeHtml(q)}</li>`)
      .join("")}</ol>`;
  }

  function critiqueBlock(report, engine) {
    const score = report.critique?.score;
    const notes = report.critique?.notes || "(no critic notes)";
    return `
      <div class="report-card report-card--compact" data-engine="${escapeHtml(engine)}">
        <div class="report-card__title">
          <span>${escapeHtml(ENGINE_LABEL[engine] || engine)} critic</span>
          <span class="text-slate-500 font-medium">score ${escapeHtml(String(score ?? "n/a"))}/10</span>
        </div>
        <div class="report-card__content">
          <pre class="step-io-body">${escapeHtml(notes)}</pre>
        </div>
      </div>
    `;
  }

  function planBlock(report, engine) {
    const questions = planQuestions(report);
    return `
      <div class="report-card report-card--compact" data-engine="${escapeHtml(engine)}">
        <div class="report-card__title">
          <span>${escapeHtml(ENGINE_LABEL[engine] || engine)} plan</span>
          <span class="text-slate-500 font-medium">${questions.length} question${questions.length === 1 ? "" : "s"}</span>
        </div>
        <div class="report-card__content">
          ${questionList(questions, "No plan questions returned.")}
        </div>
      </div>
    `;
  }

  function renderCompareReports(springReport, chainReport) {
    const springMd = reportMarkdown(springReport);
    const chainMd = reportMarkdown(chainReport);
    const springScore = springReport.critique?.score;
    const chainScore = chainReport.critique?.score;
    const springIters = reviewIterations(springReport);
    const chainIters = reviewIterations(chainReport);
    const springMs = Number(springReport.elapsedMs || 0);
    const chainMs = Number(chainReport.elapsedMs || 0);
    const springWords = wordCount(springMd);
    const chainWords = wordCount(chainMd);
    const springFindings = findingsCount(springReport);
    const chainFindings = findingsCount(chainReport);

    const timeWinner = compareWinner(springMs, chainMs, { lowerWins: true });
    const scoreWinner = compareWinner(springScore, chainScore);
    const iterWinner = compareWinner(springIters, chainIters, { lowerWins: true });
    const wordsWinner = compareWinner(springWords, chainWords);

    const headlineBits = [];
    if (timeWinner !== "tie") {
      headlineBits.push(`${winnerLabel(timeWinner)} faster`);
    }
    if (scoreWinner !== "tie") {
      headlineBits.push(`${winnerLabel(scoreWinner)} higher critic score`);
    }
    if (iterWinner !== "tie") {
      headlineBits.push(`${winnerLabel(iterWinner)} fewer review iterations`);
    }
    const headline =
      headlineBits.length > 0
        ? headlineBits.join(" · ")
        : "Metrics tied or not comparable on this run";

    metaEl.innerHTML = [
      chip(`Spring AI ${springMs} ms · ${formatIterations(springReport)}`, "report-chip--engine", 'data-engine="spring-ai"'),
      chip(`LangChain4j ${chainMs} ms · ${formatIterations(chainReport)}`, "report-chip--engine", 'data-engine="langchain4j"'),
      chip(`Critic ${springScore ?? "n/a"} vs ${chainScore ?? "n/a"}`, "report-chip--score"),
      chip(headline, "report-chip--muted"),
    ].join("");

    setReportHelp("Scoreboard, critic notes, plans, and both final reports side by side.");

    reportEl.innerHTML = `
      <div class="report-compare-wrap">
        <section class="report-scoreboard" aria-label="Report comparison scoreboard">
          <div class="report-scoreboard__header">
            <h3 class="report-scoreboard__title">Report comparison</h3>
            <p class="report-scoreboard__headline">${escapeHtml(headline)}</p>
          </div>
          <div class="metric-grid">
            ${metricCard("Total time", `${springMs} ms`, `${chainMs} ms`, timeWinner)}
            ${metricCard("Critic score", springScore ?? "n/a", chainScore ?? "n/a", scoreWinner)}
            ${metricCard("Review iterations", springIters, chainIters, iterWinner)}
            ${metricCard("Report words", springWords, chainWords, wordsWinner)}
            ${metricCard("Findings", springFindings, chainFindings, compareWinner(springFindings, chainFindings))}
            ${metricCard(
              "Plan questions",
              planQuestions(springReport).length,
              planQuestions(chainReport).length,
              compareWinner(planQuestions(springReport).length, planQuestions(chainReport).length)
            )}
          </div>
        </section>

        <section class="report-compare-section" aria-label="Critic notes comparison">
          <h3 class="report-compare-section__title">Critic notes</h3>
          <div class="report-compare">
            ${critiqueBlock(springReport, "spring-ai")}
            ${critiqueBlock(chainReport, "langchain4j")}
          </div>
        </section>

        <section class="report-compare-section" aria-label="Research plan comparison">
          <h3 class="report-compare-section__title">Research plans</h3>
          <div class="report-compare">
            ${planBlock(springReport, "spring-ai")}
            ${planBlock(chainReport, "langchain4j")}
          </div>
        </section>

        <section class="report-compare-section" aria-label="Final reports side by side">
          <h3 class="report-compare-section__title">Final reports</h3>
          <div class="report-compare">
            <div class="report-card" data-engine="spring-ai">
              <div class="report-card__title">
                <span>Spring AI</span>
                <span class="text-slate-500 font-medium">${springMs} ms · ${escapeHtml(formatIterations(springReport))} · score ${escapeHtml(String(springScore ?? "n/a"))}</span>
              </div>
              <div class="report-card__content">
                ${renderReportArticle(springMd)}
              </div>
            </div>
            <div class="report-card" data-engine="langchain4j">
              <div class="report-card__title">
                <span>LangChain4j</span>
                <span class="text-slate-500 font-medium">${chainMs} ms · ${escapeHtml(formatIterations(chainReport))} · score ${escapeHtml(String(chainScore ?? "n/a"))}</span>
              </div>
              <div class="report-card__content">
                ${renderReportArticle(chainMd)}
              </div>
            </div>
          </div>
        </section>
      </div>
    `;
  }

  function setReportPlaceholder(message, isError = false) {
    setReportHelp("Markdown from the writer agent, rendered for reading.");
    metaEl.innerHTML = chip(isError ? "Failed" : "Idle", "report-chip--muted");
    reportEl.innerHTML = `<p class="${isError ? "report-error" : "report-empty"}">${escapeHtml(message)}</p>`;
  }

  function addStep(engine, step) {
    const li = document.createElement("li");
    li.className = "step-card";
    li.dataset.engine = engine;
    const inputText = step.input || "(no input)";
    const outputText = step.output || step.status || "(no output)";
    const engineLabel = ENGINE_LABEL[engine] || engine;
    const agent = normalizeAgent(step.agent);
    let agentLabel = step.agent || "agent";
    if ((agent === "writer" || agent === "critic") && livePassCount[engine]) {
      livePassCount[engine][agent] += 1;
      agentLabel = `${agent} · pass ${livePassCount[engine][agent]}`;
    }
    li.innerHTML = `
      <div class="flex items-center justify-between gap-2">
        <strong class="capitalize">${escapeHtml(agentLabel)}</strong>
        <span class="text-xs text-slate-500">${escapeHtml(engineLabel)} · ${Number(step.elapsedMs || 0)} ms</span>
      </div>
      <details class="step-io mt-2">
        <summary class="text-xs font-semibold uppercase tracking-wide text-slate-500">Input</summary>
        <pre class="step-io-body">${escapeHtml(inputText)}</pre>
      </details>
      <details class="step-io mt-2" open>
        <summary class="text-xs font-semibold uppercase tracking-wide text-slate-500">Output</summary>
        <pre class="step-io-body">${escapeHtml(outputText)}</pre>
      </details>
    `;
    timelineTarget(engine).appendChild(li);
  }

  function countAgentSteps(report, agent) {
    return (report.steps || []).filter((s) => normalizeAgent(s.agent) === agent).length;
  }

  function normalizeAgent(agent) {
    return String(agent || "")
      .trim()
      .toLowerCase()
      .replace(/[^a-z]/g, "");
  }

  /** One review iteration = one writer pass (paired with a critic in the loop). */
  function reviewIterations(report) {
    const writers = countAgentSteps(report, "writer");
    const critics = countAgentSteps(report, "critic");
    return Math.max(writers, critics, 0);
  }

  function formatIterations(report) {
    const n = reviewIterations(report);
    if (!n) {
      return "0 review iterations";
    }
    return `${n} review iteration${n === 1 ? "" : "s"}`;
  }

  function renderCompare(springReport, chainReport) {
    const agents = ["planner", "researcher", "writer", "critic"];
    const sum = (report, agent) =>
      (report.steps || [])
        .filter((s) => normalizeAgent(s.agent) === agent)
        .reduce((acc, s) => acc + (s.elapsedMs || 0), 0);

    const rows = agents
      .map((agent) => {
        const a = sum(springReport, agent);
        const b = sum(chainReport, agent);
        const aCount = countAgentSteps(springReport, agent);
        const bCount = countAgentSteps(chainReport, agent);
        const countHint =
          agent === "writer" || agent === "critic"
            ? ` <span class="compare-count">×${aCount}</span>`
            : "";
        const countHintB =
          agent === "writer" || agent === "critic"
            ? ` <span class="compare-count">×${bCount}</span>`
            : "";
        return `<tr>
          <td class="capitalize">${agent}</td>
          <td>${a} ms${countHint}</td>
          <td>${b} ms${countHintB}</td>
        </tr>`;
      })
      .join("");

    const springIters = reviewIterations(springReport);
    const chainIters = reviewIterations(chainReport);

    compareEl.innerHTML = `
      <p class="compare-summary">
        Writer/critic loop:
        <strong class="compare-summary__spring">Spring AI ${springIters}</strong>
        vs
        <strong class="compare-summary__chain">LangChain4j ${chainIters}</strong>
        review iteration${springIters === 1 && chainIters === 1 ? "" : "s"}
        <span class="compare-summary__hint">(counted from writer/critic steps)</span>
      </p>
      <table class="compare-table">
        <thead>
          <tr><th>Agent</th><th>Spring AI</th><th>LangChain4j</th></tr>
        </thead>
        <tbody>
          ${rows}
          <tr>
            <td><strong>Review iterations</strong></td>
            <td><strong>${springIters}</strong></td>
            <td><strong>${chainIters}</strong></td>
          </tr>
          <tr>
            <td><strong>Total</strong></td>
            <td><strong>${springReport.elapsedMs || 0} ms</strong></td>
            <td><strong>${chainReport.elapsedMs || 0} ms</strong></td>
          </tr>
        </tbody>
      </table>
    `;
  }

  function runStream(engine, topic, depth, onStep) {
    return new Promise((resolve, reject) => {
      const params = new URLSearchParams({ topic, depth: String(depth) });
      const source = new EventSource(`${ENGINE_BASE[engine]}/api/v1/research/stream?${params}`);
      let settled = false;

      source.addEventListener("step", (event) => {
        try {
          onStep(JSON.parse(event.data));
        } catch (err) {
          console.warn("Bad step payload", err);
        }
      });

      source.addEventListener("report", (event) => {
        try {
          const report = JSON.parse(event.data);
          settled = true;
          source.close();
          resolve(report);
        } catch (err) {
          settled = true;
          source.close();
          reject(err);
        }
      });

      source.onerror = () => {
        if (settled) {
          return;
        }
        settled = true;
        source.close();
        reject(new Error(`${engine} stream failed or was interrupted`));
      };
    });
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    setError("");
    compareEl.textContent = "Choose Compare both to see side-by-side timings.";
    setReportPlaceholder("Running…");
    metaEl.innerHTML = chip("Running", "report-chip--time");

    const topic = form.topic.value.trim();
    const depth = Number(form.depth.value);
    const engine = selectedEngine();

    if (!topic) {
      setError("Topic is required.");
      setReportPlaceholder("Waiting for a completed run.");
      return;
    }

    clearTimeline(engine === "compare" ? "compare" : "single");
    runBtn.disabled = true;
    setStatus("Running research pipeline…");

    try {
      if (engine === "compare") {
        setStatus("Comparing Spring AI and LangChain4j…");
        const [springReport, chainReport] = await Promise.all([
          runStream("spring-ai", topic, depth, (step) => addStep("spring-ai", step)),
          runStream("langchain4j", topic, depth, (step) => addStep("langchain4j", step)),
        ]);
        renderCompare(springReport, chainReport);
        renderCompareReports(springReport, chainReport);
        setStatus("Compare run completed.");
      } else {
        const report = await runStream(engine, topic, depth, (step) => addStep(engine, step));
        renderReport(engine, report);
        setStatus(`Completed with ${engine}.`);
      }
    } catch (err) {
      console.error(err);
      setError(err.message || "Unexpected failure");
      setStatus("Run failed.");
      setReportPlaceholder("No report — the run failed.", true);
    } finally {
      runBtn.disabled = false;
    }
  });

  form.querySelectorAll('input[name="engine"]').forEach((input) => {
    input.addEventListener("change", syncPipelineHighlight);
  });

  resetBtn.addEventListener("click", () => {
    form.reset();
    setError("");
    setStatus("Ready. Enter a topic and choose an engine.");
    clearTimeline("single");
    timelineEl.innerHTML =
      '<li class="text-sm text-slate-500">No steps yet. Run a research job to see planner, researcher, writer, and critic inputs and outputs.</li>';
    compareEl.textContent = "Choose Compare both to see side-by-side timings.";
    setReportPlaceholder("Waiting for a completed run.");
    syncPipelineHighlight();
  });

  syncPipelineHighlight();
})();
