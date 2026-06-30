# Cloudflare dashboard setup

The dashboard is a static website in `web/`. It talks to the existing Piston Listener Worker API. Normal browsing uses public read-only endpoints; the private bearer token is only needed for admin review edits.

## What it provides

- Capture table with engine, phase, signal quality, file sizes, and review state.
- Filters for engine, phase, signal quality, review status, and text search.
- Summary metrics for total captures, accepted captures, flagged captures, RMS, centroid, and clipping.
- Trend chart for RMS, centroid, dominant frequency, clipping, and band energy.
- Engine/phase group breakdown.
- Detail panel for each capture.
- Public WAV and CSV downloads from R2.
- Optional admin review status, flagged state, and analyst notes.
- A **Latest APK** link to `https://github.com/charlesmcnall/piston-engine-listener/releases/latest/download/app-debug.apk`.

## Backend migration

Apply D1 migrations before deploying the updated Worker:

```powershell
cd cloudflare
npm run d1:migrate
npm run deploy
```

Recent migrations add:

- `review_status`
- `analyst_notes`
- `flagged`
- `owner_id`
- `device_id`
- `visibility`
- `uploaded_by_anonymous`
- `moderation_status`
- `claimed_at`

## Local use

The website can be opened directly from:

```text
web\index.html
```

Enter:

- Worker API: `https://piston-listener-api.piston-listener.workers.dev`
- Admin token: optional private `UPLOAD_TOKEN` value from `cloudflare\.dev.vars`, only needed to save review edits

If entered, the admin token is stored in browser local storage on that computer only. Do not commit it to git or paste it into public screenshots.

## Cloudflare Pages deploy

Create a Pages project named `piston-listener-dashboard`, then deploy the `web/` directory:

```powershell
cd web
..\cloudflare\node_modules\.bin\wrangler.cmd pages project create piston-listener-dashboard --production-branch main
..\cloudflare\node_modules\.bin\wrangler.cmd pages deploy .
```

If the project already exists, only rerun the deploy command.

## Free-tier posture

The dashboard reads compact D1 metadata for normal browsing and only downloads WAV/CSV files when you explicitly press a download button. That keeps routine dashboard use small and avoids repeatedly pulling large R2 objects.
