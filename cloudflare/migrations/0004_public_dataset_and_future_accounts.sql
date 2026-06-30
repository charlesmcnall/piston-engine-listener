ALTER TABLE captures ADD COLUMN owner_id TEXT;
ALTER TABLE captures ADD COLUMN device_id TEXT NOT NULL DEFAULT '';
ALTER TABLE captures ADD COLUMN visibility TEXT NOT NULL DEFAULT 'public';
ALTER TABLE captures ADD COLUMN uploaded_by_anonymous INTEGER NOT NULL DEFAULT 1;
ALTER TABLE captures ADD COLUMN moderation_status TEXT NOT NULL DEFAULT 'approved';
ALTER TABLE captures ADD COLUMN claimed_at TEXT;

CREATE INDEX IF NOT EXISTS captures_visibility_moderation_started_idx
  ON captures(visibility, moderation_status, started_at);

CREATE INDEX IF NOT EXISTS captures_owner_idx
  ON captures(owner_id);

CREATE INDEX IF NOT EXISTS captures_device_idx
  ON captures(device_id);
