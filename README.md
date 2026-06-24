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
- Engine selector with Jabiru 3300 default plus Jabiru 2200, Rotax 912, AeroVee/VW, and Custom.
- Per-phase target RPM settings.
- Signal-quality gate for peak level, clipping, crest factor, flat-top detection, and compression suspicion.
- Preflight checks for quiet cabin, idle, and run-up signal quality.
- Per-phase CSV session logs and summaries.
- Simple baseline score after three prior sessions for the same engine and phase.

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

## Install on a connected Android phone

Enable USB debugging on the phone, connect it, then run:

```powershell
.\scripts\install-debug.ps1
```

## Basic use

1. Open **Settings** and choose the engine. The default is Jabiru 3300.
2. Set the default capture duration. The default is 30 seconds.
3. Set target RPMs for each phase you plan to capture. Leave RPM blank or 0 if unknown.
4. Tap **Preflight** and run the quiet cabin, idle, and run-up checks before collecting trend data.
5. Put the phone in the same cabin location every time, with the mic unobstructed.
6. Tap a phase button once. The app records for the configured duration, stops automatically, and saves.
7. Use **Cancel Capture** only when the wrong phase or a bad setup was captured.
8. Re-record if the signal gate reports clipping, too quiet, or compression suspected.
9. Capture at least three good sessions for an engine and phase before treating the trend score as meaningful.

## Logged data

On-device data is written under the app-private `files/sessions` directory:

- `session-*.csv`: frame-level FFT features.
- `summary.csv`: one row per completed recording.

The app compares each phase against previous summaries for the same engine and phase. The baseline is considered ready after three accepted sessions. The `engine` column stores the selected engine tag, and the `rpm` column stores the configured target RPM for that phase. Captures with failed signal quality are not added to the baseline summary.

## Next build slice

- Export/share CSV files from the phone.
- Add WAV snippet capture for forensic review.
- Add phase-specific RPM normalization.
- Add a foreground recording service.
- Add a calibration screen for quiet cabin, idle, and run-up reference captures.
