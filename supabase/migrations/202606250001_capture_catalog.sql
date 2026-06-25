create extension if not exists pgcrypto;

create table if not exists public.captures (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid(),
  device_id text,
  recorded_at timestamptz not null,
  uploaded_at timestamptz,
  engine text not null,
  phase text not null,
  target_rpm numeric,
  duration_seconds integer,
  sample_rate integer not null,
  channels integer not null,
  bit_depth integer not null,
  phone_model text,
  android_version text,
  app_version text,
  mic_source text default 'phone',
  signal_quality text,
  avg_rms_dbfs numeric,
  peak_dbfs numeric,
  crest_factor_db numeric,
  max_clip_percent numeric,
  max_flat_top_percent numeric,
  avg_dominant_hz numeric,
  avg_centroid_hz numeric,
  avg_band20_120 numeric,
  avg_band120_600 numeric,
  avg_band600_2500 numeric,
  avg_band2500_6000 numeric,
  trend_score numeric,
  baseline_count integer,
  wav_path text,
  csv_path text,
  wav_size_bytes bigint,
  csv_size_bytes bigint,
  notes text,
  created_at timestamptz not null default now()
);

create table if not exists public.capture_feature_frames (
  id bigint generated always as identity primary key,
  capture_id uuid not null references public.captures(id) on delete cascade,
  elapsed_millis integer not null,
  rms_dbfs numeric,
  peak_dbfs numeric,
  crest_factor_db numeric,
  clipped_percent numeric,
  flat_top_percent numeric,
  dominant_hz numeric,
  centroid_hz numeric,
  band20_120 numeric,
  band120_600 numeric,
  band600_2500 numeric,
  band2500_6000 numeric,
  trend_score numeric,
  signal_quality text
);

create index if not exists captures_owner_recorded_idx on public.captures(owner_id, recorded_at desc);
create index if not exists captures_owner_engine_phase_idx on public.captures(owner_id, engine, phase, recorded_at desc);
create index if not exists capture_feature_frames_capture_idx on public.capture_feature_frames(capture_id, elapsed_millis);

alter table public.captures enable row level security;
alter table public.capture_feature_frames enable row level security;

drop policy if exists captures_select_own on public.captures;
drop policy if exists captures_insert_own on public.captures;
drop policy if exists captures_update_own on public.captures;
drop policy if exists captures_delete_own on public.captures;

create policy captures_select_own
  on public.captures
  for select
  to authenticated
  using (owner_id = auth.uid());

create policy captures_insert_own
  on public.captures
  for insert
  to authenticated
  with check (owner_id = auth.uid());

create policy captures_update_own
  on public.captures
  for update
  to authenticated
  using (owner_id = auth.uid())
  with check (owner_id = auth.uid());

create policy captures_delete_own
  on public.captures
  for delete
  to authenticated
  using (owner_id = auth.uid());

drop policy if exists capture_feature_frames_select_own on public.capture_feature_frames;
drop policy if exists capture_feature_frames_insert_own on public.capture_feature_frames;
drop policy if exists capture_feature_frames_update_own on public.capture_feature_frames;
drop policy if exists capture_feature_frames_delete_own on public.capture_feature_frames;

create policy capture_feature_frames_select_own
  on public.capture_feature_frames
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.captures
      where captures.id = capture_feature_frames.capture_id
        and captures.owner_id = auth.uid()
    )
  );

create policy capture_feature_frames_insert_own
  on public.capture_feature_frames
  for insert
  to authenticated
  with check (
    exists (
      select 1
      from public.captures
      where captures.id = capture_feature_frames.capture_id
        and captures.owner_id = auth.uid()
    )
  );

create policy capture_feature_frames_update_own
  on public.capture_feature_frames
  for update
  to authenticated
  using (
    exists (
      select 1
      from public.captures
      where captures.id = capture_feature_frames.capture_id
        and captures.owner_id = auth.uid()
    )
  )
  with check (
    exists (
      select 1
      from public.captures
      where captures.id = capture_feature_frames.capture_id
        and captures.owner_id = auth.uid()
    )
  );

create policy capture_feature_frames_delete_own
  on public.capture_feature_frames
  for delete
  to authenticated
  using (
    exists (
      select 1
      from public.captures
      where captures.id = capture_feature_frames.capture_id
        and captures.owner_id = auth.uid()
    )
  );

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'engine-samples',
  'engine-samples',
  false,
  52428800,
  array['audio/wav', 'audio/x-wav', 'text/csv', 'text/plain', 'application/octet-stream']
)
on conflict (id) do update
set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists engine_samples_select_own on storage.objects;
drop policy if exists engine_samples_insert_own on storage.objects;
drop policy if exists engine_samples_update_own on storage.objects;
drop policy if exists engine_samples_delete_own on storage.objects;

create policy engine_samples_select_own
  on storage.objects
  for select
  to authenticated
  using (
    bucket_id = 'engine-samples'
    and name like auth.uid()::text || '/%'
  );

create policy engine_samples_insert_own
  on storage.objects
  for insert
  to authenticated
  with check (
    bucket_id = 'engine-samples'
    and name like auth.uid()::text || '/%'
  );

create policy engine_samples_update_own
  on storage.objects
  for update
  to authenticated
  using (
    bucket_id = 'engine-samples'
    and name like auth.uid()::text || '/%'
  )
  with check (
    bucket_id = 'engine-samples'
    and name like auth.uid()::text || '/%'
  );

create policy engine_samples_delete_own
  on storage.objects
  for delete
  to authenticated
  using (
    bucket_id = 'engine-samples'
    and name like auth.uid()::text || '/%'
  );
