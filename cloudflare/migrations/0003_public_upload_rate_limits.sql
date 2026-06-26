CREATE TABLE IF NOT EXISTS upload_rate_limits (
  bucket_key TEXT PRIMARY KEY,
  request_count INTEGER NOT NULL DEFAULT 0,
  reset_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS upload_rate_limits_reset_idx
  ON upload_rate_limits(reset_at);
