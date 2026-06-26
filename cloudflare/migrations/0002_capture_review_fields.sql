ALTER TABLE captures ADD COLUMN review_status TEXT NOT NULL DEFAULT '';
ALTER TABLE captures ADD COLUMN analyst_notes TEXT NOT NULL DEFAULT '';
ALTER TABLE captures ADD COLUMN flagged INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS captures_review_status_idx
  ON captures(review_status, flagged);
