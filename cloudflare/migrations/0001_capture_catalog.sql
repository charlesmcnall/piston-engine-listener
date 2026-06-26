CREATE TABLE IF NOT EXISTS captures (
  capture_id TEXT PRIMARY KEY,
  started_at TEXT NOT NULL,
  received_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  device_label TEXT NOT NULL DEFAULT '',
  app_version TEXT NOT NULL DEFAULT '',
  engine TEXT NOT NULL,
  phase TEXT NOT NULL,
  tmoh_hours REAL NOT NULL DEFAULT 0,
  known_issue_tags TEXT NOT NULL DEFAULT '',
  known_issue_notes TEXT NOT NULL DEFAULT '',
  duration_millis INTEGER NOT NULL DEFAULT 0,
  frame_count INTEGER NOT NULL DEFAULT 0,
  avg_rpm REAL NOT NULL DEFAULT 0,
  avg_rms_dbfs REAL NOT NULL DEFAULT 0,
  max_clip_percent REAL NOT NULL DEFAULT 0,
  avg_dominant_hz REAL NOT NULL DEFAULT 0,
  avg_centroid_hz REAL NOT NULL DEFAULT 0,
  avg_band20_120 REAL NOT NULL DEFAULT 0,
  avg_band120_600 REAL NOT NULL DEFAULT 0,
  avg_band600_2500 REAL NOT NULL DEFAULT 0,
  avg_band2500_6000 REAL NOT NULL DEFAULT 0,
  avg_peak_dbfs REAL NOT NULL DEFAULT 0,
  avg_crest_factor_db REAL NOT NULL DEFAULT 0,
  max_flat_top_percent REAL NOT NULL DEFAULT 0,
  signal_quality TEXT NOT NULL DEFAULT '',
  accepted_for_trend INTEGER NOT NULL DEFAULT 0,
  sample_rate INTEGER NOT NULL DEFAULT 48000,
  audio_file_name TEXT NOT NULL DEFAULT '',
  features_file_name TEXT NOT NULL DEFAULT '',
  audio_r2_key TEXT,
  audio_bytes INTEGER NOT NULL DEFAULT 0,
  audio_content_type TEXT NOT NULL DEFAULT '',
  feature_csv_r2_key TEXT,
  feature_csv_bytes INTEGER NOT NULL DEFAULT 0,
  feature_csv_content_type TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS captures_engine_phase_started_idx
  ON captures(engine, phase, started_at);

CREATE INDEX IF NOT EXISTS captures_signal_quality_idx
  ON captures(signal_quality);
