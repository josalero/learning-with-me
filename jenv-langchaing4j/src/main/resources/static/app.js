const resumeField = document.querySelector("#resume");
const form = document.querySelector("#classify-form");
const sampleList = document.querySelector("#sample-list");
const sampleStatus = document.querySelector("#sample-status");
const fileInput = document.querySelector("#resume-file");
const classifyButton = document.querySelector("#classify");
const formError = document.querySelector("#form-error");
const result = document.querySelector("#result");

const levels = ["JUNIOR", "INTERMEDIATE", "SENIOR"];

loadSamples();

form.addEventListener("submit", (event) => {
  event.preventDefault();
  classify(resumeField.value);
});

fileInput.addEventListener("change", async () => {
  const file = fileInput.files && fileInput.files[0];
  if (!file) {
    return;
  }
  resumeField.value = await file.text();
  clearPressedSample();
  formError.hidden = true;
});

async function loadSamples() {
  try {
    const response = await fetch("/api/v1/sample-resumes");
    if (!response.ok) {
      throw new Error("Samples could not be loaded");
    }
    const samples = await response.json();
    sampleStatus.remove();
    for (const sample of samples) {
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = labelFor(sample.name);
      button.addEventListener("click", () => {
        resumeField.value = sample.text;
        clearPressedSample();
        button.setAttribute("aria-pressed", "true");
        formError.hidden = true;
        resumeField.focus();
      });
      sampleList.append(button);
    }
  } catch (error) {
    sampleStatus.textContent = error.message;
  }
}

async function classify(resume) {
  formError.hidden = true;
  if (!resume.trim()) {
    showError("Paste a resume or load a sample first.");
    return;
  }
  setBusy(true);
  result.replaceChildren(paragraph("Classifying…"));
  try {
    const response = await fetch("/api/v1/seniority", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ resume })
    });
    const body = await response.json();
    if (!response.ok) {
      showError(body.message || "Classification failed.");
      result.replaceChildren(paragraph("No result."));
      return;
    }
    renderAssessment(body);
  } catch (error) {
    showError("The lab could not be reached.");
    result.replaceChildren(paragraph("No result."));
  } finally {
    setBusy(false);
  }
}

function renderAssessment(assessment) {
  const level = document.createElement("p");
  level.className = "level";
  level.textContent = titleCase(assessment.level);

  const meta = document.createElement("p");
  meta.className = "meta";
  meta.textContent = `Confidence ${(assessment.confidence * 100).toFixed(0)}% · ${assessment.decider}`;

  const review = document.createElement("p");
  review.className = "review";
  review.textContent = assessment.needsHumanReview
    ? "Needs a person to review this result."
    : "Confidence is high enough to skip human review.";

  const bars = document.createElement("ul");
  bars.className = "bars";
  for (const name of levels) {
    const probability = Number(assessment.probabilities[name] ?? 0);
    const item = document.createElement("li");
    const nameCell = document.createElement("span");
    nameCell.textContent = titleCase(name);
    const track = document.createElement("span");
    track.className = "track";
    const fill = document.createElement("span");
    fill.className = "fill";
    fill.style.width = `${Math.round(probability * 100)}%`;
    track.append(fill);
    const value = document.createElement("span");
    value.textContent = `${Math.round(probability * 100)}%`;
    item.append(nameCell, track, value);
    bars.append(item);
  }

  const explanation = document.createElement("p");
  explanation.className = "explanation";
  explanation.textContent = assessment.explanation;

  result.replaceChildren(level, meta, review, bars, explanation);
}

function showError(message) {
  formError.hidden = false;
  formError.textContent = message;
}

function setBusy(busy) {
  classifyButton.disabled = busy;
  fileInput.disabled = busy;
  classifyButton.textContent = busy ? "Classifying…" : "Classify resume";
  result.setAttribute("aria-busy", busy ? "true" : "false");
}

function clearPressedSample() {
  for (const button of sampleList.querySelectorAll("button")) {
    button.removeAttribute("aria-pressed");
  }
}

function labelFor(fileName) {
  return titleCase(fileName.replace(".txt", "").replaceAll("-", " "));
}

function titleCase(value) {
  return String(value)
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function paragraph(text) {
  const node = document.createElement("p");
  node.className = "empty";
  node.textContent = text;
  return node;
}
