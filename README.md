# Piston Listener

Android prototype for piston-aircraft audio trend logging.

## Current scope

- Built-in phone microphone by default.
- 48 kHz mono PCM capture through `AudioRecord`.
- 4096-sample Hann-windowed FFT.
- Live spectrum bars from 0-6 kHz.
- Per-frame RMS, clipping, dominant frequency, centroid, and band ratios.
- Per-phase CSV session logs and summaries.
- Simple baseline score after three prior sessions for the same phase.

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

## Logged data

On-device data is written under the app-private `files/sessions` directory:

- `session-*.csv`: frame-level FFT features.
- `summary.csv`: one row per completed recording.

The app compares each phase against previous summaries for the same phase. The baseline is considered ready after three saved sessions.

## Next build slice

- Export/share CSV files from the phone.
- Add WAV snippet capture for forensic review.
- Add phase-specific RPM normalization.
- Add a foreground recording service.
- Add a calibration screen for quiet cabin, idle, and run-up reference captures.
