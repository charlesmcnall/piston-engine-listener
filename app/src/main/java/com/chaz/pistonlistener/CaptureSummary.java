package com.chaz.pistonlistener;

import java.io.File;
import java.util.Locale;

public final class CaptureSummary {
    public final String captureId;
    public final String startedAt;
    public final String phase;
    public final String engine;
    public final double tmohHours;
    public final String knownIssueTags;
    public final String knownIssueNotes;
    public final long durationMillis;
    public final long frameCount;
    public final double avgRpm;
    public final double avgRmsDbfs;
    public final double maxClipPercent;
    public final double avgDominantHz;
    public final double avgCentroidHz;
    public final double avgBand20To120;
    public final double avgBand120To600;
    public final double avgBand600To2500;
    public final double avgBand2500To6000;
    public final double avgPeakDbfs;
    public final double avgCrestFactorDb;
    public final double maxFlatTopPercent;
    public final String signalQuality;
    public final boolean acceptedForTrend;
    public final File featuresFile;
    public final File audioFile;

    public CaptureSummary(
            String captureId,
            String startedAt,
            String phase,
            String engine,
            double tmohHours,
            String knownIssueTags,
            String knownIssueNotes,
            long durationMillis,
            long frameCount,
            double avgRpm,
            double avgRmsDbfs,
            double maxClipPercent,
            double avgDominantHz,
            double avgCentroidHz,
            double avgBand20To120,
            double avgBand120To600,
            double avgBand600To2500,
            double avgBand2500To6000,
            double avgPeakDbfs,
            double avgCrestFactorDb,
            double maxFlatTopPercent,
            String signalQuality,
            boolean acceptedForTrend,
            File featuresFile,
            File audioFile
    ) {
        this.captureId = captureId;
        this.startedAt = startedAt;
        this.phase = phase;
        this.engine = engine;
        this.tmohHours = tmohHours;
        this.knownIssueTags = knownIssueTags;
        this.knownIssueNotes = knownIssueNotes;
        this.durationMillis = durationMillis;
        this.frameCount = frameCount;
        this.avgRpm = avgRpm;
        this.avgRmsDbfs = avgRmsDbfs;
        this.maxClipPercent = maxClipPercent;
        this.avgDominantHz = avgDominantHz;
        this.avgCentroidHz = avgCentroidHz;
        this.avgBand20To120 = avgBand20To120;
        this.avgBand120To600 = avgBand120To600;
        this.avgBand600To2500 = avgBand600To2500;
        this.avgBand2500To6000 = avgBand2500To6000;
        this.avgPeakDbfs = avgPeakDbfs;
        this.avgCrestFactorDb = avgCrestFactorDb;
        this.maxFlatTopPercent = maxFlatTopPercent;
        this.signalQuality = signalQuality;
        this.acceptedForTrend = acceptedForTrend;
        this.featuresFile = featuresFile;
        this.audioFile = audioFile;
    }

    public String toMetadataJson(String deviceLabel, String appVersion, String deviceId) {
        StringBuilder json = new StringBuilder(768);
        json.append('{');
        appendString(json, "captureId", captureId).append(',');
        appendString(json, "startedAt", startedAt).append(',');
        appendString(json, "deviceLabel", deviceLabel).append(',');
        appendString(json, "appVersion", appVersion).append(',');
        appendString(json, "deviceId", deviceId).append(',');
        appendString(json, "visibility", "public").append(',');
        appendString(json, "phase", phase).append(',');
        appendString(json, "engine", engine).append(',');
        appendNumber(json, "tmohHours", tmohHours).append(',');
        appendString(json, "knownIssueTags", knownIssueTags).append(',');
        appendString(json, "knownIssueNotes", knownIssueNotes).append(',');
        appendNumber(json, "durationMillis", durationMillis).append(',');
        appendNumber(json, "frameCount", frameCount).append(',');
        appendNumber(json, "avgRpm", avgRpm).append(',');
        appendNumber(json, "avgRmsDbfs", avgRmsDbfs).append(',');
        appendNumber(json, "maxClipPercent", maxClipPercent).append(',');
        appendNumber(json, "avgDominantHz", avgDominantHz).append(',');
        appendNumber(json, "avgCentroidHz", avgCentroidHz).append(',');
        appendNumber(json, "avgBand20To120", avgBand20To120).append(',');
        appendNumber(json, "avgBand120To600", avgBand120To600).append(',');
        appendNumber(json, "avgBand600To2500", avgBand600To2500).append(',');
        appendNumber(json, "avgBand2500To6000", avgBand2500To6000).append(',');
        appendNumber(json, "avgPeakDbfs", avgPeakDbfs).append(',');
        appendNumber(json, "avgCrestFactorDb", avgCrestFactorDb).append(',');
        appendNumber(json, "maxFlatTopPercent", maxFlatTopPercent).append(',');
        appendString(json, "signalQuality", signalQuality).append(',');
        appendBoolean(json, "acceptedForTrend", acceptedForTrend).append(',');
        appendNumber(json, "sampleRate", EngineAudioRecorder.SAMPLE_RATE).append(',');
        appendString(json, "audioFileName", audioFile == null ? "" : audioFile.getName()).append(',');
        appendString(json, "featuresFileName", featuresFile == null ? "" : featuresFile.getName());
        json.append('}');
        return json.toString();
    }

    private static StringBuilder appendString(StringBuilder json, String key, String value) {
        json.append('"').append(key).append("\":");
        quote(json, value == null ? "" : value);
        return json;
    }

    private static StringBuilder appendNumber(StringBuilder json, String key, double value) {
        json.append('"').append(key).append("\":");
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            json.append('0');
        } else {
            json.append(String.format(Locale.US, "%.6f", value));
        }
        return json;
    }

    private static StringBuilder appendNumber(StringBuilder json, String key, long value) {
        json.append('"').append(key).append("\":").append(value);
        return json;
    }

    private static StringBuilder appendBoolean(StringBuilder json, String key, boolean value) {
        json.append('"').append(key).append("\":").append(value ? "true" : "false");
        return json;
    }

    private static void quote(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                json.append('\\').append(c);
            } else if (c == '\n') {
                json.append("\\n");
            } else if (c == '\r') {
                json.append("\\r");
            } else if (c == '\t') {
                json.append("\\t");
            } else if (c < 0x20) {
                json.append(String.format(Locale.US, "\\u%04x", (int) c));
            } else {
                json.append(c);
            }
        }
        json.append('"');
    }
}
