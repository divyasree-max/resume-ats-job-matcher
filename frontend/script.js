const API_BASE = "http://localhost:8081/api";

let currentResumeId = null;
let debounceTimer = null;
let latestResults = [];
let selectedJobType = "all";
const DEBOUNCE_MS = 600;

const roadmapData = {
  dataScientist: {
    title: "Data Scientist", summary: "Make decisions clearer with evidence, experiments, and models.",
    stack: ["Python", "SQL", "Pandas", "NumPy", "scikit-learn", "Statistics", "Git", "Jupyter"],
    projects: ["Build a customer-churn model with an honest evaluation report.", "Create an end-to-end dashboard from a messy public dataset.", "Run an A/B test analysis and explain uncertainty in plain language."],
    courses: ["Kaggle Learn: Python, Pandas, SQL, and Intro to ML.", "DeepLearning.AI: Mathematics for Machine Learning.", "Practice one structured case study each week."],
    checklist: ["Publish two polished GitHub projects", "Add a measurable project result to your resume", "Complete one Kaggle competition", "Practice SQL and statistics interview questions"],
    platforms: [["K", "Kaggle", "Competitions and notebooks"], ["G", "GitHub", "Project proof"], ["C", "Coursera", "Structured learning"]],
    opportunities: { internships: ["Search university labs, analytics teams, and startup internships with a project-first portfolio.", "Apply for research assistant roles where Python, SQL, and experimentation are used."], remote: ["Target junior analytics, BI, and ML support roles that accept project-based experience.", "Show a readable README, demo video, and reproducible notebook for remote hiring."], competitions: ["Kaggle tabular or playground competitions", "DrivenData social-impact challenges", "Local data meetups and hackathons"] }
  },
  aiEngineer: {
    title: "AI Engineer", summary: "Turn models into reliable products that people can use.",
    stack: ["Python", "PyTorch", "APIs", "FastAPI", "Docker", "SQL", "Cloud", "Testing"],
    projects: ["Deploy a document classifier as a tested FastAPI service.", "Build a RAG assistant with citations, evaluation cases, and guardrails.", "Create a model-monitoring report for latency, quality, and drift."],
    courses: ["DeepLearning.AI: Machine Learning Engineering for Production.", "Full Stack Deep Learning lectures and labs.", "Practice Docker, API testing, and cloud deployment."],
    checklist: ["Deploy one AI API publicly", "Add tests and evaluation data to a project", "Document latency and cost trade-offs", "Complete an ML system design mock interview"],
    platforms: [["F", "Full Stack DL", "Production practice"], ["H", "Hugging Face", "Models and demos"], ["G", "GitHub", "Engineering proof"]],
    opportunities: { internships: ["Look for ML platform, applied AI, and software engineering internships.", "A small shipped service can substitute for limited professional experience when documented well."], remote: ["Search applied AI, ML engineer, and backend roles with remote or distributed teams.", "Emphasize API ownership, tests, deployment, observability, and written communication."], competitions: ["Hugging Face community challenges", "AI hackathons on Devpost", "Open-source issues in model-serving projects"] }
  },
  promptEngineer: {
    title: "Prompt Engineer", summary: "Design, test, and improve language-model behavior for real workflows.",
    stack: ["Python", "Prompt design", "Evaluation", "JSON", "APIs", "RAG", "Safety", "Analytics"],
    projects: ["Build a prompt test suite with 30 edge cases and scored outputs.", "Create a structured extraction workflow with JSON validation and retries.", "Compare prompt versions using a small evaluation dataset and a clear rubric."],
    courses: ["DeepLearning.AI: ChatGPT Prompt Engineering for Developers.", "OpenAI and Anthropic prompting and safety guides.", "Learn basic Python, APIs, JSON schema, and experiment tracking."],
    checklist: ["Publish a prompt evaluation report", "Show before/after quality metrics", "Build one API-based workflow", "Document failure modes and safety boundaries"],
    platforms: [["D", "Devpost", "Workflow hackathons"], ["H", "Hugging Face", "Community demos"], ["G", "GitHub", "Evaluation proof"]],
    opportunities: { internships: ["Search AI operations, conversational AI, content automation, and AI product internships.", "Frame prompt work as evaluation and workflow engineering, not just clever prompts."], remote: ["Target AI automation, conversation design, and LLM evaluation roles.", "Remote teams value precise test cases, written decisions, and reproducible experiments."], competitions: ["Prompt contests and evaluation challenges", "Devpost generative-AI hackathons", "Open-source benchmark contributions"] }
  },
  genAiEngineer: {
    title: "Generative AI / LLM Engineer", summary: "Build useful, grounded, and measurable systems around language models.",
    stack: ["Python", "LLM APIs", "RAG", "Vector DB", "Agents", "FastAPI", "Docker", "Evaluation"],
    projects: ["Build a cited RAG app over a real document collection.", "Create a tool-using agent with approval steps and trace logs.", "Ship an LLM evaluation harness for hallucination, relevance, and latency."],
    courses: ["DeepLearning.AI: Building Systems with the ChatGPT API.", "LangChain or LlamaIndex fundamentals, then reproduce the ideas without hiding the architecture.", "Learn embeddings, retrieval, tool calling, prompt injection, and cost control."],
    checklist: ["Ship a RAG project with citations", "Implement an agent with a bounded tool set", "Add an evaluation dataset and failure analysis", "Explain privacy, cost, and prompt-injection risks"],
    platforms: [["L", "LangChain", "Agent patterns"], ["HF", "Hugging Face", "Open models"], ["D", "Devpost", "GenAI builds"]],
    opportunities: { internships: ["Look for LLM application, AI platform, developer tools, and research engineering internships.", "A strong portfolio should show evaluation and safety, not only a chatbot screen."], remote: ["Search LLM application engineer, AI solutions, and automation engineer roles.", "Lead with shipped demos, architecture diagrams, and evidence your system works."], competitions: ["GenAI hackathons on Devpost", "Hugging Face open-source challenges", "LLM evaluation and red-teaming events"] }
  }
};

let selectedRoadmapRole = "dataScientist";
let selectedOpportunity = "internships";

function getRoadmap() {
  return roadmapData[selectedRoadmapRole] || roadmapData.custom;
}

function createCustomRoadmap(title) {
  const role = title.trim() || "Software Developer";
  const roleText = role.toLowerCase();
  const stack = roleText.includes("design") || roleText.includes("ux")
    ? ["Figma", "HTML", "CSS", "JavaScript", "Accessibility", "User research", "Git", "Portfolio"]
    : roleText.includes("security")
      ? ["Python", "Linux", "Networking", "SIEM", "Cloud", "OWASP", "SQL", "Git"]
      : roleText.includes("cloud") || roleText.includes("devops")
        ? ["Linux", "Python", "Docker", "Kubernetes", "CI/CD", "Cloud", "Terraform", "Git"]
        : ["Python or Java", "SQL", "Git", "APIs", "Data structures", "Testing", "Cloud basics", "Communication"];
  return {
    title: role, summary: `Build credible proof for ${role} with focused skills, shipped work, and real-world practice.`, stack,
    projects: [`Build a small end-to-end ${role} project that solves a real user problem.`, "Publish a clear README, demo, architecture note, and lessons learned.", `Recreate one common ${role} workflow using a public dataset, API, or open-source tool.`],
    courses: [`Search Coursera, edX, and freeCodeCamp for a beginner-to-intermediate ${role} path.`, "Use official documentation to build one feature while learning.", "Practice interview questions and write a short weekly progress note."],
    checklist: ["Publish two role-relevant projects", "Add measurable outcomes to your resume", "Complete one recognized course or certification", "Join one community event or open-source contribution"],
    platforms: [["G", "GitHub", "Project proof"], ["D", "Devpost", "Hackathons and demos"], ["C", "Coursera", "Structured learning"]],
    opportunities: { internships: [`Search ${role} internships, apprenticeships, and research assistant roles.`, "Tailor one project README to each internship and show what you personally built."], remote: [`Search junior ${role} roles and distributed teams on legitimate job boards.`, "Show async communication, documentation, and a working demo in your application."], competitions: ["Join a relevant Devpost hackathon or community challenge.", "Contribute a small fix, tutorial, or issue investigation to an open-source project."] }
  };
}

function renderRoadmap() {
  const roadmap = getRoadmap();
  document.getElementById("roadmap-title").textContent = roadmap.title;
  document.getElementById("roadmap-summary").textContent = roadmap.summary;
  document.getElementById("roadmap-state").textContent = `${roadmap.title} path`;
  document.getElementById("roadmap-stack").innerHTML = roadmap.stack.map((item) => `<span class="roadmap-tag">${escapeHtml(item)}</span>`).join("");
  renderList("roadmap-projects", roadmap.projects);
  renderList("roadmap-courses", roadmap.courses);
  document.getElementById("roadmap-platforms").innerHTML = roadmap.platforms.map(([mark, name, detail]) => `<a class="platform-card" href="https://www.google.com/search?q=${encodeURIComponent(name + " " + roadmap.title + " course")}" target="_blank" rel="noopener" title="${escapeHtml(detail)}"><span class="platform-logo">${escapeHtml(mark)}</span><span><strong>${escapeHtml(name)}</strong><small>${escapeHtml(detail)}</small></span></a>`).join("");
  const checklist = document.getElementById("roadmap-checklist");
  checklist.innerHTML = roadmap.checklist.map((item, index) => `<label class="check-item"><input type="checkbox" data-check-index="${index}"><span>${escapeHtml(item)}</span></label>`).join("");
  checklist.querySelectorAll("input").forEach((input) => input.addEventListener("change", updateRoadmapProgress));
  const saved = JSON.parse(localStorage.getItem(`roadmap-${roadmap.title}`) || "[]");
  checklist.querySelectorAll("input").forEach((input, index) => { input.checked = saved.includes(index); });
  updateRoadmapProgress();
  renderOpportunities();
}

function renderList(elementId, items) {
  document.getElementById(elementId).innerHTML = items.map((item) => `<li>${escapeHtml(item)}</li>`).join("");
}

function updateRoadmapProgress() {
  const inputs = [...document.querySelectorAll("#roadmap-checklist input")];
  const complete = inputs.filter((input) => input.checked).length;
  const percent = inputs.length ? Math.round((complete / inputs.length) * 100) : 0;
  localStorage.setItem(`roadmap-${getRoadmap().title}`, JSON.stringify(inputs.map((input, index) => input.checked ? index : null).filter((index) => index !== null)));
  document.getElementById("roadmap-progress-value").textContent = `${percent}%`;
  document.getElementById("roadmap-progress-bar").style.width = `${percent}%`;
}

function renderOpportunities() {
  document.getElementById("opportunity-content").innerHTML = getRoadmap().opportunities[selectedOpportunity].map((item) => `<div class="opportunity-item"><span class="opportunity-mark">✓</span><span>${escapeHtml(item)}</span></div>`).join("");
}

document.getElementById("set-role-btn").addEventListener("click", () => {
  const title = document.getElementById("target-role-input").value.trim();
  const knownRole = Object.values(roadmapData).find((roadmap) => roadmap.title.toLowerCase() === title.toLowerCase());
  if (knownRole) selectedRoadmapRole = Object.keys(roadmapData).find((key) => roadmapData[key] === knownRole);
  else { selectedRoadmapRole = "custom"; roadmapData.custom = createCustomRoadmap(title); }
  renderRoadmap();
});

document.querySelectorAll("[data-role-suggestion]").forEach((button) => button.addEventListener("click", () => {
  document.getElementById("target-role-input").value = button.dataset.roleSuggestion;
  document.getElementById("set-role-btn").click();
}));

document.querySelectorAll(".opportunity-tab").forEach((button) => {
  button.addEventListener("click", () => {
    selectedOpportunity = button.dataset.opportunity;
    document.querySelectorAll(".opportunity-tab").forEach((tab) => tab.classList.toggle("active", tab === button));
    renderOpportunities();
  });
});

renderRoadmap();

function showView(view) {
  const homeView = document.getElementById("home-view");
  const careerView = document.getElementById("roadmap-panel");
  const showCareer = view === "career";
  homeView.classList.toggle("hidden", showCareer);
  careerView.classList.toggle("hidden", !showCareer);
  if (showCareer) careerView.scrollIntoView({ behavior: "smooth", block: "start" });
}

document.querySelectorAll(".top-nav a").forEach((link) => link.addEventListener("click", (event) => {
  const target = link.getAttribute("href");
  if (target === "#roadmap-panel") {
    event.preventDefault();
    showView("career");
  } else if (target === "#search-panel") {
    event.preventDefault();
    showView("home");
    document.getElementById("search-panel").scrollIntoView({ behavior: "smooth", block: "start" });
  } else {
    event.preventDefault();
    showView("home");
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
}));

document.getElementById("open-guide-btn").addEventListener("click", () => showView("career"));

document.querySelectorAll(".job-type-tab").forEach((button) => button.addEventListener("click", () => {
  selectedJobType = button.dataset.jobType;
  document.querySelectorAll(".job-type-tab").forEach((tab) => tab.classList.toggle("active", tab === button));
  document.getElementById("search-status").textContent = selectedJobType === "all" ? "Searching all roles" : `Ready to search ${selectedJobType} roles`;
  document.getElementById("search-status").className = "status-line";
}));

const connectionStatus = document.getElementById("connection-status");

async function checkApi() {
  try {
    const response = await fetch(`${API_BASE}/jobs/search`, { method: "OPTIONS" });
    connectionStatus.classList.add("online");
    connectionStatus.innerHTML = '<span class="status-dot"></span> API ready';
  } catch (err) {
    connectionStatus.classList.add("offline");
    connectionStatus.innerHTML = '<span class="status-dot"></span> Backend offline';
  }
}

checkApi();

async function responseError(response, action) {
  let detail = "";
  try {
    const body = await response.json();
    detail = body.message || body.error || "";
  } catch (err) {
    // The server may return an empty response for infrastructure errors.
  }
  return new Error(`${action} failed (${response.status})${detail ? `: ${detail}` : ""}`);
}

// ---------------------------------------------------------------------
// Resume upload
// ---------------------------------------------------------------------

const dropzone = document.getElementById("dropzone");
const fileInput = document.getElementById("file-input");
const uploadStatus = document.getElementById("upload-status");
const resumeSummary = document.getElementById("resume-summary");

dropzone.addEventListener("click", () => fileInput.click());

dropzone.addEventListener("dragover", (e) => {
  e.preventDefault();
  dropzone.classList.add("drag-over");
});

dropzone.addEventListener("dragleave", () => dropzone.classList.remove("drag-over"));

dropzone.addEventListener("drop", (e) => {
  e.preventDefault();
  dropzone.classList.remove("drag-over");
  if (e.dataTransfer.files.length > 0) {
    handleFileUpload(e.dataTransfer.files[0]);
  }
});

fileInput.addEventListener("change", () => {
  if (fileInput.files.length > 0) {
    handleFileUpload(fileInput.files[0]);
  }
});

async function handleFileUpload(file) {
  if (!file) return;
  const allowedTypes = ["application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword"];
  if (!allowedTypes.includes(file.type) && !/\.(pdf|docx?|PDF|DOCX?)$/.test(file.name)) {
    uploadStatus.textContent = "Please choose a PDF, DOCX, or DOC file.";
    uploadStatus.className = "status-line error";
    return;
  }
  uploadStatus.textContent = "Uploading and parsing...";
  uploadStatus.className = "status-line";
  document.getElementById("resume-state").textContent = "Reading resume...";

  const formData = new FormData();
  formData.append("file", file);

  try {
    const response = await fetch(`${API_BASE}/resumes/upload`, {
      method: "POST",
      body: formData,
    });

    if (!response.ok) throw await responseError(response, "Upload");

    const data = await response.json();
    currentResumeId = data.resumeId;

    document.getElementById("mode-value").textContent =
      data.experienceMode === "FRESHER" ? "Fresher" : "Experienced";
    document.getElementById("years-value").textContent =
      `${data.totalYearsExperience} years`;
    document.getElementById("confidence-value").textContent = data.detectionConfidence;
    document.getElementById("filename-value").textContent = data.filename;
    document.getElementById("resume-state").textContent = "Resume ready";
    document.getElementById("dropzone-text").textContent = "Replace resume";

    resumeSummary.classList.remove("hidden");

    if (data.lowExtractionConfidence) {
      uploadStatus.textContent =
        "We had trouble reading this file clearly. If your score looks off, try a different file format or paste your resume text manually.";
      uploadStatus.className = "status-line warn";
    } else if (data.detectionConfidence === "low") {
      uploadStatus.textContent =
        "Uploaded. We're not fully confident in the experience detection, please check the mode above.";
      uploadStatus.className = "status-line warn";
    } else {
      uploadStatus.textContent = `Uploaded successfully: ${data.filename}`;
      uploadStatus.className = "status-line success";
    }

    // If a JD is already pasted, score it now that we have a resume.
    const jdText = document.getElementById("jd-input").value.trim();
    if (jdText.length > 30) {
      scoreAgainstJd(jdText);
    }
  } catch (err) {
    document.getElementById("resume-state").textContent = "Upload needs attention";
    uploadStatus.textContent = `${err.message}. Check that the backend is running, then try again.`;
    uploadStatus.className = "status-line error";
    console.error(err);
  }
}

document.getElementById("override-mode-btn").addEventListener("click", () => {
  const current = document.getElementById("mode-value").textContent;
  const next = current === "Fresher" ? "Experienced" : "Fresher";
  document.getElementById("mode-value").textContent = next;
  document.getElementById("confidence-value").textContent = "manually set";
  uploadStatus.textContent = `Mode set to ${next}. This preference will be used for the next score in this session.`;
  uploadStatus.className = "status-line success";
});

// ---------------------------------------------------------------------
// Live JD scoring
// ---------------------------------------------------------------------

const jdInput = document.getElementById("jd-input");

jdInput.addEventListener("input", () => {
  clearTimeout(debounceTimer);
  const text = jdInput.value.trim();
  document.getElementById("jd-counter").textContent = `${jdInput.value.length} characters`;

  if (text.length < 30) {
    document.getElementById("score-result").classList.add("hidden");
    document.getElementById("score-status").textContent = currentResumeId ? "Keep typing for a score" : "Upload a resume to score";
    return;
  }

  document.getElementById("score-status").textContent = currentResumeId ? "Scoring..." : "Upload a resume to score";
  debounceTimer = setTimeout(() => scoreAgainstJd(text), DEBOUNCE_MS);
});

async function scoreAgainstJd(jdText) {
  if (!currentResumeId) {
    document.getElementById("score-status").textContent = "Upload a resume to score";
    return;
  }

  try {
    const response = await fetch(`${API_BASE}/resumes/score-jd`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ resumeId: currentResumeId, jdText }),
    });

    if (!response.ok) throw await responseError(response, "Scoring");

    const data = await response.json();
    renderScore(data);
    document.getElementById("score-status").textContent = "Updated just now";
  } catch (err) {
    document.getElementById("score-status").textContent = err.message;
    console.error(err);
  }
}

function renderScore(data) {
  document.getElementById("score-result").classList.remove("hidden");
  document.getElementById("score-number").textContent = Math.round(data.finalScore);
  document.getElementById("semantic-bar").style.width = `${data.semanticSimilarity * 100}%`;
  document.getElementById("keyword-bar").style.width = `${data.keywordMatchPct * 100}%`;

  const matchedList = document.getElementById("matched-skills-list");
  matchedList.innerHTML = "";
  data.matchedSkills.forEach((skill) => {
    const li = document.createElement("li");
    li.textContent = skill;
    matchedList.appendChild(li);
  });

  const missingList = document.getElementById("missing-skills-list");
  missingList.innerHTML = "";
  data.missingSkills.forEach((skill) => {
    const li = document.createElement("li");
    li.textContent = skill;
    missingList.appendChild(li);
  });

  const suggestionsList = document.getElementById("suggestions-list");
  suggestionsList.innerHTML = "";
  data.suggestions.forEach((s) => {
    const li = document.createElement("li");
    li.textContent = s;
    suggestionsList.appendChild(li);
  });
}

// ---------------------------------------------------------------------
// Job search
// ---------------------------------------------------------------------

document.getElementById("search-btn").addEventListener("click", runJobSearch);
document.getElementById("sort-results").addEventListener("change", () => renderResults(latestResults));
document.querySelectorAll("#keywords-input, #location-input, #min-salary-input").forEach((input) => {
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter") runJobSearch();
  });
});

async function runJobSearch() {
  const keywords = document.getElementById("keywords-input").value.trim();
  const location = document.getElementById("location-input").value.trim();
  const minSalaryLpa = document.getElementById("min-salary-input").value;

  const searchStatus = document.getElementById("search-status");
  const resultsList = document.getElementById("results-list");

  if (!keywords) {
    searchStatus.textContent = "Enter a role or keyword to search.";
    searchStatus.className = "status-line error";
    return;
  }

  searchStatus.textContent = "Searching...";
  searchStatus.className = "status-line";
  resultsList.innerHTML = "";

  const requestBody = {
    keywords: selectedJobType === "all" ? keywords : `${keywords} ${selectedJobType}`,
    location: location || null,
    resumeId: currentResumeId, // null is fine, results just won't be scored
    minSalaryAnnualInr: minSalaryLpa ? parseFloat(minSalaryLpa) * 100000 : null,
    page: 1,
    resultsPerPage: 20,
  };

  try {
    const response = await fetch(`${API_BASE}/jobs/search`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(requestBody),
    });

    if (!response.ok) throw await responseError(response, "Search");

    const data = await response.json();
    searchStatus.textContent = `${data.totalFound} result(s) found (${data.duplicatesRemoved} duplicate(s) removed)`;
    searchStatus.className = "status-line success";
    latestResults = data.results || [];
    document.getElementById("results-toolbar").classList.toggle("hidden", latestResults.length === 0);
    document.getElementById("results-count").textContent = `${latestResults.length} roles to explore`;
    renderResults(latestResults);
  } catch (err) {
    searchStatus.textContent = `${err.message}. Check your Adzuna keys and backend connection.`;
    searchStatus.className = "status-line error";
    console.error(err);
  }
}

function renderResults(results) {
  const resultsList = document.getElementById("results-list");
  resultsList.innerHTML = "";

  const sortMode = document.getElementById("sort-results").value;
  const orderedResults = [...results].sort((first, second) => {
    if (sortMode === "fresh") return new Date(second.postedDate || 0) - new Date(first.postedDate || 0);
    if (sortMode === "salary") return String(second.salaryDisplay || "").localeCompare(String(first.salaryDisplay || ""));
    return (second.matchScore ?? -1) - (first.matchScore ?? -1);
  });

  if (orderedResults.length === 0) {
    resultsList.innerHTML = `<p class="status-line">No matching jobs found. Try broadening your search.</p>`;
    return;
  }

  orderedResults.forEach((job) => {
    const card = document.createElement("div");
    card.className = "job-card";
    const company = job.company || "Company";
    const companyMark = company.split(/\s+/).map((word) => word[0]).join("").slice(0, 2).toUpperCase();

    const matchScoreHtml = job.matchScore != null
      ? `<span><span class="match-label">match</span><span class="job-match-score">${Math.round(job.matchScore)}%</span></span>`
      : `<span class="match-label">fresh listing</span>`;

    const scamWarningHtml = job.scamRiskScore >= 0.5
      ? `<div class="scam-warning">Caution: this listing shows signs of ${job.scamFlags.join(", ")}.</div>`
      : "";

    const postedText = job.postedDate
      ? new Date(job.postedDate).toLocaleDateString()
      : "Date unknown";

    card.innerHTML = `
      <div class="job-card-top">
        <div class="job-identity">
          <span class="company-mark" aria-hidden="true">${escapeHtml(companyMark)}</span>
          <div>
          <div class="job-title">${escapeHtml(job.title)}</div>
          <div class="job-company">${escapeHtml(company)}</div>
          </div>
        </div>
        ${matchScoreHtml}
      </div>
      <div class="job-meta">
        <span>${escapeHtml(job.location || "Location not specified")}</span>
        <span>${job.salaryDisplay || "Salary not listed"}</span>
        <span>Posted ${postedText}</span>
      </div>
      ${scamWarningHtml}
      <details class="job-details">
        <summary>Why this role</summary>
        ${job.matchedSkills?.length ? `<div>${job.matchedSkills.map((skill) => `<span class="skill-chip">${escapeHtml(skill)}</span>`).join("")}</div>` : "<p>Upload a resume to see skill-level matching.</p>"}
        ${job.missingSkills?.length ? `<p>Missing: ${job.missingSkills.map(escapeHtml).join(", ")}</p>` : ""}
      </details>
      <a class="job-apply-link" href="${escapeHtml(job.applyUrl)}" target="_blank" rel="noopener">View and apply ↗</a>
    `;

    resultsList.appendChild(card);
  });
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str || "";
  return div.innerHTML;
}
