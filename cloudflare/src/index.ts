export interface Env {
  DB: D1Database;
  SAMPLES: R2Bucket;
  UPLOAD_TOKEN?: string;
  ALLOWED_ORIGIN?: string;
}

type CapturePayload = Record<string, unknown>;
type QueryValue = string | number;

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
};
const MAX_METADATA_BYTES = 128 * 1024;
const MAX_AUDIO_BYTES = 40 * 1024 * 1024;
const MAX_FEATURE_BYTES = 5 * 1024 * 1024;
const PUBLIC_UPLOAD_WINDOW_SECONDS = 60 * 60;
const PUBLIC_UPLOAD_MAX_REQUESTS_PER_WINDOW = 240;
const VALID_PHASES = new Set(["Idle", "Run-up", "Climb", "Cruise", "Descent"]);

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return withCors(new Response(null, { status: 204 }), env);
    }

    if (url.pathname === "/health" && request.method === "GET") {
      return json({ ok: true, service: "piston-listener-api" }, env);
    }

    if (url.pathname === "/v1/public/captures" && request.method === "GET") {
      return listCaptures(url, env, true);
    }

    if (url.pathname === "/v1/public/stats" && request.method === "GET") {
      return getStats(url, env, true);
    }

    const publicFileMatch = url.pathname.match(/^\/v1\/public\/captures\/([^/]+)\/(audio|features)$/);
    if (publicFileMatch && request.method === "GET") {
      return downloadCaptureFile(env, decodeURIComponent(publicFileMatch[1]), publicFileMatch[2] as "audio" | "features", true);
    }

    const publicCaptureMatch = url.pathname.match(/^\/v1\/public\/captures\/([^/]+)$/);
    if (publicCaptureMatch && request.method === "GET") {
      return getCapture(env, decodeURIComponent(publicCaptureMatch[1]), true);
    }

    if (url.pathname === "/v1/captures" && request.method === "POST") {
      const guard = await publicUploadGuard(request, env, "metadata");
      if (guard) {
        return guard;
      }
      return createCapture(request, env, true);
    }

    const fileMatch = url.pathname.match(/^\/v1\/captures\/([^/]+)\/(audio|features)$/);
    if (fileMatch && request.method === "PUT") {
      const kind = fileMatch[2] as "audio" | "features";
      const guard = await publicUploadGuard(request, env, kind);
      if (guard) {
        return guard;
      }
      return uploadCaptureFile(request, env, decodeURIComponent(fileMatch[1]), kind, true);
    }

    const authFailure = authorize(request, env);
    if (authFailure) {
      return authFailure;
    }

    if (url.pathname === "/v1/captures" && request.method === "GET") {
      return listCaptures(url, env);
    }

    if (url.pathname === "/v1/stats" && request.method === "GET") {
      return getStats(url, env);
    }

    if (fileMatch && request.method === "GET") {
      return downloadCaptureFile(env, decodeURIComponent(fileMatch[1]), fileMatch[2] as "audio" | "features");
    }

    const captureMatch = url.pathname.match(/^\/v1\/captures\/([^/]+)$/);
    if (captureMatch && request.method === "GET") {
      return getCapture(env, decodeURIComponent(captureMatch[1]));
    }

    if (captureMatch && request.method === "PATCH") {
      return updateCaptureReview(request, env, decodeURIComponent(captureMatch[1]));
    }

    return json({ error: "not found" }, env, 404);
  },
};

async function createCapture(request: Request, env: Env, publicUpload = false): Promise<Response> {
  let payload: CapturePayload;
  try {
    payload = (await request.json()) as CapturePayload;
  } catch {
    return json({ error: "invalid JSON" }, env, 400);
  }

  const captureId = stringField(payload, "captureId");
  if (!validCaptureId(captureId)) {
    return json({ error: "invalid captureId" }, env, 400);
  }
  if (publicUpload) {
    const validationError = validatePublicCapturePayload(payload);
    if (validationError) {
      return json({ error: validationError }, env, 400);
    }
  }

  const now = new Date().toISOString();
  await env.DB.prepare(
    `INSERT INTO captures (
      capture_id, started_at, received_at, updated_at, device_label, app_version,
      owner_id, device_id, visibility, uploaded_by_anonymous, moderation_status, engine, phase,
      tmoh_hours, known_issue_tags, known_issue_notes,
      duration_millis, frame_count, avg_rpm, avg_rms_dbfs, max_clip_percent,
      avg_dominant_hz, avg_centroid_hz, avg_band20_120, avg_band120_600,
      avg_band600_2500, avg_band2500_6000, avg_peak_dbfs, avg_crest_factor_db,
      max_flat_top_percent, signal_quality, accepted_for_trend, sample_rate,
      audio_file_name, features_file_name
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(capture_id) DO UPDATE SET
      started_at = excluded.started_at,
      updated_at = excluded.updated_at,
      device_label = excluded.device_label,
      app_version = excluded.app_version,
      owner_id = excluded.owner_id,
      device_id = excluded.device_id,
      visibility = excluded.visibility,
      uploaded_by_anonymous = excluded.uploaded_by_anonymous,
      moderation_status = excluded.moderation_status,
      engine = excluded.engine,
      phase = excluded.phase,
      tmoh_hours = excluded.tmoh_hours,
      known_issue_tags = excluded.known_issue_tags,
      known_issue_notes = excluded.known_issue_notes,
      duration_millis = excluded.duration_millis,
      frame_count = excluded.frame_count,
      avg_rpm = excluded.avg_rpm,
      avg_rms_dbfs = excluded.avg_rms_dbfs,
      max_clip_percent = excluded.max_clip_percent,
      avg_dominant_hz = excluded.avg_dominant_hz,
      avg_centroid_hz = excluded.avg_centroid_hz,
      avg_band20_120 = excluded.avg_band20_120,
      avg_band120_600 = excluded.avg_band120_600,
      avg_band600_2500 = excluded.avg_band600_2500,
      avg_band2500_6000 = excluded.avg_band2500_6000,
      avg_peak_dbfs = excluded.avg_peak_dbfs,
      avg_crest_factor_db = excluded.avg_crest_factor_db,
      max_flat_top_percent = excluded.max_flat_top_percent,
      signal_quality = excluded.signal_quality,
      accepted_for_trend = excluded.accepted_for_trend,
      sample_rate = excluded.sample_rate,
      audio_file_name = excluded.audio_file_name,
      features_file_name = excluded.features_file_name`
  )
    .bind(
      captureId,
      stringField(payload, "startedAt"),
      now,
      now,
      stringField(payload, "deviceLabel"),
      stringField(payload, "appVersion"),
      stringField(payload, "ownerId"),
      stringField(payload, "deviceId"),
      visibilityField(payload),
      publicUpload ? 1 : booleanField(payload, "uploadedByAnonymous") ? 1 : 0,
      moderationStatusField(payload, publicUpload),
      stringField(payload, "engine"),
      stringField(payload, "phase"),
      numberField(payload, "tmohHours"),
      stringField(payload, "knownIssueTags"),
      stringField(payload, "knownIssueNotes"),
      integerField(payload, "durationMillis"),
      integerField(payload, "frameCount"),
      numberField(payload, "avgRpm"),
      numberField(payload, "avgRmsDbfs"),
      numberField(payload, "maxClipPercent"),
      numberField(payload, "avgDominantHz"),
      numberField(payload, "avgCentroidHz"),
      numberField(payload, "avgBand20To120"),
      numberField(payload, "avgBand120To600"),
      numberField(payload, "avgBand600To2500"),
      numberField(payload, "avgBand2500To6000"),
      numberField(payload, "avgPeakDbfs"),
      numberField(payload, "avgCrestFactorDb"),
      numberField(payload, "maxFlatTopPercent"),
      stringField(payload, "signalQuality"),
      booleanField(payload, "acceptedForTrend") ? 1 : 0,
      integerField(payload, "sampleRate") || 48000,
      stringField(payload, "audioFileName"),
      stringField(payload, "featuresFileName")
    )
    .run();

  return json({ ok: true, captureId }, env, 201);
}

async function uploadCaptureFile(
  request: Request,
  env: Env,
  captureId: string,
  kind: "audio" | "features",
  publicUpload = false,
): Promise<Response> {
  if (!validCaptureId(captureId)) {
    return json({ error: "invalid captureId" }, env, 400);
  }
  if (publicUpload) {
    const existing = await env.DB.prepare("SELECT capture_id FROM captures WHERE capture_id = ?").bind(captureId).first();
    if (!existing) {
      return json({ error: "capture metadata must be created before file upload" }, env, 404);
    }
  }

  const body = await request.arrayBuffer();
  if (body.byteLength === 0) {
    return json({ error: "empty upload" }, env, 400);
  }

  const contentType = request.headers.get("content-type") || (kind === "audio" ? "audio/wav" : "text/csv");
  const key = `captures/${captureId}/${kind === "audio" ? "audio.wav" : "features.csv"}`;
  await env.SAMPLES.put(key, body, {
    httpMetadata: { contentType },
    customMetadata: { captureId, kind },
  });

  const now = new Date().toISOString();
  if (kind === "audio") {
    await env.DB.prepare(
      `UPDATE captures
       SET audio_r2_key = ?, audio_bytes = ?, audio_content_type = ?, updated_at = ?
       WHERE capture_id = ?`
    ).bind(key, body.byteLength, contentType, now, captureId).run();
  } else {
    await env.DB.prepare(
      `UPDATE captures
       SET feature_csv_r2_key = ?, feature_csv_bytes = ?, feature_csv_content_type = ?, updated_at = ?
       WHERE capture_id = ?`
    ).bind(key, body.byteLength, contentType, now, captureId).run();
  }

  return json({ ok: true, captureId, kind, key, bytes: body.byteLength }, env);
}

async function publicUploadGuard(
  request: Request,
  env: Env,
  kind: "metadata" | "audio" | "features",
): Promise<Response | null> {
  if (!["POST", "PUT"].includes(request.method)) {
    return json({ error: "method not allowed" }, env, 405);
  }

  const maxBytes = kind === "audio" ? MAX_AUDIO_BYTES : kind === "features" ? MAX_FEATURE_BYTES : MAX_METADATA_BYTES;
  const contentLength = Number(request.headers.get("content-length") || "0");
  if (!Number.isFinite(contentLength) || contentLength <= 0) {
    return json({ error: "missing content length" }, env, 411);
  }
  if (contentLength > maxBytes) {
    return json({ error: `${kind} upload too large` }, env, 413);
  }

  const contentType = (request.headers.get("content-type") || "").toLowerCase();
  if (kind === "metadata" && !contentType.includes("application/json")) {
    return json({ error: "metadata must be JSON" }, env, 415);
  }
  if (kind === "audio" && !contentType.includes("audio/") && !contentType.includes("octet-stream")) {
    return json({ error: "audio upload must be audio content" }, env, 415);
  }
  if (kind === "features" && !contentType.includes("text/") && !contentType.includes("csv") && !contentType.includes("octet-stream")) {
    return json({ error: "features upload must be CSV/text content" }, env, 415);
  }

  return enforcePublicUploadRateLimit(request, env);
}

async function enforcePublicUploadRateLimit(request: Request, env: Env): Promise<Response | null> {
  const ip = request.headers.get("cf-connecting-ip") || request.headers.get("x-forwarded-for") || "unknown";
  const nowMillis = Date.now();
  const bucket = Math.floor(nowMillis / (PUBLIC_UPLOAD_WINDOW_SECONDS * 1000));
  const reset = new Date((bucket + 1) * PUBLIC_UPLOAD_WINDOW_SECONDS * 1000).toISOString();
  const bucketKey = `public-upload:${ip.slice(0, 80)}:${bucket}`;

  await env.DB.prepare(
    `INSERT INTO upload_rate_limits (bucket_key, request_count, reset_at)
     VALUES (?, 1, ?)
     ON CONFLICT(bucket_key) DO UPDATE SET request_count = request_count + 1`
  ).bind(bucketKey, reset).run();

  const row = await env.DB.prepare(
    "SELECT request_count FROM upload_rate_limits WHERE bucket_key = ?"
  ).bind(bucketKey).first<{ request_count: number }>();

  if ((row?.request_count || 0) > PUBLIC_UPLOAD_MAX_REQUESTS_PER_WINDOW) {
    return json({ error: "public upload rate limit exceeded" }, env, 429);
  }

  if (Math.random() < 0.01) {
    await env.DB.prepare("DELETE FROM upload_rate_limits WHERE reset_at < ?")
      .bind(new Date(nowMillis - PUBLIC_UPLOAD_WINDOW_SECONDS * 1000).toISOString())
      .run();
  }
  return null;
}

function validatePublicCapturePayload(payload: CapturePayload): string {
  if (!stringField(payload, "deviceLabel")) {
    return "deviceLabel is required";
  }
  if (!stringField(payload, "appVersion")) {
    return "appVersion is required";
  }
  if (!stringField(payload, "engine")) {
    return "engine is required";
  }
  const phase = stringField(payload, "phase");
  if (!VALID_PHASES.has(phase)) {
    return "invalid phase";
  }
  const durationMillis = integerField(payload, "durationMillis");
  if (durationMillis < 5000 || durationMillis > 300000) {
    return "durationMillis out of range";
  }
  const frameCount = integerField(payload, "frameCount");
  if (frameCount < 1 || frameCount > 10000) {
    return "frameCount out of range";
  }
  const sampleRate = integerField(payload, "sampleRate") || 48000;
  if (sampleRate < 8000 || sampleRate > 192000) {
    return "sampleRate out of range";
  }
  if (!booleanField(payload, "acceptedForTrend")) {
    return "only accepted captures may upload anonymously";
  }
  return "";
}

async function listCaptures(url: URL, env: Env, publicRead = false): Promise<Response> {
  const rawLimit = Number(url.searchParams.get("limit") || "25");
  const limit = Math.max(1, Math.min(250, Number.isFinite(rawLimit) ? rawLimit : 25));
  const filters = captureFilters(url, publicRead);
  const result = await env.DB.prepare(
    `SELECT capture_id, started_at, received_at, updated_at, device_label, app_version,
       owner_id, device_id, visibility, uploaded_by_anonymous, moderation_status,
       engine, phase, tmoh_hours, known_issue_tags, duration_millis, frame_count,
       avg_rpm, avg_rms_dbfs, max_clip_percent, avg_dominant_hz, avg_centroid_hz,
       avg_band20_120, avg_band120_600, avg_band600_2500, avg_band2500_6000,
       avg_peak_dbfs, avg_crest_factor_db, max_flat_top_percent, signal_quality,
       accepted_for_trend, sample_rate, audio_bytes, feature_csv_bytes,
       review_status, analyst_notes, flagged
     FROM captures
     ${filters.where}
     ORDER BY started_at DESC
     LIMIT ?`
  ).bind(...filters.values, limit).all();
  return json({ ok: true, captures: result.results || [] }, env);
}

async function getStats(url: URL, env: Env, publicRead = false): Promise<Response> {
  const filters = captureFilters(url, publicRead);
  const totals = await env.DB.prepare(
    `SELECT
       COUNT(*) AS total_captures,
       SUM(CASE WHEN accepted_for_trend = 1 THEN 1 ELSE 0 END) AS accepted_captures,
       SUM(CASE WHEN flagged = 1 THEN 1 ELSE 0 END) AS flagged_captures,
       AVG(avg_rms_dbfs) AS avg_rms_dbfs,
       AVG(avg_centroid_hz) AS avg_centroid_hz,
       AVG(max_clip_percent) AS avg_clip_percent,
       MIN(started_at) AS first_started_at,
       MAX(started_at) AS last_started_at
     FROM captures
     ${filters.where}`
  ).bind(...filters.values).first();

  const byEnginePhase = await env.DB.prepare(
    `SELECT engine, phase, COUNT(*) AS captures, AVG(avg_rms_dbfs) AS avg_rms_dbfs,
       AVG(avg_centroid_hz) AS avg_centroid_hz, MAX(started_at) AS last_started_at
     FROM captures
     ${filters.where}
     GROUP BY engine, phase
     ORDER BY engine ASC, phase ASC`
  ).bind(...filters.values).all();

  const timeline = await env.DB.prepare(
    `SELECT capture_id, started_at, engine, phase, avg_rms_dbfs, avg_dominant_hz,
       avg_centroid_hz, max_clip_percent, avg_band20_120, avg_band120_600,
       avg_band600_2500, avg_band2500_6000, signal_quality, accepted_for_trend
     FROM captures
     ${filters.where}
     ORDER BY started_at ASC
     LIMIT 500`
  ).bind(...filters.values).all();

  return json({
    ok: true,
    totals: totals || {},
    byEnginePhase: byEnginePhase.results || [],
    timeline: timeline.results || [],
  }, env);
}

async function getCapture(env: Env, captureId: string, publicRead = false): Promise<Response> {
  if (!validCaptureId(captureId)) {
    return json({ error: "invalid captureId" }, env, 400);
  }
  const row = await env.DB.prepare(
    `SELECT * FROM captures
     WHERE capture_id = ?
     ${publicRead ? "AND visibility = 'public' AND moderation_status = 'approved'" : ""}`
  ).bind(captureId).first();
  if (!row) {
    return json({ error: "not found" }, env, 404);
  }
  return json({ ok: true, capture: row }, env);
}

async function updateCaptureReview(request: Request, env: Env, captureId: string): Promise<Response> {
  if (!validCaptureId(captureId)) {
    return json({ error: "invalid captureId" }, env, 400);
  }

  let payload: CapturePayload;
  try {
    payload = (await request.json()) as CapturePayload;
  } catch {
    return json({ error: "invalid JSON" }, env, 400);
  }

  const reviewStatus = stringField(payload, "reviewStatus").slice(0, 40);
  const analystNotes = stringField(payload, "analystNotes").slice(0, 5000);
  const flagged = booleanField(payload, "flagged") ? 1 : 0;
  const now = new Date().toISOString();

  const result = await env.DB.prepare(
    `UPDATE captures
     SET review_status = ?, analyst_notes = ?, flagged = ?, updated_at = ?
     WHERE capture_id = ?`
  ).bind(reviewStatus, analystNotes, flagged, now, captureId).run();

  if (result.meta.changes === 0) {
    return json({ error: "not found" }, env, 404);
  }

  return json({ ok: true, captureId, reviewStatus, flagged: flagged === 1 }, env);
}

async function downloadCaptureFile(env: Env, captureId: string, kind: "audio" | "features", publicRead = false): Promise<Response> {
  if (!validCaptureId(captureId)) {
    return json({ error: "invalid captureId" }, env, 400);
  }

  const column = kind === "audio" ? "audio_r2_key" : "feature_csv_r2_key";
  const contentColumn = kind === "audio" ? "audio_content_type" : "feature_csv_content_type";
  const row = await env.DB.prepare(
    `SELECT ${column} AS r2_key, ${contentColumn} AS content_type
     FROM captures
     WHERE capture_id = ?
     ${publicRead ? "AND visibility = 'public' AND moderation_status = 'approved'" : ""}`
  ).bind(captureId).first<{ r2_key?: string; content_type?: string }>();

  if (!row?.r2_key) {
    return json({ error: "file not found" }, env, 404);
  }

  const object = await env.SAMPLES.get(row.r2_key);
  if (!object) {
    return json({ error: "file not found" }, env, 404);
  }

  const extension = kind === "audio" ? "wav" : "csv";
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("content-type", row.content_type || (kind === "audio" ? "audio/wav" : "text/csv"));
  headers.set("content-disposition", `attachment; filename="${captureId}-${kind}.${extension}"`);
  return withCors(new Response(object.body, { headers }), env);
}

function captureFilters(url: URL, publicRead = false): { where: string; values: QueryValue[] } {
  const clauses: string[] = [];
  const values: QueryValue[] = [];
  if (publicRead) {
    clauses.push("visibility = 'public'");
    clauses.push("moderation_status = 'approved'");
  }
  addTextFilter(url, clauses, values, "engine", "engine");
  addTextFilter(url, clauses, values, "phase", "phase");
  addTextFilter(url, clauses, values, "quality", "signal_quality");
  addTextFilter(url, clauses, values, "review", "review_status");

  const flagged = url.searchParams.get("flagged");
  if (flagged === "true" || flagged === "1") {
    clauses.push("flagged = 1");
  } else if (flagged === "false" || flagged === "0") {
    clauses.push("flagged = 0");
  }

  const accepted = url.searchParams.get("accepted");
  if (accepted === "true" || accepted === "1") {
    clauses.push("accepted_for_trend = 1");
  } else if (accepted === "false" || accepted === "0") {
    clauses.push("accepted_for_trend = 0");
  }

  const from = url.searchParams.get("from");
  if (from) {
    clauses.push("started_at >= ?");
    values.push(from.slice(0, 40));
  }

  const to = url.searchParams.get("to");
  if (to) {
    clauses.push("started_at <= ?");
    values.push(to.slice(0, 40));
  }

  const search = url.searchParams.get("search");
  if (search) {
    clauses.push("(engine LIKE ? OR phase LIKE ? OR known_issue_tags LIKE ? OR device_label LIKE ?)");
    const value = `%${search.slice(0, 100)}%`;
    values.push(value, value, value, value);
  }

  return {
    where: clauses.length > 0 ? `WHERE ${clauses.join(" AND ")}` : "",
    values,
  };
}

function addTextFilter(url: URL, clauses: string[], values: QueryValue[], param: string, column: string): void {
  const value = url.searchParams.get(param);
  if (value) {
    clauses.push(`${column} = ?`);
    values.push(value.slice(0, 200));
  }
}

function authorize(request: Request, env: Env): Response | null {
  if (!env.UPLOAD_TOKEN || env.UPLOAD_TOKEN.length < 12) {
    return json({ error: "UPLOAD_TOKEN secret is not configured" }, env, 500);
  }

  const expected = `Bearer ${env.UPLOAD_TOKEN}`;
  const actual = request.headers.get("authorization") || "";
  if (!constantTimeEqual(actual, expected)) {
    return json({ error: "unauthorized" }, env, 401);
  }
  return null;
}

function stringField(payload: CapturePayload, key: string): string {
  const value = payload[key];
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).slice(0, 5000);
}

function numberField(payload: CapturePayload, key: string): number {
  const value = Number(payload[key]);
  return Number.isFinite(value) ? value : 0;
}

function integerField(payload: CapturePayload, key: string): number {
  return Math.trunc(numberField(payload, key));
}

function booleanField(payload: CapturePayload, key: string): boolean {
  return payload[key] === true || payload[key] === 1 || payload[key] === "true";
}

function visibilityField(payload: CapturePayload): string {
  return stringField(payload, "visibility") === "private" ? "private" : "public";
}

function moderationStatusField(payload: CapturePayload, publicUpload: boolean): string {
  if (publicUpload) {
    return "approved";
  }
  const value = stringField(payload, "moderationStatus");
  return value || "approved";
}

function validCaptureId(value: string): boolean {
  return /^[A-Za-z0-9_-]{8,80}$/.test(value);
}

function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) {
    return false;
  }
  let result = 0;
  for (let i = 0; i < a.length; i++) {
    result |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return result === 0;
}

function json(body: unknown, env: Env, status = 200): Response {
  return withCors(new Response(JSON.stringify(body), { status, headers: JSON_HEADERS }), env);
}

function withCors(response: Response, env: Env): Response {
  const headers = new Headers(response.headers);
  headers.set("access-control-allow-origin", env.ALLOWED_ORIGIN || "*");
  headers.set("access-control-allow-methods", "GET,POST,PUT,PATCH,OPTIONS");
  headers.set("access-control-allow-headers", "authorization,content-type");
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}
