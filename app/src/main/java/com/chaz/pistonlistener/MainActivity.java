package com.chaz.pistonlistener;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 7001;
    private static final String PREFS_NAME = "capture_settings";
    private static final String KEY_CAPTURE_SECONDS = "capture_seconds";
    private static final int DEFAULT_CAPTURE_SECONDS = 30;
    private static final int MIN_CAPTURE_SECONDS = 5;
    private static final int MAX_CAPTURE_SECONDS = 300;
    private static final int PREFLIGHT_CAPTURE_SECONDS = 8;
    private static final String[] PHASES = new String[]{"Idle", "Run-up", "Climb", "Cruise", "Descent"};
    private static final String[] PREFLIGHT_STEPS = new String[]{"Quiet cabin", "Idle", "Run-up"};
    private static final String USER_INSTRUCTIONS =
            "1. Install\n"
                    + "Download the APK from the GitHub release, open it on the Android phone, allow install unknown apps if prompted, then launch Piston Listener. Grant microphone permission.\n\n"
                    + "2. Configure Targets\n"
                    + "Open Settings, set the default capture time, and enter the target RPM for each engine phase you plan to record. Leave RPM blank or 0 if you do not know it yet.\n\n"
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
                    + "Signal: the current quality gate result.\n"
                    + "RMS: overall loudness.\n"
                    + "Clipping: overloaded audio percentage. Should stay near zero.\n"
                    + "Peak/Crest: peak level and headroom shape.\n"
                    + "Dominant: strongest frequency.\n"
                    + "Centroid: center of spectral energy.\n"
                    + "Bands: energy split across frequency ranges.\n"
                    + "Baseline: how many prior sessions exist for the active phase.\n"
                    + "Trend: rough change score versus previous recordings for that phase.\n\n"
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
    private TextView signalText;
    private TextView rmsText;
    private TextView clipText;
    private TextView peakText;
    private TextView dominantText;
    private TextView centroidText;
    private TextView bandsText;
    private TextView trendText;
    private TextView baselineText;

    private SessionLogger logger;
    private SessionLogger.Baseline baseline = SessionLogger.Baseline.empty();
    private String activePhase = "Idle";
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

        signalText = metric("Signal", "--");
        rmsText = metric("RMS", "--");
        clipText = metric("Clipping", "--");
        peakText = metric("Peak/Crest", "--");
        dominantText = metric("Dominant", "--");
        centroidText = metric("Centroid", "--");
        bandsText = metric("Bands", "--");
        trendText = metric("Trend", "--");
        baselineText = metric("Baseline", "--");

        metrics.addView(signalText, matchWrap());
        metrics.addView(rmsText, matchWrap());
        metrics.addView(clipText, matchWrap());
        metrics.addView(peakText, matchWrap());
        metrics.addView(dominantText, matchWrap());
        metrics.addView(centroidText, matchWrap());
        metrics.addView(bandsText, matchWrap());
        metrics.addView(trendText, matchWrap());
        metrics.addView(baselineText, matchWrap());

        statusText = text("Ready", 14, Color.rgb(51, 65, 85), Typeface.NORMAL);
        statusText.setPadding(0, dp(12), 0, 0);
        content.addView(statusText, matchWrap());

        setContentView(scrollView);
        refreshPhaseButtons();
        refreshBaselineLabel();
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
        activeTargetRpm = targetRpm(phase);
        activeCaptureSeconds = durationSeconds;
        baseline = calibrationCapture ? SessionLogger.Baseline.empty() : SessionLogger.loadBaseline(this, activePhase);
        logger = calibrationCapture ? null : new SessionLogger();
        qualityGate.reset();
        captureCompleting = false;
        sourceStatus = "";

        if (logger != null) {
            try {
                logger.start(this, activePhase);
            } catch (IOException exception) {
                statusText.setText("Log start failed: " + exception.getMessage());
                logger = null;
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
            public void onFeatures(SpectrumFeatures rawFeatures) {
                double trend = baseline.score(rawFeatures);
                SpectrumFeatures features = rawFeatures.withContext(activePhase, activeTargetRpm, trend);
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

        SignalQualityGate.Snapshot signalQuality = qualityGate.snapshot();
        String sessionName = "";
        boolean rejected = false;
        boolean summaryFailed = false;
        String summaryError = "";
        if (logger != null && completed) {
            sessionName = logger.getSessionFileName();
            if (signalQuality.acceptableForTrend) {
                try {
                    logger.finish(signalQuality);
                } catch (IOException exception) {
                    summaryFailed = true;
                    summaryError = exception.getMessage();
                }
            } else {
                rejected = true;
            }
        }
        logger = null;
        baseline = calibrationCapture ? SessionLogger.Baseline.empty() : SessionLogger.loadBaseline(this, activePhase);

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
            if (completed && summaryFailed) {
                statusText.setText("Summary write failed: " + summaryError);
            } else if (completed && rejected) {
                statusText.setText("Rejected " + sessionName + " - " + signalQuality.label);
            } else if (completed && !sessionName.isEmpty()) {
                statusText.setText("Saved " + sessionName);
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

        TextView durationLabel = text("Default capture time, seconds", 14, Color.rgb(51, 65, 85), Typeface.BOLD);
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

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(form);

        new AlertDialog.Builder(this)
                .setTitle("Capture Settings")
                .setView(scrollView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    saveSettings(durationInput, rpmInputs);
                    refreshPhaseButtons();
                    statusText.setText("Settings saved");
                })
                .show();
    }

    private void saveSettings(EditText durationInput, Map<String, EditText> rpmInputs) {
        SharedPreferences.Editor editor = prefs().edit();
        int seconds = clamp(parseInt(durationInput.getText().toString(), DEFAULT_CAPTURE_SECONDS), MIN_CAPTURE_SECONDS, MAX_CAPTURE_SECONDS);
        editor.putInt(KEY_CAPTURE_SECONDS, seconds);

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

    private void refreshBaselineLabel() {
        if (baseline.isReady()) {
            baselineText.setText(String.format(Locale.US, "Baseline   %s %d sessions", activePhase, baseline.count));
        } else {
            baselineText.setText(String.format(Locale.US, "Baseline   %s %d / 3", activePhase, baseline.count));
        }
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

    private EditText numberInput(String value) {
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setText(value);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
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
