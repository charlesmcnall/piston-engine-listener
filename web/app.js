const DEFAULT_API_URL = "https://piston-listener-api.piston-listener.workers.dev";
const STORAGE_KEY = "piston-listener-dashboard";

const state = {
  apiUrl: DEFAULT_API_URL,
  token: "",
  captures: [],
  stats: null,
  selectedCapture: null,
};

const els = {
  apiUrl: document.getElementById("apiUrl"),
  apiToken: document.getElementById("apiToken"),
  saveConnection: document.getElementById("saveConnection"),
  connectionStatus: document.getElementById("connectionStatus"),
  engineFilter: document.getElementById("engineFilter"),
  phaseFilter: document.getElementById("phaseFilter"),
  qualityFilter: document.getElementById("qualityFilter"),
  reviewFilter: document.getElementById("reviewFilter"),
  searchFilter: document.getElementById("searchFilter"),
  refreshButton: document.getElementById("refreshButton"),
  totalCaptures: document.getElementById("totalCaptures"),
  acceptedCaptures: document.getElementById("acceptedCaptures"),
  flaggedCaptures: document.getElementById("flaggedCaptures"),
  avgRms: document.getElementById("avgRms"),
  avgCentroid: document.getElementById("avgCentroid"),
  avgClip: document.getElementById("avgClip"),
  chartMetric: document.getElementById("chartMetric"),
  trendCanvas: document.getElementById("trendCanvas"),
  phaseBreakdown: document.getElementById("phaseBreakdown"),
  tableCount: document.getElementById("tableCount"),
  capturesTable: document.getElementById("capturesTable"),
  detailPanel: document.getElementById("detailPanel"),
  detailTitle: document.getElementById("detailTitle"),
  detailMeta: document.getElementById("detailMeta"),
  closeDetail: document.getElementById("closeDetail"),
  flaggedInput: document.getElementById("flaggedInput"),
  reviewStatusInput: document.getElementById("reviewStatusInput"),
  analystNotesInput: document.getElementById("analystNotesInput"),
  saveReview: document.getElementById("saveReview"),
  downloadAudio: document.getElementById("downloadAudio"),
  downloadFeatures: document.getElementById("downloadFeatures"),
};

loadConnection();
bindEvents();
renderEmptyState();
refreshDashboard();

function bindEvents() {
  els.saveConnection.addEventListener("click", () => {
    state.apiUrl = cleanUrl(els.apiUrl.value) || DEFAULT_API_URL;
    state.token = els.apiToken.value.trim();
    saveConnection();
    refreshDashboard();
  });

  for (const element of [els.engineFilter, els.phaseFilter, els.qualityFilter, els.reviewFilter]) {
    element.addEventListener("change", refreshDashboard);
  }

  els.searchFilter.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      refreshDashboard();
    }
  });

  els.refreshButton.addEventListener("click", refreshDashboard);
  els.chartMetric.addEventListener("change", () => renderTrendChart());
  els.closeDetail.addEventListener("click", closeDetail);
  els.saveReview.addEventListener("click", saveReview);
  els.downloadAudio.addEventListener("click", () => downloadFile("audio"));
  els.downloadFeatures.addEventListener("click", () => downloadFile("features"));
}

function loadConnection() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
    state.apiUrl = cleanUrl(saved.apiUrl) || DEFAULT_API_URL;
    state.token = typeof saved.token === "string" ? saved.token : "";
  } catch {
    state.apiUrl = DEFAULT_API_URL;
    state.token = "";
  }

  els.apiUrl.value = state.apiUrl;
  els.apiToken.value = state.token;
}

function saveConnection() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    apiUrl: state.apiUrl,
    token: state.token,
  }));
}

async function refreshDashboard() {
  state.apiUrl = cleanUrl(els.apiUrl.value) || DEFAULT_API_URL;
  state.token = els.apiToken.value.trim();
  saveConnection();

  setStatus("Loading", "warn");
  try {
    const query = filterQuery();
    const [stats, captures] = await Promise.all([
      apiGet(`/v1/public/stats${query}`),
      apiGet(`/v1/public/captures${query}${query ? "&" : "?"}limit=250`),
    ]);
    state.stats = stats;
    state.captures = captures.captures || [];
    populateFilterOptions();
    renderSummary();
    renderBreakdown();
    renderTrendChart();
    renderTable();
    setStatus(state.token ? "Public + admin" : "Public dataset", "good");
  } catch (error) {
    setStatus(shortError(error), "bad");
  }
}

function filterQuery() {
  const params = new URLSearchParams();
  if (els.engineFilter.value) params.set("engine", els.engineFilter.value);
  if (els.phaseFilter.value) params.set("phase", els.phaseFilter.value);
  if (els.qualityFilter.value) params.set("quality", els.qualityFilter.value);
  if (els.reviewFilter.value) params.set("review", els.reviewFilter.value);
  if (els.searchFilter.value.trim()) params.set("search", els.searchFilter.value.trim());
  const value = params.toString();
  return value ? `?${value}` : "";
}

async function apiGet(path) {
  const response = await fetch(`${state.apiUrl}${path}`);
  return parseResponse(response);
}

async function apiPatch(path, body) {
  const response = await fetch(`${state.apiUrl}${path}`, {
    method: "PATCH",
    headers: {
      ...authHeaders(),
      "content-type": "application/json",
    },
    body: JSON.stringify(body),
  });
  return parseResponse(response);
}

async function parseResponse(response) {
  const text = await response.text();
  const body = text ? JSON.parse(text) : {};
  if (!response.ok || body.ok === false) {
    throw new Error(body.error || `${response.status} ${response.statusText}`);
  }
  return body;
}

function authHeaders() {
  return state.token ? { authorization: `Bearer ${state.token}` } : {};
}

function populateFilterOptions() {
  const captures = state.captures;
  mergeOptions(els.engineFilter, captures.map((capture) => capture.engine).filter(Boolean), "All engines");
  mergeOptions(els.phaseFilter, captures.map((capture) => capture.phase).filter(Boolean), "All phases");
  mergeOptions(els.qualityFilter, captures.map((capture) => capture.signal_quality).filter(Boolean), "All quality");
}

function mergeOptions(select, values, allLabel) {
  const current = select.value;
  const unique = Array.from(new Set(values)).sort((a, b) => String(a).localeCompare(String(b)));
  select.replaceChildren(option("", allLabel), ...unique.map((value) => option(value, value)));
  if (unique.includes(current)) {
    select.value = current;
  }
}

function option(value, label) {
  const element = document.createElement("option");
  element.value = value;
  element.textContent = label;
  return element;
}

function renderSummary() {
  const totals = state.stats?.totals || {};
  els.totalCaptures.textContent = intText(totals.total_captures);
  els.acceptedCaptures.textContent = intText(totals.accepted_captures);
  els.flaggedCaptures.textContent = intText(totals.flagged_captures);
  els.avgRms.textContent = dbText(totals.avg_rms_dbfs);
  els.avgCentroid.textContent = hzText(totals.avg_centroid_hz);
  els.avgClip.textContent = percentText(totals.avg_clip_percent);
}

function renderBreakdown() {
  const rows = state.stats?.byEnginePhase || [];
  if (rows.length === 0) {
    els.phaseBreakdown.replaceChildren(emptyBlock("No phase groups yet"));
    return;
  }

  els.phaseBreakdown.replaceChildren(...rows.slice(0, 18).map((row) => {
    const element = document.createElement("div");
    element.className = "phase-row";
    const left = document.createElement("div");
    const title = document.createElement("strong");
    title.textContent = `${row.engine || "Unknown"} / ${row.phase || "Unknown"}`;
    const sub = document.createElement("span");
    sub.textContent = `${intText(row.captures)} captures, ${dbText(row.avg_rms_dbfs)} RMS, ${hzText(row.avg_centroid_hz)} centroid`;
    left.append(title, sub);
    const right = document.createElement("em");
    right.textContent = shortDate(row.last_started_at);
    element.append(left, right);
    return element;
  }));
}

function renderTrendChart() {
  const canvas = els.trendCanvas;
  const context = canvas.getContext("2d");
  const metric = els.chartMetric.value;
  const rows = (state.stats?.timeline || []).filter((row) => Number.isFinite(Number(row[metric])));
  const width = canvas.width;
  const height = canvas.height;
  context.clearRect(0, 0, width, height);
  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, width, height);

  const margin = { left: 62, right: 24, top: 22, bottom: 42 };
  drawAxes(context, width, height, margin);

  if (rows.length === 0) {
    drawCenteredText(context, "No trend data for this filter", width, height);
    return;
  }

  const values = rows.map((row) => Number(row[metric]));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const plotWidth = width - margin.left - margin.right;
  const plotHeight = height - margin.top - margin.bottom;

  context.strokeStyle = "#235789";
  context.lineWidth = 3;
  context.beginPath();
  rows.forEach((row, index) => {
    const x = margin.left + (rows.length === 1 ? plotWidth / 2 : (index / (rows.length - 1)) * plotWidth);
    const y = margin.top + plotHeight - ((Number(row[metric]) - min) / span) * plotHeight;
    if (index === 0) {
      context.moveTo(x, y);
    } else {
      context.lineTo(x, y);
    }
  });
  context.stroke();

  rows.forEach((row, index) => {
    const x = margin.left + (rows.length === 1 ? plotWidth / 2 : (index / (rows.length - 1)) * plotWidth);
    const y = margin.top + plotHeight - ((Number(row[metric]) - min) / span) * plotHeight;
    context.fillStyle = row.accepted_for_trend ? "#26735f" : "#93630d";
    context.beginPath();
    context.arc(x, y, 4, 0, Math.PI * 2);
    context.fill();
  });

  context.fillStyle = "#667085";
  context.font = "12px Arial";
  context.fillText(formatMetricValue(metric, max), 8, margin.top + 4);
  context.fillText(formatMetricValue(metric, min), 8, height - margin.bottom + 4);
}

function drawAxes(context, width, height, margin) {
  context.strokeStyle = "#d8dee8";
  context.lineWidth = 1;
  for (let i = 0; i <= 4; i++) {
    const y = margin.top + ((height - margin.top - margin.bottom) / 4) * i;
    context.beginPath();
    context.moveTo(margin.left, y);
    context.lineTo(width - margin.right, y);
    context.stroke();
  }
  context.strokeStyle = "#8b96a3";
  context.beginPath();
  context.moveTo(margin.left, margin.top);
  context.lineTo(margin.left, height - margin.bottom);
  context.lineTo(width - margin.right, height - margin.bottom);
  context.stroke();
}

function drawCenteredText(context, text, width, height) {
  context.fillStyle = "#667085";
  context.font = "15px Arial";
  context.textAlign = "center";
  context.fillText(text, width / 2, height / 2);
  context.textAlign = "left";
}

function renderTable() {
  els.tableCount.textContent = `${state.captures.length} rows`;
  if (state.captures.length === 0) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = 8;
    cell.textContent = "No captures match the current filters.";
    row.append(cell);
    els.capturesTable.replaceChildren(row);
    return;
  }

  els.capturesTable.replaceChildren(...state.captures.map((capture) => {
    const row = document.createElement("tr");
    row.append(
      cell(shortDateTime(capture.started_at)),
      cell(capture.engine),
      cell(capture.phase),
      pillCell(capture.signal_quality),
      cell(dbText(capture.avg_rms_dbfs)),
      cell(hzText(capture.avg_centroid_hz)),
      cell(percentText(capture.max_clip_percent)),
      pillCell(reviewLabel(capture), capture.flagged ? "review" : ""),
    );
    row.addEventListener("click", () => openDetail(capture.capture_id));
    return row;
  }));
}

async function openDetail(captureId) {
  try {
    const body = await apiGet(`/v1/public/captures/${encodeURIComponent(captureId)}`);
    state.selectedCapture = body.capture;
    renderDetail();
  } catch (error) {
    setStatus(shortError(error), "bad");
  }
}

function renderDetail() {
  const capture = state.selectedCapture;
  if (!capture) return;
  els.detailTitle.textContent = `${capture.engine || "Engine"} / ${capture.phase || "Phase"}`;
  els.detailMeta.replaceChildren(
    detailItem("Capture ID", capture.capture_id),
    detailItem("Started", shortDateTime(capture.started_at)),
    detailItem("Device", capture.device_label),
    detailItem("App", capture.app_version),
    detailItem("TMOH", numberText(capture.tmoh_hours, 1)),
    detailItem("Quality", capture.signal_quality),
    detailItem("RMS", dbText(capture.avg_rms_dbfs)),
    detailItem("Dominant", hzText(capture.avg_dominant_hz)),
    detailItem("Centroid", hzText(capture.avg_centroid_hz)),
    detailItem("Clip", percentText(capture.max_clip_percent)),
    detailItem("Bands", bandText(capture)),
    detailItem("Files", `${bytesText(capture.audio_bytes)} WAV / ${bytesText(capture.feature_csv_bytes)} CSV`),
    detailItem("Known Issues", capture.known_issue_tags || "None known"),
    detailItem("Issue Notes", capture.known_issue_notes || ""),
  );
  els.flaggedInput.checked = Boolean(capture.flagged);
  els.reviewStatusInput.value = capture.review_status || "";
  els.analystNotesInput.value = capture.analyst_notes || "";
  els.detailPanel.classList.add("open");
  els.detailPanel.setAttribute("aria-hidden", "false");
}

function closeDetail() {
  els.detailPanel.classList.remove("open");
  els.detailPanel.setAttribute("aria-hidden", "true");
}

async function saveReview() {
  const capture = state.selectedCapture;
  if (!capture) return;
  if (!state.token) {
    setStatus("Admin token needed", "warn");
    return;
  }
  try {
    await apiPatch(`/v1/captures/${encodeURIComponent(capture.capture_id)}`, {
      flagged: els.flaggedInput.checked,
      reviewStatus: els.reviewStatusInput.value,
      analystNotes: els.analystNotesInput.value,
    });
    setStatus("Review saved", "good");
    closeDetail();
    refreshDashboard();
  } catch (error) {
    setStatus(shortError(error), "bad");
  }
}

async function downloadFile(kind) {
  const capture = state.selectedCapture;
  if (!capture) return;
  try {
    const response = await fetch(`${state.apiUrl}/v1/public/captures/${encodeURIComponent(capture.capture_id)}/${kind}`);
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || `${response.status} ${response.statusText}`);
    }
    const blob = await response.blob();
    const extension = kind === "audio" ? "wav" : "csv";
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `${capture.capture_id}-${kind}.${extension}`;
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(link.href), 1000);
  } catch (error) {
    setStatus(shortError(error), "bad");
  }
}

function cell(value) {
  const element = document.createElement("td");
  element.textContent = value === null || value === undefined || value === "" ? "--" : String(value);
  return element;
}

function pillCell(value, forcedClass = "") {
  const element = document.createElement("td");
  const pill = document.createElement("span");
  pill.className = `pill ${forcedClass || qualityClass(value)}`;
  pill.textContent = value || "--";
  element.append(pill);
  return element;
}

function detailItem(label, value) {
  const item = document.createElement("div");
  item.className = "detail-item";
  const labelElement = document.createElement("span");
  labelElement.textContent = label;
  const valueElement = document.createElement("strong");
  valueElement.textContent = value === null || value === undefined || value === "" ? "--" : String(value);
  item.append(labelElement, valueElement);
  return item;
}

function emptyBlock(text) {
  const element = document.createElement("div");
  element.className = "phase-row";
  const strong = document.createElement("strong");
  strong.textContent = text;
  element.append(strong);
  return element;
}

function setStatus(text, level) {
  els.connectionStatus.textContent = text;
  els.connectionStatus.style.background = level === "good" ? "#26735f" : level === "bad" ? "#a33b3b" : "#93630d";
}

function renderEmptyState() {
  state.captures = [];
  state.stats = null;
  renderSummary();
  renderBreakdown();
  renderTrendChart();
  renderTable();
}

function qualityClass(value) {
  const text = String(value || "").toLowerCase();
  if (text.includes("good")) return "good";
  if (text.includes("clip") || text.includes("too quiet") || text.includes("compression")) return "bad";
  return "warn";
}

function reviewLabel(capture) {
  if (capture.flagged) return "Flagged";
  return capture.review_status || "Unreviewed";
}

function formatMetricValue(metric, value) {
  if (metric.includes("hz")) return hzText(value);
  if (metric.includes("percent")) return percentText(value);
  if (metric.includes("dbfs")) return dbText(value);
  return numberText(value, 3);
}

function bandText(capture) {
  return [
    numberText(capture.avg_band20_120, 3),
    numberText(capture.avg_band120_600, 3),
    numberText(capture.avg_band600_2500, 3),
    numberText(capture.avg_band2500_6000, 3),
  ].join(" / ");
}

function intText(value) {
  return Number.isFinite(Number(value)) ? String(Math.trunc(Number(value))) : "--";
}

function numberText(value, digits = 1) {
  return Number.isFinite(Number(value)) ? Number(value).toFixed(digits) : "--";
}

function dbText(value) {
  return Number.isFinite(Number(value)) ? `${Number(value).toFixed(1)} dB` : "--";
}

function hzText(value) {
  return Number.isFinite(Number(value)) ? `${Math.round(Number(value))} Hz` : "--";
}

function percentText(value) {
  return Number.isFinite(Number(value)) ? `${Number(value).toFixed(2)}%` : "--";
}

function bytesText(value) {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) return "none";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function shortDate(value) {
  if (!value) return "--";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value).slice(0, 10) : date.toLocaleDateString();
}

function shortDateTime(value) {
  if (!value) return "--";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

function cleanUrl(value) {
  return String(value || "").trim().replace(/\/+$/, "");
}

function shortError(error) {
  return String(error?.message || error || "Request failed").slice(0, 80);
}
