# Supabase Capture Catalog Setup

This project uses Supabase in two parts:

- Postgres stores searchable capture metadata.
- Supabase Storage stores the larger uploaded files, such as WAV audio and CSV feature logs.

## Create or Choose a Project

1. Open the Supabase dashboard.
2. Create a project named `piston-listener`, or choose an existing project.
3. Enable anonymous sign-ins in Authentication if you want the app to create a private upload identity without email/password.
4. Copy the project URL and anon public key from Project Settings > API.

## Apply the Schema

If the Supabase CLI is logged in:

```powershell
supabase login
supabase link --project-ref <your-project-ref>
supabase db push
```

If the CLI is not logged in, open the Supabase SQL Editor and run:

```text
supabase/migrations/202606250001_capture_catalog.sql
```

The migration creates:

- `public.captures`
- `public.capture_feature_frames`
- Private storage bucket `engine-samples`
- Row-level security policies so authenticated users can access only their own rows/files

Capture rows include engine-condition metadata such as `tmoh_hours`, `known_issue_tags`, and `known_issue_notes` so uploaded samples can be filtered by overhaul time and known defects.

## Storage Path Convention

The app should upload files under the authenticated user's ID:

```text
engine-samples/<user-id>/<capture-id>/capture.wav
engine-samples/<user-id>/<capture-id>/features.csv
```

The matching `captures` row stores those paths in `wav_path` and `csv_path`.

## Android App Wiring

The next app build needs:

1. Local WAV saving during each accepted capture.
2. A local upload queue.
3. Settings for Supabase URL, anon key, auto-upload, and Wi-Fi-only upload.
4. Anonymous Supabase sign-in or another authenticated account flow.
5. Background uploads through WorkManager.

For the first prototype, keep uploads private and use the default `engine-samples` bucket policies from the migration.
