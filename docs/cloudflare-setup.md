# Cloudflare Upload Setup

This setup uses:

- Cloudflare Workers for the upload API.
- Cloudflare D1 for capture metadata.
- Cloudflare R2 for WAV and CSV sample files.

The Android APK does not contain Cloudflare admin credentials. It stores the Worker URL and uploads accepted captures through the public, rate-limited app upload path. Dashboard browsing, review, and downloads still require the private Worker token.

## One-time Cloudflare setup

From the repo root:

```powershell
cd .\cloudflare
npm install
npx wrangler login
```

Create the D1 database:

```powershell
npm run d1:create
```

Wrangler prints a `database_id`. Copy that value into `cloudflare/wrangler.toml`, replacing:

```text
00000000-0000-0000-0000-000000000000
```

Create the R2 bucket:

```powershell
npm run r2:create
```

If Wrangler returns Cloudflare error `10042` saying R2 must be enabled, open:

```text
https://dash.cloudflare.com/<account-id>/r2/overview
```

Enable R2 in the dashboard, then rerun `npm run r2:create`.

Apply the D1 schema:

```powershell
npm run d1:migrate
```

Create a long random upload token and store it as a Worker secret:

```powershell
npm run secret:upload-token
```

Deploy the Worker:

```powershell
npm run deploy
```

If Wrangler says a `workers.dev` subdomain has not been registered yet, open:

```text
https://dash.cloudflare.com/?to=/:account/workers/onboarding
```

If that route does not land correctly, open the account directly:

```text
https://dash.cloudflare.com/<account-id>/workers/subdomain
```

Choose the account-wide Workers subdomain, then rerun `npm run deploy`. In the dashboard, this is usually under **Workers & Pages** and then the Workers subdomain/settings area.

The deploy prints a Worker URL similar to:

```text
https://piston-listener-api.<your-account>.workers.dev
```

Current deployed API URL:

```text
https://piston-listener-api.piston-listener.workers.dev
```

## Android app setup

1. Open **Settings** in Piston Listener.
2. Confirm **Auto upload accepted captures** is enabled.
3. Confirm the Worker API URL is prefilled as `https://piston-listener-api.piston-listener.workers.dev`.
4. Set a device label, such as `Pixel 8 left seat`.
5. Save settings, then tap **Sync**.

Accepted captures are saved locally first. If the phone is offline or upload fails, the pending upload files stay on the phone and can be retried with **Sync**.

Phone upload does not require a token. Public dashboard browsing and public WAV/CSV downloads also do not require a token. Keep the private `UPLOAD_TOKEN` for admin review edits and private API calls.

## API shape

The app uploads each capture in three public write calls:

```text
POST /v1/captures
PUT  /v1/captures/{captureId}/audio
PUT  /v1/captures/{captureId}/features
```

Public read-only comparison endpoints:

```text
GET /v1/public/stats
GET /v1/public/captures
GET /v1/public/captures/{captureId}
GET /v1/public/captures/{captureId}/audio
GET /v1/public/captures/{captureId}/features
```

Private/admin read and review endpoints still require:

```text
Authorization: Bearer <upload-token>
```

`GET /health` is public and only confirms that the Worker is running.

## Query examples

List recent captures:

```powershell
npx wrangler d1 execute piston-listener-catalog --remote --command "select capture_id, started_at, engine, phase, signal_quality, audio_bytes from captures order by started_at desc limit 10;"
```

Get one capture through the Worker:

```powershell
curl -H "Authorization: Bearer <upload-token>" https://piston-listener-api.<your-account>.workers.dev/v1/captures/<capture-id>
```

Get one public capture without a token:

```powershell
curl https://piston-listener-api.<your-account>.workers.dev/v1/public/captures/<capture-id>
```

## Notes

- A 30 second 48 kHz mono 16-bit WAV is about 2.9 MB.
- The app uploads only captures that pass the signal-quality gate.
- Public phone uploads are limited by request size, metadata validation, and per-IP rate limiting.
- Public reads only return captures with `visibility = public` and `moderation_status = approved`.
- The schema already includes nullable ownership fields for future user login, claiming, and owner-editable metadata workflows.
- The app keeps compact baseline summary rows locally, but prunes older detailed WAV/CSV files after they are uploaded or superseded by a newer local capture for the same engine and phase.
- Rejected captures are not queued for Cloudflare upload and may be removed by local retention. Cancelled captures delete the partial WAV.
- Keep the dashboard/admin upload token private. Rotate it with `npm run secret:upload-token` if it leaks.
