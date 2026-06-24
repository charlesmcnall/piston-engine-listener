package com.chaz.pistonlistener;

import java.util.Locale;

public final class SignalQualityGate {
    private static final double CLIP_PERCENT_LIMIT = 0.02;
    private static final double FLAT_TOP_PERCENT_LIMIT = 0.05;
    private static final double PEAK_HARD_LIMIT_DBFS = -0.10;
    private static final double TOO_QUIET_RMS_DBFS = -55.0;
    private static final double TOO_QUIET_PEAK_DBFS = -35.0;
    private static final double LOW_CREST_DB = 5.5;
    private static final double LOUD_RMS_FOR_COMPRESSION_DBFS = -35.0;
    private static final double LOUD_PEAK_FOR_COMPRESSION_DBFS = -8.0;

    private int frameCount;
    private int worstSeverity;
    private double sumRmsDbfs;
    private double sumPeakDbfs;
    private double sumCrestFactorDb;
    private double maxPeakDbfs = -180.0;
    private double maxClipPercent;
    private double maxFlatTopPercent;
    private double minRmsDbfs = 180.0;
    private double maxRmsDbfs = -180.0;

    public void reset() {
        frameCount = 0;
        worstSeverity = 0;
        sumRmsDbfs = 0.0;
        sumPeakDbfs = 0.0;
        sumCrestFactorDb = 0.0;
        maxPeakDbfs = -180.0;
        maxClipPercent = 0.0;
        maxFlatTopPercent = 0.0;
        minRmsDbfs = 180.0;
        maxRmsDbfs = -180.0;
    }

    public void add(SpectrumFeatures features) {
        Snapshot frame = classifyFrame(features);
        frameCount++;
        worstSeverity = Math.max(worstSeverity, frame.severity);
        sumRmsDbfs += features.rmsDbfs;
        sumPeakDbfs += features.peakDbfs;
        sumCrestFactorDb += features.crestFactorDb;
        maxPeakDbfs = Math.max(maxPeakDbfs, features.peakDbfs);
        maxClipPercent = Math.max(maxClipPercent, features.clippedPercent);
        maxFlatTopPercent = Math.max(maxFlatTopPercent, features.flatTopPercent);
        minRmsDbfs = Math.min(minRmsDbfs, features.rmsDbfs);
        maxRmsDbfs = Math.max(maxRmsDbfs, features.rmsDbfs);
    }

    public Snapshot snapshot() {
        if (frameCount == 0) {
            return new Snapshot(1, "No signal", "No frames captured", false);
        }

        double avgRms = sumRmsDbfs / frameCount;
        double avgPeak = sumPeakDbfs / frameCount;
        double avgCrest = sumCrestFactorDb / frameCount;

        int severity = worstSeverity;
        String label = "Good";
        boolean acceptable = true;

        if (maxClipPercent >= CLIP_PERCENT_LIMIT
                || maxFlatTopPercent >= FLAT_TOP_PERCENT_LIMIT
                || maxPeakDbfs >= PEAK_HARD_LIMIT_DBFS) {
            severity = Math.max(severity, 3);
            label = "Clipping";
            acceptable = false;
        } else if (avgRms <= TOO_QUIET_RMS_DBFS || maxPeakDbfs <= TOO_QUIET_PEAK_DBFS) {
            severity = Math.max(severity, 1);
            label = "Too quiet";
            acceptable = false;
        } else if (avgCrest <= LOW_CREST_DB
                && avgRms >= LOUD_RMS_FOR_COMPRESSION_DBFS
                && maxPeakDbfs >= LOUD_PEAK_FOR_COMPRESSION_DBFS) {
            severity = Math.max(severity, 2);
            label = "Compression suspected";
            acceptable = false;
        } else if (severity == 2) {
            label = "Compression suspected";
            acceptable = false;
        } else if (severity == 1) {
            label = "Too quiet";
            acceptable = false;
        }

        String detail = String.format(
                Locale.US,
                "avg RMS %.1f dBFS, peak %.1f dBFS, crest %.1f dB, clip %.3f%%, flat %.3f%%",
                avgRms,
                maxPeakDbfs,
                avgCrest,
                maxClipPercent,
                maxFlatTopPercent
        );
        return new Snapshot(severity, label, detail, acceptable);
    }

    public static Snapshot classifyFrame(SpectrumFeatures features) {
        if (features.clippedPercent >= CLIP_PERCENT_LIMIT
                || features.flatTopPercent >= FLAT_TOP_PERCENT_LIMIT
                || features.peakDbfs >= PEAK_HARD_LIMIT_DBFS) {
            return new Snapshot(3, "Clipping", "Mic/input path is saturated", false);
        }

        if (features.rmsDbfs <= TOO_QUIET_RMS_DBFS || features.peakDbfs <= TOO_QUIET_PEAK_DBFS) {
            return new Snapshot(1, "Too quiet", "Signal is near the noise floor", false);
        }

        if (features.crestFactorDb <= LOW_CREST_DB
                && features.rmsDbfs >= LOUD_RMS_FOR_COMPRESSION_DBFS
                && features.peakDbfs >= LOUD_PEAK_FOR_COMPRESSION_DBFS) {
            return new Snapshot(2, "Compression suspected", "Low crest factor at high level", false);
        }

        return new Snapshot(0, "Good", "Signal level is usable", true);
    }

    public static final class Snapshot {
        public final int severity;
        public final String label;
        public final String detail;
        public final boolean acceptableForTrend;

        Snapshot(int severity, String label, String detail, boolean acceptableForTrend) {
            this.severity = severity;
            this.label = label;
            this.detail = detail;
            this.acceptableForTrend = acceptableForTrend;
        }
    }
}
