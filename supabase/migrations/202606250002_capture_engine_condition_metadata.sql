alter table public.captures
  add column if not exists tmoh_hours numeric,
  add column if not exists known_issue_tags text[] not null default '{}',
  add column if not exists known_issue_notes text;

create index if not exists captures_known_issue_tags_idx
  on public.captures
  using gin (known_issue_tags);
