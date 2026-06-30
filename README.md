# Piston Listener

Android prototype for piston-aircraft audio trend logging.

## Current scope

- Built-in phone microphone by default.
- 48 kHz mono PCM capture through `AudioRecord`.
- 4096-sample Hann-windowed FFT.
- Live spectrum bars from 0-6 kHz.
- Per-frame RMS, clipping, dominant frequency, centroid, and band ratios.
- One-tap phase buttons for Idle, Run-up, Climb, Cruise, and Descent.
- Auto-stop timed captures with a configurable default duration.
- Engine selector with Jabiru 3300 default plus common Jabiru, AeroVee/VW, Continental, Lycoming, Rotax, and Custom tags.
- Engine-condition metadata for TMOH hours, known issue tags, and issue notes.
- Per-phase target RPM settings.
- Signal-quality gate for peak level, clipping, crest factor, flat-top detection, and compression suspicion.
- Preflight checks for quiet cabin, idle, and run-up signal quality.
- Per-phase CSV session logs and summaries.
- WAV audio saved alongside completed captures for reprocessing and review.
- Simple baseline score after three prior sessions for the same engine and phase.
- Previous-capture comparison for the same engine and phase.
- Automatic local pruning that keeps compact baseline history, the latest detailed capture per engine/phase, and pending uploads.
- Optional Cloudflare upload queue for accepted captures.
- Static Cloudflare dashboard for browsing, filtering, charting, reviewing, and downloading uploaded captures.

This is advisory data collection software. It is not an approved engine monitor, maintenance release tool, or flight safety system.

## Build

Set up the local, repo-private Android toolchain first:

```powershell
.\scripts\setup-toolchain.ps1
```

The toolchain is installed under `.toolchain/` and is intentionally ignored by git.

```powershell
.\scripts\build-debug.ps1
```

The debug APK is written to:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Latest published debug APK:

```text
https://github.com/charlesmcnall/piston-engine-listener/releases/latest/download/app-debug.apk
```

## Install on a connected Android phone

Enable USB debugging on the phone, connect it, then run:

```powershell
.\scripts\install-debug.ps1
```

## Basic use

1. Open **Settings** and choose the engine. The default is Jabiru 3300.
2. Enter TMOH/TSMOH hours if known.
3. Tag any known engine issues, or leave **None known** selected.
4. Set the default capture duration. The default is 30 seconds.
5. Set target RPMs for each phase you plan to capture. Leave RPM blank or 0 if unknown.
6. Confirm Cloudflare sync is enabled and the Worker URL is prefilled. Accepted captures upload automatically; no phone-side upload token is required.
7. Tap **Preflight** and run the quiet cabin, idle, and run-up checks before collecting trend data.
8. Put the phone in the same cabin location every time, with the mic unobstructed.
9. Tap a phase button once. The app records for the configured duration, stops automatically, and saves.
10. Use **Cancel Capture** only when the wrong phase or a bad setup was captured.
11. Re-record if the signal gate reports clipping, too quiet, or compression suspected.
12. Capture at least three good sessions for an engine and phase before treating the trend score as meaningful.

## Logged data

On-device data is written under the app-private `files/sessions` directory:

- `session-*.csv`: frame-level FFT features.
- `session-*.wav`: raw 48 kHz mono 16-bit PCM audio in WAV format.
- `summary.csv`: one row per completed recording.

The app compares each phase against previous summaries for the same engine and phase. The baseline is considered ready after three accepted sessions. The `engine` column stores the selected engine tag, `tmohHours` stores time since major overhaul, `knownIssueTags` and `knownIssueNotes` store condition tags, and the `rpm` column stores the configured target RPM for that phase. Captures with failed signal quality are not added to the baseline summary.

To control phone storage, `summary.csv` is kept as the compact baseline history, while large per-capture WAV/CSV detail files are pruned. The phone keeps the latest accepted detailed capture for each engine and phase, plus any files still waiting for Cloudflare upload. Older detailed files are removed after they are no longer needed locally.

## Online catalog

Supabase setup files live in `supabase/`, with setup notes in `docs/supabase-setup.md`. The intended online model is a private `engine-samples` storage bucket for WAV/CSV files plus Postgres catalog rows in `public.captures`.

Cloudflare setup files live in `cloudflare/`, with setup notes in `docs/cloudflare-setup.md`. The Cloudflare path uses a Worker upload API, D1 metadata rows, and R2 objects for WAV/CSV files.

## Web dashboard

The dashboard lives in `web/` and can be opened directly from `web\index.html` or deployed to Cloudflare Pages. Setup notes are in `docs/cloudflare-dashboard-setup.md`.

The Android app uploads accepted captures anonymously through a size-limited and rate-limited public Worker path. The dashboard is public read-only for browsing, comparison, capture details, and WAV/CSV downloads. The private Worker token is only needed for admin review edits.

The live dashboard includes a **Latest APK** link that points to the newest GitHub Release asset.

## Next build slice

- Export/share CSV files from the phone.
- Add account login so users can claim uploads and edit their own metadata.
- Add automatic Supabase upload for accepted captures, if Supabase remains a target.
- Add phase-specific RPM normalization.
- Add a foreground recording service.
- Add a calibration screen for quiet cabin, idle, and run-up reference captures.
