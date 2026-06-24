package com.chaz.pistonlistener;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 7001;

    private final EngineAudioRecorder recorder = new EngineAudioRecorder();

    private Button recordButton;
    private Spinner phaseSpinner;
    private EditText rpmInput;
    private SpectrumView spectrumView;
    private TextView statusText;
    private TextView rmsText;
    private TextView clipText;
    private TextView dominantText;
    private TextView centroidText;
    private TextView bandsText;
    private TextView trendText;
    private TextView baselineText;

    private SessionLogger logger;
    private SessionLogger.Baseline baseline = SessionLogger.Baseline.empty();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        updatePermissionState();
    }

    @Override
    protected void onDestroy() {
        stopRecording();
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

        TextView subtitle = text("Audio trend logger", 14, Color.rgb(71, 85, 105), Typeface.NORMAL);
        subtitle.setPadding(0, dp(2), 0, dp(14));
        content.addView(subtitle, matchWrap());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, 0, 0, dp(12));
        content.addView(controls, matchWrap());

        phaseSpinner = new Spinner(this);
        String[] phases = new String[]{"Idle", "Run-up", "Climb", "Cruise", "Descent"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, phases);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        phaseSpinner.setAdapter(adapter);
        phaseSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                baseline = SessionLogger.loadBaseline(MainActivity.this, selectedPhase());
                refreshBaselineLabel();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        controls.addView(phaseSpinner, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));

        rpmInput = new EditText(this);
        rpmInput.setHint("RPM");
        rpmInput.setSingleLine(true);
        rpmInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rpmInput.setGravity(Gravity.CENTER);
        controls.addView(rpmInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f));

        recordButton = new Button(this);
        recordButton.setText("Record");
        recordButton.setOnClickListener(view -> toggleRecording());
        content.addView(recordButton, matchWrap());

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

        rmsText = metric("RMS", "--");
        clipText = metric("Clipping", "--");
        dominantText = metric("Dominant", "--");
        centroidText = metric("Centroid", "--");
        bandsText = metric("Bands", "--");
        trendText = metric("Trend", "--");
        baselineText = metric("Baseline", "--");

        metrics.addView(rmsText, matchWrap());
        metrics.addView(clipText, matchWrap());
        metrics.addView(dominantText, matchWrap());
        metrics.addView(centroidText, matchWrap());
        metrics.addView(bandsText, matchWrap());
        metrics.addView(trendText, matchWrap());
        metrics.addView(baselineText, matchWrap());

        statusText = text("Ready", 14, Color.rgb(51, 65, 85), Typeface.NORMAL);
        statusText.setPadding(0, dp(12), 0, 0);
        content.addView(statusText, matchWrap());

        setContentView(scrollView);
        baseline = SessionLogger.loadBaseline(this, selectedPhase());
        refreshBaselineLabel();
    }

    private void toggleRecording() {
        if (recorder.isRunning()) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        baseline = SessionLogger.loadBaseline(this, selectedPhase());
        logger = new SessionLogger();
        try {
            logger.start(this, selectedPhase());
        } catch (IOException exception) {
            statusText.setText("Log start failed: " + exception.getMessage());
            return;
        }

        recordButton.setText("Stop");
        phaseSpinner.setEnabled(false);
        statusText.setText("Recording");

        recorder.start(new EngineAudioRecorder.Listener() {
            @Override
            public void onFeatures(SpectrumFeatures rawFeatures) {
                double trend = baseline.score(rawFeatures);
                SpectrumFeatures features = rawFeatures.withContext(selectedPhase(), parseRpm(), trend);
                updateMetrics(features);
                try {
                    logger.append(features);
                } catch (IOException exception) {
                    statusText.setText("Log write failed: " + exception.getMessage());
                }
            }

            @Override
            public void onStatus(String status) {
                statusText.setText(status);
            }

            @Override
            public void onError(String message) {
                statusText.setText(message);
                stopRecording();
            }
        });
    }

    private void stopRecording() {
        if (!recorder.isRunning() && logger == null) {
            return;
        }

        recorder.stop();
        String sessionName = "";
        if (logger != null) {
            sessionName = logger.getSessionFileName();
            try {
                logger.finish();
            } catch (IOException exception) {
                statusText.setText("Summary write failed: " + exception.getMessage());
            }
        }
        logger = null;
        baseline = SessionLogger.loadBaseline(this, selectedPhase());

        if (recordButton != null) {
            recordButton.setText("Record");
            phaseSpinner.setEnabled(true);
        }
        refreshBaselineLabel();
        if (statusText != null && !sessionName.isEmpty()) {
            statusText.setText("Saved " + sessionName);
        }
    }

    private void updateMetrics(SpectrumFeatures features) {
        spectrumView.setSpectrum(features.spectrum);
        rmsText.setText(formatMetric("RMS", "%.1f dBFS", features.rmsDbfs));
        clipText.setText(formatMetric("Clipping", "%.3f %%", features.clippedPercent));
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

        if (features.clippedPercent > 0.25) {
            statusText.setText("Input clipping");
        } else if (baseline.isReady() && features.trendScore > 20.0) {
            statusText.setText("Spectral shift");
        }
    }

    private void updatePermissionState() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recordButton.setEnabled(true);
            statusText.setText("Ready");
        } else {
            recordButton.setEnabled(true);
            statusText.setText("Microphone permission");
        }
    }

    private void refreshBaselineLabel() {
        if (baseline.isReady()) {
            baselineText.setText(String.format(Locale.US, "Baseline   %d sessions", baseline.count));
        } else {
            baselineText.setText(String.format(Locale.US, "Baseline   %d / 3", baseline.count));
        }
    }

    private String selectedPhase() {
        Object selected = phaseSpinner == null ? null : phaseSpinner.getSelectedItem();
        return selected == null ? "Idle" : selected.toString();
    }

    private double parseRpm() {
        if (rpmInput == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(rpmInput.getText().toString());
        } catch (NumberFormatException exception) {
            return 0.0;
        }
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
}

