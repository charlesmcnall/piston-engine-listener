package com.chaz.pistonlistener;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 7001;
    private static final String PREFS_NAME = "capture_settings";
    private static final String KEY_CAPTURE_SECONDS = "capture_seconds";
    private static final String KEY_ENGINE_OPTION = "engine_option";
    private static final String KEY_CUSTOM_ENGINE = "custom_engine";
    private static final String KEY_TMOH_HOURS = "tmoh_hours";
    private static final String KEY_KNOWN_ISSUE_TAGS = "known_issue_tags";
    private static final String KEY_KNOWN_ISSUE_NOTES = "known_issue_notes";
    private static final String KEY_CLOUDFLARE_ENABLED = "cloudflare_enabled";
    private static final String KEY_CLOUDFLARE_URL = "cloudflare_url";
    private static final String KEY_DEVICE_LABEL = "device_label";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final int DEFAULT_CAPTURE_SECONDS = 30;
    private static final String DEFAULT_CLOUDFLARE_URL = "https://piston-listener-api.piston-listener.workers.dev";
    private static final int MIN_CAPTURE_SECONDS = 5;
    private static final int MAX_CAPTURE_SECONDS = 300;
    private static final int PREFLIGHT_CAPTURE_SECONDS = 8;
    private static final String DEFAULT_ENGINE = "Jabiru 3300";
    private static final String CUSTOM_ENGINE_OPTION = "Custom";
    private static final String[] ENGINE_OPTIONS = new String[]{
            DEFAULT_ENGINE,
            "Jabiru 2200",
            "AeroVee/VW",
            "Continental A-65",
            "Continental C-85",
            "Continental C-90",
            "Continental O-200",
            "Continental O-300",
            "Continental IO-360",
            "Continental O-470",
            "Continental IO-470",
            "Continental IO-520",
            "Continental TSIO-520",
            "Continental IO-550",
            "Continental TSIO-550",
            "Lycoming O-235",
            "Lycoming O-290",
            "Lycoming O-320",
            "Lycoming O-360",
            "Lycoming IO-360",
            "Lycoming AEIO-360",
            "Lycoming O-540",
            "Lycoming IO-540",
            "Lycoming AEIO-540",
            "Lycoming TIO-540",
            "Rotax 447",
            "Rotax 503",
            "Rotax 582",
            "Rotax 912",
            "Rotax 912 iS",
            "Rotax 914",
            "Rotax 915 iS",
            "Rotax 916 iS",
            CUSTOM_ENGINE_OPTION
    };
    private static final String NO_KNOWN_ISSUES = "None known";
    private static final String[] KNOWN_ISSUE_OPTIONS = new String[]{
            NO_KNOWN_ISSUES,
            "Oil leak or high oil consumption",
            "Low compression",
            "Cylinder or valve concern",
            "Ignition or magneto issue",
            "Carburetor or fuel issue",
            "Cooling or CHT concern",
            "Exhaust leak",
            "Rough running or vibration",
            "Gearbox or propeller drive concern"
    };
    private static final String[] PHASES = new String[]{"Idle", "Run-up", "Climb", "Cruise", "Descent"};
    private static final String[] PREFLIGHT_STEPS = new String[]{"Quiet cabin", "Idle", "Run-up"};
    private static final String USER_INSTRUCTIONS =
            "1. Install\n"
                    + "Download the APK from the GitHub release, open it on the Android phone, allow install unknown apps if prompted, then launch Piston Listener. Grant microphone permission.\n\n"
                    + "2. Configure Targets\n"
                    + "Open Settings, choose the engine, enter TMOH hours, tag any known engine issues, set the default capture time, and enter target RPM for each phase. Leave RPM blank or 0 if you do not know it yet.\n\n"
                    + "Optional Cloudflare Sync\n"
                    + "The Worker API URL and auto upload are set by default. Accepted captures are queued locally first and uploaded when the phone has a network connection. No upload credential is needed on the phone.\n\n"
                    + "3. Run Preflight\n"
                    + "Tap Preflight before collecting trend data. The app checks quiet cabin, idle, and run-up levels and reports Good, Too quiet, Clipping, or Compression suspected.\n\n"
                    + "4. Place the Phone Consistently\n"
                    + "Put the phone in the same cabin location every time, with the mic unobstructed. Do not use Bluetooth audio. Consistent placement matters more than perfect placement.\n\n"
                    + "5. Capture a Stable Engine Phase\n"
                    + "Tap Idle, Run-up, Climb, Cruise, or Descent once. The app records for the configured duration, stops automatically, and saves the session. Keep the engine stable during the countdown.\n\n"
                    + "6. Watch Signal Quality\n"
                    + "If the app shows Input clipping, that recording is not useful. Move the phone farther from loud vents, speakers, or panels and try again. Clipping means the mic is overloaded.\n\n"
                    + "7. Build a Baseline\n"
                    + "The app needs 3 saved sessions per phase before the trend score becomes useful. Example: do three good Idle recordings before trusting Idle trend changes.\n\n"
                    + "8. Read the Screen\n"
                    + "Engine: the tag saved with new captures.\n"
                    + "TMOH: time since major overhaul hours saved with new captures.\n"
                    + "Issues: known issue tags saved with new captures.\n"
                    + "Signal: the current quality gate result.\n"
                    + "RMS: overall loudness.\n"
                    + "Clipping: overloaded audio percentage. Should stay near zero.\n"
                    + "Peak/Crest: peak level and headroom shape.\n"
                    + "Dominant: strongest frequency.\n"
                    + "Centroid: center of spectral energy.\n"
                    + "Bands: energy split across frequency ranges.\n"
                    + "Baseline: how many prior sessions exist for the active phase.\n"
                    + "Trend: rough change score versus previous recordings for that phase.\n"
                    + "Previous: rough change score versus the last accepted capture for the same engine and phase.\n\n"
                    + "Local Storage\n"
                    + "The app keeps compact baseline history and the latest detailed capture for each engine and phase. Older WAV/CSV detail files are pruned after upload or when a newer local comparison file replaces them.\n\n"
                    + "9. Interpret Carefully\n"
                    + "A high trend score means this sounds different than the baseline, not that the engine is failing. Use it as a prompt to inspect, compare with engine monitor data, or ask a mechanic.\n\n"
                    + "10. Safety\n"
                    + "This is advisory trend-logging software only. It is not an approved engine monitor, maintenance tool, or flight safety system. Use normal aircraft instruments and maintenance procedures first.";

    private final EngineAudioRecorder recorder = new EngineAudioRecorder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Button> phaseButtons = new HashMap<>();
    private final SignalQualityGate qualityGate = new SignalQualityGate();

    private Button preflightButton;
    private Button cancelButton;
    private SpectrumView spectrumView;
    private TextView captureText;
    private TextView statusText;
    private TextView engineText;
    private TextView tmohText;
    private TextView issueText;
    private TextView signalText;
    private TextView rmsText;
    private TextView clipText;
    private TextView peakText;
    private TextView dominantText;
    private TextView centroidText;
    private TextView bandsText;
    private TextView trendText;
    private TextView previousText;
    private TextView baselineText;
    private TextView syncText;

    private SessionLogger logger;
    private volatile WavFileWriter wavWriter;
    private volatile boolean audioWriteFailed = false;
    private volatile String audioWriteError = "";
    private SessionLogger.Baseline baseline = SessionLogger.Baseline.empty();
    private String activePhase = "Idle";
    private String activeEngineTag = DEFAULT_ENGINE;
    private double activeTmohHours = 0.0;
    private String activeKnownIssueTags = NO_KNOWN_ISSUES;
    private String activeKnownIssueNotes = "";
    private double activeTargetRpm = 0.0;
    private int activeCaptureSeconds = DEFAULT_CAPTURE_SECONDS;
    private long captureEndsAtMillis = 0L;
    private String sourceStatus = "";
    private boolean captureCompleting = false;
    private boolean calibrationCapture = false;
    private boolean preflightMode = false;
    private int preflightStepIndex = 0;
    private StringBuilder preflightReport = new StringBuilder();

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        updatePermissionState();
        if (uploadEnabled()) {
            startCloudflareSync();
        }
    }

    @Override
    protected void onDestroy() {
        finishCapture(false);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            updatePermissionState();
        }
    }

    private void buildUi() {
        activeEngineTag = currentEngineTag();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(248, 250, 252));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(20));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("Piston Listener", 26, Color.rgb(15, 23, 42), Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView subtitle = text("One-tap engine phase captures", 14, Color.rgb(71, 85, 105), Typeface.NORMAL);
        subtitle.setPadding(0, dp(2), 0, dp(14));
        content.addView(subtitle, matchWrap());

        LinearLayout utilityRow = new LinearLayout(this);
        utilityRow.setOrientation(LinearLayout.HORIZONTAL);
        utilityRow.setGravity(Gravity.CENTER_VERTICAL);
        utilityRow.setPadding(0, 0, 0, dp(12));
        content.addView(utilityRow, matchWrap());

        Button readMeButton = new Button(this);
        readMeButton.setText("Read Me");
        readMeButton.setOnClickListener(view -> showInstructions());
        utilityRow.addView(readMeButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        Button settingsButton = new Button(this);
        settingsButton.setText("Settings");
        settingsButton.setOnClickListener(view -> showSettings());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        settingsParams.setMargins(dp(10), 0, 0, 0);
        utilityRow.addView(settingsButton, settingsParams);

        preflightButton = new Button(this);
        preflightButton.setText("Preflight");
        preflightButton.setOnClickListener(view -> startPreflight());
        LinearLayout.LayoutParams preflightParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        preflightParams.setMargins(dp(10), 0, 0, 0);
        utilityRow.addView(preflightButton, preflightParams);

        Button syncButton = new Button(this);
        syncButton.setText("Sync");
        syncButton.setOnClickListener(view -> startCloudflareSync());
        LinearLayout.LayoutParams syncParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        syncParams.setMargins(dp(10), 0, 0, 0);
        utilityRow.addView(syncButton, syncParams);

        captureText = text("Tap a phase to start a timed capture.", 15, Color.rgb(15, 23, 42), Typeface.BOLD);
        captureText.setPadding(0, 0, 0, dp(10));
        content.addView(captureText, matchWrap());

        LinearLayout phaseGrid = new LinearLayout(this);
        phaseGrid.setOrientation(LinearLayout.VERTICAL);
        content.addView(phaseGrid, matchWrap());

        for (int i = 0; i < PHASES.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.setMargins(0, 0, 0, dp(8));
            phaseGrid.addView(row, rowParams);

            addPhaseButton(row, PHASES[i], false);
            if (i + 1 < PHASES.length) {
                addPhaseButton(row, PHASES[i + 1], true);
            }
        }

        cancelButton = new Button(this);
        cancelButton.setText("Cancel Capture");
        cancelButton.setEnabled(false);
        cancelButton.setOnClickListener(view -> finishCapture(false));
        content.addView(cancelButton, matchWrap());

        spectrumView = new SpectrumView(this);
        LinearLayout.LayoutParams spectrumParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(190)
        );
        spectrumParams.setMargins(0, dp(14), 0, dp(14));
        content.addView(spectrumView, spectrumParams);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.VERTICAL);
        content.addView(metrics, matchWrap());

        engineText = metric("Engine", currentEngineTag());
        tmohText = metric("TMOH", tmohLabel(currentTmohHours()));
        issueText = metric("Issues", currentKnownIssueTags());
        signalText = metric("Signal", "--");
        rmsText = metric("RMS", "--");
        clipText = metric("Clipping", "--");
        peakText = metric("Peak/Crest", "--");
        dominantText = metric("Dominant", "--");
        centroidText = metric("Centroid", "--");
        bandsText = metric("Bands", "--");
        trendText = metric("Trend", "--");
        previousText = metric("Previous", "--");
        baselineText = metric("Baseline", "--");
        syncText = metric("Sync", "--");

        metrics.addView(engineText, matchWrap());
        metrics.addView(tmohText, matchWrap());
        metrics.addView(issueText, matchWrap());
        metrics.addView(signalText, matchWrap());
        metrics.addView(rmsText, matchWrap());
        metrics.addView(clipText, matchWrap());
        metrics.addView(peakText, matchWrap());
        metrics.addView(dominantText, matchWrap());
        metrics.addView(centroidText, matchWrap());
        metrics.addView(bandsText, matchWrap());
        metrics.addView(trendText, matchWrap());
        metrics.addView(previousText, matchWrap());
        metrics.addView(baselineText, matchWrap());
        metrics.addView(syncText, matchWrap());

        statusText = text("Ready", 14, Color.rgb(51, 65, 85), Typeface.NORMAL);
        statusText.setPadding(0, dp(12), 0, 0);
        content.addView(statusText, matchWrap());

        setContentView(scrollView);
        refreshPhaseButtons();
        refreshMetadataLabels();
        refreshBaselineLabel();
        refreshSyncLabel();
    }

    private void addPhaseButton(LinearLayout row, String phase, boolean withLeftMargin) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setMinHeight(dp(72));
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(view -> startPhaseCapture(phase));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(82), 1.0f);
        if (withLeftMargin) {
            params.setMargins(dp(10), 0, 0, 0);
        }
        row.addView(button, params);
        phaseButtons.put(phase, button);
    }

    private void startPhaseCapture(String phase) {
        startTimedCapture(phase, getCaptureSeconds(), false);
    }

    private void startPreflight() {
        if (recorder.isRunning()) {
            return;
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        preflightMode = true;
        preflightStepIndex = 0;
        preflightReport = new StringBuilder();
        statusText.setText("Starting preflight");
        startTimedCapture(PREFLIGHT_STEPS[preflightStepIndex], PREFLIGHT_CAPTURE_SECONDS, true);
    }

    private void startTimedCapture(String phase, int durationSeconds, boolean calibration) {
        if (recorder.isRunning()) {
            return;
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activePhase = phase;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        calibrationCapture = calibration;
        activePhase = phase;
        activeEngineTag = currentEngineTag();
        activeTmohHours = currentTmohHours();
        activeKnownIssueTags = currentKnownIssueTags();
        activeKnownIssueNotes = currentKnownIssueNotes();
        activeTargetRpm = targetRpm(phase);
        activeCaptureSeconds = durationSeconds;
        baseline = calibrationCapture ? SessionLogger.Baseline.empty() : SessionLogger.loadBaseline(this, activePhase, activeEngineTag);
        logger = calibrationCapture ? null : new SessionLogger();
        wavWriter = null;
        audioWriteFailed = false;
        audioWriteError = "";
        qualityGate.reset();
        captureCompleting = false;
        sourceStatus = "";

        if (logger != null) {
            WavFileWriter writer = null;
            try {
                logger.start(this, activePhase, activeEngineTag, activeTmohHours, activeKnownIssueTags, activeKnownIssueNotes);
                writer = new WavFileWriter(logger.getAudioFile());
                writer.start();
                wavWriter = writer;
            } catch (IOException exception) {
                statusText.setText("Log start failed: " + exception.getMessage());
                logger = null;
                if (writer != null) {
                    writer.abort();
                }
                wavWriter = null;
                return;
            }
        }

        setPhaseButtonsEnabled(false);
        if (preflightButton != null) {
            preflightButton.setEnabled(false);
        }
        cancelButton.setEnabled(true);
        statusText.setText((calibrationCapture ? "Preflight " : "Recording ") + activePhase);
        captureEndsAtMillis = System.currentTimeMillis() + activeCaptureSeconds * 1000L;

        recorder.start(new EngineAudioRecorder.Listener() {
            @Override
            public void onPcm(short[] samples, int sampleCount) {
                WavFileWriter writer = wavWriter;
                if (writer == null) {
                    return;
                }
                try {
                    writer.append(samples, sampleCount);
                } catch (IOException exception) {
                    audioWriteFailed = true;
                    audioWriteError = exception.getMessage();
                    handler.post(() -> statusText.setText("Audio write failed: " + exception.getMessage()));
                }
            }

            @Override
            public void onFeatures(SpectrumFeatures rawFeatures) {
                double trend = baseline.score(rawFeatures);
                SpectrumFeatures features = rawFeatures.withContext(
                        activePhase,
                        activeTargetRpm,
                        activeEngineTag,
                        activeTmohHours,
                        activeKnownIssueTags,
                        activeKnownIssueNotes,
                        trend
                );
                qualityGate.add(features);
                updateMetrics(features);
                if (logger != null) {
                    try {
                        logger.append(features);
                    } catch (IOException exception) {
                        statusText.setText("Log write failed: " + exception.getMessage());
                    }
                }
            }

            @Override
            public void onStatus(String status) {
                sourceStatus = status;
                if (recorder.isRunning()) {
                    statusText.setText((calibrationCapture ? "Preflight " : "Recording ") + activePhase + " - " + sourceStatus);
                }
            }

            @Override
            public void onError(String message) {
                statusText.setText(message);
                finishCapture(false);
            }
        });
        updateCountdown();
    }

    private void finishCapture(boolean completed) {
        if (captureCompleting) {
            return;
        }
        captureCompleting = true;
        handler.removeCallbacks(countdownRunnable);

        boolean hadCapture = recorder.isRunning() || logger != null;
        recorder.stop();

        WavFileWriter finishedWriter = wavWriter;
        wavWriter = null;
        boolean audioFailed = audioWriteFailed;
        String audioError = audioWriteError;
        if (finishedWriter != null) {
            if (completed) {
                try {
                    finishedWriter.finish();
                } catch (IOException exception) {
                    audioFailed = true;
                    audioError = exception.getMessage();
                }
            } else {
                finishedWriter.abort();
            }
        }

        SignalQualityGate.Snapshot signalQuality = qualityGate.snapshot();
        String sessionName = "";
        boolean rejected = false;
        boolean summaryFailed = false;
        String summaryError = "";
        CaptureSummary completedSummary = null;
        boolean uploadQueued = false;
        if (logger != null && completed) {
            sessionName = logger.getSessionFileName();
            if (signalQuality.acceptableForTrend) {
                try {
                    completedSummary = logger.finish(signalQuality);
                } catch (IOException exception) {
                    summaryFailed = true;
                    summaryError = exception.getMessage();
                }
            } else {
                rejected = true;
            }
        }
        logger = null;
        if (completedSummary != null && !summaryFailed && !audioFailed) {
            uploadQueued = queueCaptureForUpload(completedSummary);
        }
        CaptureRetention.Result retention = CaptureRetention.prune(this);
        String retentionNote = retention.label();
        baseline = calibrationCapture ? SessionLogger.Baseline.empty() : SessionLogger.loadBaseline(this, activePhase, activeEngineTag);

        if (calibrationCapture && completed) {
            appendPreflightResult(activePhase, signalQuality);
            calibrationCapture = false;
            preflightStepIndex++;
            captureCompleting = false;

            if (preflightMode && preflightStepIndex < PREFLIGHT_STEPS.length) {
                handler.postDelayed(() -> startTimedCapture(PREFLIGHT_STEPS[preflightStepIndex], PREFLIGHT_CAPTURE_SECONDS, true), 700L);
                return;
            }

            preflightMode = false;
            showPreflightResults();
        }

        if (!completed) {
            preflightMode = false;
            calibrationCapture = false;
        }

        if (cancelButton != null) {
            cancelButton.setEnabled(false);
        }
        setPhaseButtonsEnabled(true);
        if (preflightButton != null) {
            preflightButton.setEnabled(true);
        }
        refreshPhaseButtons();
        refreshBaselineLabel();

        if (captureText != null) {
            captureText.setText("Tap a phase to start a timed capture.");
        }

        if (statusText != null && hadCapture) {
            if (completed && audioFailed) {
                statusText.setText("Audio write failed: " + audioError);
            } else if (completed && summaryFailed) {
                statusText.setText("Summary write failed: " + summaryError);
            } else if (completed && rejected) {
                statusText.setText("Rejected " + sessionName + " - " + signalQuality.label);
            } else if (completed && !sessionName.isEmpty()) {
                String status = uploadQueued ? "Saved and queued " + sessionName : "Saved locally " + sessionName;
                if (!retentionNote.isEmpty()) {
                    status += " - " + retentionNote;
                }
                statusText.setText(status);
            } else if (!completed) {
                statusText.setText("Capture cancelled");
            }
        }

        captureCompleting = false;
    }

    private void appendPreflightResult(String step, SignalQualityGate.Snapshot signalQuality) {
        if (preflightReport.length() > 0) {
            preflightReport.append("\n\n");
        }
        preflightReport
                .append(step)
                .append(": ")
                .append(signalQuality.label)
                .append("\n")
                .append(signalQuality.detail);
    }

    private void showPreflightResults() {
        TextView resultText = text(preflightReport.toString(), 15, Color.rgb(15, 23, 42), Typeface.NORMAL);
        resultText.setPadding(dp(18), dp(12), dp(18), dp(4));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(resultText);

        new AlertDialog.Builder(this)
                .setTitle("Preflight Results")
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .show();
        statusText.setText("Preflight complete");
    }

    private void updateCountdown() {
        if (!recorder.isRunning()) {
            return;
        }

        long remainingMillis = captureEndsAtMillis - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            finishCapture(true);
            return;
        }

        long remainingSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        Button activeButton = phaseButtons.get(activePhase);
        if (activeButton != null) {
            activeButton.setText(activePhase + "\n" + remainingSeconds + "s remaining");
        }

        if (calibrationCapture) {
            captureText.setText(String.format(
                    Locale.US,
                    "Preflight %s: %ds remaining",
                    activePhase,
                    remainingSeconds
            ));
        } else {
            captureText.setText(String.format(
                    Locale.US,
                    "%s capture: %ds remaining - target %s",
                    activePhase,
                    remainingSeconds,
                    targetRpmLabel(activeTargetRpm)
            ));
        }
        handler.postDelayed(countdownRunnable, 250L);
    }

    private void updateMetrics(SpectrumFeatures features) {
        SignalQualityGate.Snapshot frameQuality = SignalQualityGate.classifyFrame(features);
        SignalQualityGate.Snapshot sessionQuality = qualityGate.snapshot();

        spectrumView.setSpectrum(features.spectrum);
        signalText.setText("Signal   " + sessionQuality.label + " - " + frameQuality.label);
        rmsText.setText(formatMetric("RMS", "%.1f dBFS", features.rmsDbfs));
        clipText.setText(formatMetric("Clipping", "%.3f %%", features.clippedPercent));
        peakText.setText(String.format(
                Locale.US,
                "Peak/Crest   %.1f dBFS / %.1f dB / flat %.3f %%",
                features.peakDbfs,
                features.crestFactorDb,
                features.flatTopPercent
        ));
        dominantText.setText(formatMetric("Dominant", "%.1f Hz", features.dominantHz));
        centroidText.setText(formatMetric("Centroid", "%.1f Hz", features.centroidHz));
        bandsText.setText(String.format(
                Locale.US,
                "Bands   %.2f / %.2f / %.2f / %.2f",
                features.band20To120,
                features.band120To600,
                features.band600To2500,
                features.band2500To6000
        ));

        if (!baseline.isReady()) {
            trendText.setText("Trend   collecting");
        } else {
            trendText.setText(formatMetric("Trend", "%.1f", features.trendScore));
        }
        if (!baseline.hasPrevious()) {
            previousText.setText("Previous   none");
        } else {
            previousText.setText(formatMetric("Previous", "%.1f", baseline.previousScore(features)));
        }

        if (frameQuality.severity >= 3) {
            statusText.setText("Signal: " + frameQuality.label);
        } else if (frameQuality.severity >= 2) {
            statusText.setText("Signal: " + frameQuality.label);
        } else if (frameQuality.severity >= 1) {
            statusText.setText("Signal: " + frameQuality.label);
        } else if (!calibrationCapture && baseline.isReady() && features.trendScore > 20.0) {
            statusText.setText("Spectral shift");
        } else if (recorder.isRunning()) {
            statusText.setText((calibrationCapture ? "Preflight " : "Recording ") + activePhase + (sourceStatus.isEmpty() ? "" : " - " + sourceStatus));
        }
    }

    private void showInstructions() {
        TextView instructions = text(USER_INSTRUCTIONS, 15, Color.rgb(15, 23, 42), Typeface.NORMAL);
        instructions.setPadding(dp(18), dp(12), dp(18), dp(4));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(instructions);

        new AlertDialog.Builder(this)
                .setTitle("Basic User Instructions")
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showSettings() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        TextView engineLabel = text("Engine", 14, Color.rgb(51, 65, 85), Typeface.BOLD);
        form.addView(engineLabel, matchWrap());

        Spinner engineSpinner = new Spinner(this);
        ArrayAdapter<String> engineAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ENGINE_OPTIONS);
        engineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        engineSpinner.setAdapter(engineAdapter);
        engineSpinner.setSelection(engineOptionIndex(selectedEngineOption()));
        form.addView(engineSpinner, matchWrap());

        TextView customEngineLabel = text("Custom engine name", 14, Color.rgb(51, 65, 85), Typeface.BOLD);
        customEngineLabel.setPadding(0, dp(10), 0, 0);
        form.addView(customEngineLabel, matchWrap());

        EditText customEngineInput = textInput(customEngineName());
        boolean customSelected = CUSTOM_ENGINE_OPTION.equals(selectedEngineOption());
        customEngineInput.setEnabled(customSelected);
        customEngineInput.setAlpha(customSelected ? 1.0f : 0.45f);
        form.addView(customEngineInput, matchWrap());

        engineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean enabled = CUSTOM_ENGINE_OPTION.equals(ENGINE_OPTIONS[position]);
                customEngineInput.setEnabled(enabled);
                customEngineInput.setAlpha(enabled ? 1.0f : 0.45f);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                customEngineInput.setEnabled(false);
                customEngineInput.setAlpha(0.45f);
            }
        });

        TextView tmohLabel = text("TMOH / TSMOH hours", 14, Color.rgb(51, 65, 85), Typeface.BOLD);
        tmohLabel.setPadding(0, dp(14), 0, 0);
        form.addView(tmohLabel, matchWrap());

        EditText tmohInput = numberInput(currentTmohHours() <= 0.0 ? "" : String.format(Locale.US, "%.1f", currentTmohHours()));
        form.addView(tmohInput, matchWrap());

        TextView issueLabel = text("Known engine issues", 14, Color.rgb(51, 65, 85), Typeface.BOLD);
        issueLabel.setPadding(0, dp(14), 0, 0);
        form.addView(issueLabel, matchWrap());

        Set<String> selectedIssues = selectedIssueSet(currentKnownIssueTags());
        Map<String, CheckBox> issueInputs = new HashMap<>();
        for (String issue : KNOWN_ISSUE_OPTIONS) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(issue);
            checkBox.setTextSize(14);
            checkBox.setTextColor(Color.rgb(15, 23, 42));
            checkBox.setChecked(selectedIssues.contains(issue));
            form.addView(checkBox, matchWrap());
            issueInputs.put(issue, checkBox);
        }

        TextView issueNotesLabel = text("Known issue notes", 14, Color.rgb(51, 65, 85), Typeface.BOLD);
        issueNotesLabel.setPadding(0, dp(10), 0, 0);
        form.addView(issueNotesLabel, matchWrap());

        EditText issueNotesInput = multiLineInput(currentKnownIssueNotes());
        form.addView(issueNotesInput, matchWrap());

        TextView durationLabel = text("Default capture time, seconds", 14, Color.rgb(51, 65, 85), Typeface.BOLD);
        durationLabel.setPadding(0, dp(14), 0, 0);
        form.addView(durationLabel, matchWrap());

        EditText durationInput = numberInput(String.valueOf(getCaptureSeconds()));
        form.addView(durationInput, matchWrap());

        Map<String, EditText> rpmInputs = new HashMap<>();
        for (String phase : PHASES) {
            TextView label = text(phase + " target RPM", 14, Color.rgb(51, 65, 85), Typeface.BOLD);
            label.setPadding(0, dp(10), 0, 0);
            form.addView(label, matchWrap());

            double rpm = targetRpm(phase);
            EditText input = numberInput(rpm <= 0.0 ? "" : String.format(Locale.US, "%.0f", rpm));
            form.addView(input, matchWrap());
            rpmInputs.put(phase, input);
        }

        TextView cloudflareLabel = text("Cloudflare sync", 14, Color.rgb(51, 65, 85), Typeface.BOLD);
        cloudflareLabel.setPadding(0, dp(16), 0, 0);
        form.addView(cloudflareLabel, matchWrap());

        CheckBox cloudflareEnabledInput = new CheckBox(this);
        cloudflareEnabledInput.setText("Auto upload accepted captures");
        cloudflareEnabledInput.setTextSize(14);
        cloudflareEnabledInput.setTextColor(Color.rgb(15, 23, 42));
        cloudflareEnabledInput.setChecked(uploadEnabled());
        form.addView(cloudflareEnabledInput, matchWrap());

        TextView deviceLabel = text("Device label", 14, Color.rgb(51, 65, 85), Typeface.BOLD);
        deviceLabel.setPadding(0, dp(10), 0, 0);
        form.addView(deviceLabel, matchWrap());

        EditText deviceLabelInput = textInput(currentDeviceLabel());
        form.addView(deviceLabelInput, matchWrap());

        TextView cloudflareUrlLabel = text("Worker API URL", 14, Color.rgb(51, 65, 85), Typeface.BOLD);
        cloudflareUrlLabel.setPadding(0, dp(10), 0, 0);
        form.addView(cloudflareUrlLabel, matchWrap());

        EditText cloudflareUrlInput = urlInput(currentCloudflareUrl());
        form.addView(cloudflareUrlInput, matchWrap());

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(form);

        new AlertDialog.Builder(this)
                .setTitle("Capture Settings")
                .setView(scrollView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    saveSettings(
                            durationInput,
                            rpmInputs,
                            engineSpinner,
                            customEngineInput,
                            tmohInput,
                            issueInputs,
                            issueNotesInput,
                            cloudflareEnabledInput,
                            cloudflareUrlInput,
                            deviceLabelInput
                    );
                    refreshPhaseButtons();
                    refreshMetadataLabels();
                    refreshSyncLabel();
                    activeEngineTag = currentEngineTag();
                    activeTmohHours = currentTmohHours();
                    activeKnownIssueTags = currentKnownIssueTags();
                    activeKnownIssueNotes = currentKnownIssueNotes();
                    baseline = SessionLogger.loadBaseline(this, activePhase, activeEngineTag);
                    refreshBaselineLabel();
                    statusText.setText("Settings saved");
                    if (uploadEnabled()) {
                        startCloudflareSync();
                    }
                })
                .show();
    }

    private void saveSettings(
            EditText durationInput,
            Map<String, EditText> rpmInputs,
            Spinner engineSpinner,
            EditText customEngineInput,
            EditText tmohInput,
            Map<String, CheckBox> issueInputs,
            EditText issueNotesInput,
            CheckBox cloudflareEnabledInput,
            EditText cloudflareUrlInput,
            EditText deviceLabelInput
    ) {
        SharedPreferences.Editor editor = prefs().edit();
        int seconds = clamp(parseInt(durationInput.getText().toString(), DEFAULT_CAPTURE_SECONDS), MIN_CAPTURE_SECONDS, MAX_CAPTURE_SECONDS);
        editor.putInt(KEY_CAPTURE_SECONDS, seconds);

        Object selected = engineSpinner.getSelectedItem();
        String engineOption = selected instanceof String ? (String) selected : DEFAULT_ENGINE;
        if (!isEngineOption(engineOption)) {
            engineOption = DEFAULT_ENGINE;
        }
        editor.putString(KEY_ENGINE_OPTION, engineOption);
        editor.putString(KEY_CUSTOM_ENGINE, cleanCustomEngineName(customEngineInput.getText().toString()));
        double tmohHours = parseDouble(tmohInput.getText().toString(), 0.0);
        editor.putFloat(KEY_TMOH_HOURS, (float) Math.max(0.0, Math.min(100000.0, tmohHours)));
        editor.putString(KEY_KNOWN_ISSUE_TAGS, knownIssueTagsForSave(issueInputs));
        editor.putString(KEY_KNOWN_ISSUE_NOTES, cleanOptionalText(issueNotesInput.getText().toString()));
        editor.putBoolean(KEY_CLOUDFLARE_ENABLED, cloudflareEnabledInput.isChecked());
        editor.putString(KEY_CLOUDFLARE_URL, cleanUrl(cloudflareUrlInput.getText().toString()));
        editor.putString(KEY_DEVICE_LABEL, cleanOptionalText(deviceLabelInput.getText().toString()));

        for (String phase : PHASES) {
            EditText input = rpmInputs.get(phase);
            double rpm = input == null ? 0.0 : parseDouble(input.getText().toString(), 0.0);
            editor.putFloat(targetRpmKey(phase), (float) Math.max(0.0, Math.min(5000.0, rpm)));
        }

        editor.apply();
    }

    private void updatePermissionState() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            setPhaseButtonsEnabled(true);
            statusText.setText("Ready");
        } else {
            setPhaseButtonsEnabled(true);
            statusText.setText("Microphone permission");
        }
    }

    private void setPhaseButtonsEnabled(boolean enabled) {
        for (Button button : phaseButtons.values()) {
            button.setEnabled(enabled);
        }
    }

    private void refreshPhaseButtons() {
        int seconds = getCaptureSeconds();
        for (String phase : PHASES) {
            Button button = phaseButtons.get(phase);
            if (button != null && !recorder.isRunning()) {
                button.setText(phase + "\n" + seconds + "s - " + targetRpmLabel(targetRpm(phase)));
            }
        }
    }

    private void refreshMetadataLabels() {
        if (engineText != null) {
            engineText.setText("Engine   " + currentEngineTag());
        }
        if (tmohText != null) {
            tmohText.setText("TMOH   " + tmohLabel(currentTmohHours()));
        }
        if (issueText != null) {
            issueText.setText("Issues   " + currentKnownIssueTags());
        }
    }

    private void refreshBaselineLabel() {
        if (baseline.isReady()) {
            baselineText.setText(String.format(Locale.US, "Baseline   %s %s %d sessions", activeEngineTag, activePhase, baseline.count));
        } else {
            baselineText.setText(String.format(Locale.US, "Baseline   %s %s %d / 3", activeEngineTag, activePhase, baseline.count));
        }
    }

    private void refreshSyncLabel() {
        if (syncText == null) {
            return;
        }

        int pending = new UploadQueueDatabase(this).pendingCount();
        if (!uploadEnabled()) {
            syncText.setText(String.format(Locale.US, "Sync   off, %d queued", pending));
        } else if (!cloudflareConfig().isReady()) {
            syncText.setText(String.format(Locale.US, "Sync   needs URL, %d queued", pending));
        } else {
            syncText.setText(String.format(Locale.US, "Sync   ready, %d queued", pending));
        }
    }

    private boolean queueCaptureForUpload(CaptureSummary summary) {
        if (!uploadEnabled() || !cloudflareConfig().isReady()) {
            refreshSyncLabel();
            return false;
        }

        new UploadQueueDatabase(this).enqueue(summary, currentDeviceLabel(), BuildConfig.VERSION_NAME, currentDeviceId());
        refreshSyncLabel();
        startCloudflareSync();
        return true;
    }

    private void startCloudflareSync() {
        refreshSyncLabel();
        if (!uploadEnabled()) {
            statusText.setText("Cloudflare sync off");
            return;
        }

        CloudflareUploader.Config config = cloudflareConfig();
        if (!config.isReady()) {
            statusText.setText("Cloudflare needs URL");
            return;
        }

        CloudflareUploader.uploadPending(this, config, status -> {
            if (syncText != null) {
                syncText.setText("Sync   " + status);
            }
            if (statusText != null) {
                statusText.setText(status);
            }
        });
    }

    private CloudflareUploader.Config cloudflareConfig() {
        return new CloudflareUploader.Config(currentCloudflareUrl(), "");
    }

    private int getCaptureSeconds() {
        return prefs().getInt(KEY_CAPTURE_SECONDS, DEFAULT_CAPTURE_SECONDS);
    }

    private double targetRpm(String phase) {
        return prefs().getFloat(targetRpmKey(phase), 0.0f);
    }

    private String targetRpmLabel(double rpm) {
        if (rpm <= 0.0) {
            return "RPM not set";
        }
        return String.format(Locale.US, "%.0f RPM", rpm);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private String targetRpmKey(String phase) {
        return "target_rpm_" + phase.toLowerCase(Locale.US).replace("-", "_").replace(" ", "_");
    }

    private String selectedEngineOption() {
        String engineOption = prefs().getString(KEY_ENGINE_OPTION, DEFAULT_ENGINE);
        return isEngineOption(engineOption) ? engineOption : DEFAULT_ENGINE;
    }

    private String customEngineName() {
        return cleanCustomEngineName(prefs().getString(KEY_CUSTOM_ENGINE, ""));
    }

    private String currentEngineTag() {
        String engineOption = selectedEngineOption();
        if (!CUSTOM_ENGINE_OPTION.equals(engineOption)) {
            return engineOption;
        }

        String customName = customEngineName();
        return customName.isEmpty() || "Unknown".equals(customName) ? CUSTOM_ENGINE_OPTION : customName;
    }

    private double currentTmohHours() {
        return prefs().getFloat(KEY_TMOH_HOURS, 0.0f);
    }

    private boolean uploadEnabled() {
        return prefs().getBoolean(KEY_CLOUDFLARE_ENABLED, true);
    }

    private String currentCloudflareUrl() {
        return cleanUrl(prefs().getString(KEY_CLOUDFLARE_URL, DEFAULT_CLOUDFLARE_URL));
    }

    private String currentDeviceLabel() {
        String label = cleanOptionalText(prefs().getString(KEY_DEVICE_LABEL, ""));
        if (!label.isEmpty()) {
            return label;
        }
        return cleanOptionalText(Build.MANUFACTURER + " " + Build.MODEL);
    }

    private String currentDeviceId() {
        String deviceId = cleanOptionalText(prefs().getString(KEY_DEVICE_ID, ""));
        if (!deviceId.isEmpty()) {
            return deviceId;
        }
        String generated = UUID.randomUUID().toString();
        prefs().edit().putString(KEY_DEVICE_ID, generated).apply();
        return generated;
    }

    private String tmohLabel(double hours) {
        if (hours <= 0.0) {
            return "not set";
        }
        return String.format(Locale.US, "%.1f hr", hours);
    }

    private String currentKnownIssueTags() {
        String tags = prefs().getString(KEY_KNOWN_ISSUE_TAGS, NO_KNOWN_ISSUES);
        tags = SpectrumFeatures.sanitizeCsv(tags);
        return tags.isEmpty() || "Unknown".equals(tags) ? NO_KNOWN_ISSUES : tags;
    }

    private String currentKnownIssueNotes() {
        return cleanOptionalText(prefs().getString(KEY_KNOWN_ISSUE_NOTES, ""));
    }

    private Set<String> selectedIssueSet(String tags) {
        Set<String> selected = new HashSet<>();
        if (tags == null || tags.trim().isEmpty()) {
            selected.add(NO_KNOWN_ISSUES);
            return selected;
        }

        String[] parts = tags.split(";");
        for (String part : parts) {
            String clean = cleanOptionalText(part);
            if (!clean.isEmpty()) {
                selected.add(clean);
            }
        }

        if (selected.isEmpty()) {
            selected.add(NO_KNOWN_ISSUES);
        }
        return selected;
    }

    private String knownIssueTagsForSave(Map<String, CheckBox> issueInputs) {
        StringBuilder selected = new StringBuilder();
        boolean hasSpecificIssue = false;

        for (String issue : KNOWN_ISSUE_OPTIONS) {
            if (NO_KNOWN_ISSUES.equals(issue)) {
                continue;
            }
            CheckBox checkBox = issueInputs.get(issue);
            if (checkBox != null && checkBox.isChecked()) {
                if (selected.length() > 0) {
                    selected.append("; ");
                }
                selected.append(issue);
                hasSpecificIssue = true;
            }
        }

        if (hasSpecificIssue) {
            return selected.toString();
        }

        CheckBox noneKnown = issueInputs.get(NO_KNOWN_ISSUES);
        if (noneKnown == null || noneKnown.isChecked()) {
            return NO_KNOWN_ISSUES;
        }

        return NO_KNOWN_ISSUES;
    }

    private int engineOptionIndex(String engineOption) {
        for (int i = 0; i < ENGINE_OPTIONS.length; i++) {
            if (ENGINE_OPTIONS[i].equals(engineOption)) {
                return i;
            }
        }
        return 0;
    }

    private boolean isEngineOption(String engineOption) {
        for (String option : ENGINE_OPTIONS) {
            if (option.equals(engineOption)) {
                return true;
            }
        }
        return false;
    }

    private String cleanCustomEngineName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return SpectrumFeatures.sanitizeCsv(value);
    }

    private String cleanOptionalText(String value) {
        return SpectrumFeatures.sanitizeOptionalCsv(value);
    }

    private String cleanUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String clean = value.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    private EditText numberInput(String value) {
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setText(value);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return editText;
    }

    private EditText urlInput(String value) {
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setText(value);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        return editText;
    }

    private EditText textInput(String value) {
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setText(value);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        return editText;
    }

    private EditText multiLineInput(String value) {
        EditText editText = new EditText(this);
        editText.setSingleLine(false);
        editText.setMinLines(2);
        editText.setGravity(Gravity.TOP);
        editText.setText(value);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return editText;
    }

    private TextView metric(String label, String value) {
        TextView textView = text(label + "   " + value, 15, Color.rgb(15, 23, 42), Typeface.NORMAL);
        textView.setPadding(0, dp(4), 0, dp(4));
        textView.setTypeface(Typeface.MONOSPACE);
        return textView;
    }

    private String formatMetric(String label, String pattern, double value) {
        return String.format(Locale.US, "%s   " + pattern, label, value);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.DEFAULT, style);
        textView.setIncludeFontPadding(true);
        return textView;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                return fallback;
            }
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
