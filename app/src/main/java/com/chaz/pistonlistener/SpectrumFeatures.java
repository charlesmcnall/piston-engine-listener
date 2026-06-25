package com.chaz.pistonlistener;

import java.util.Locale;

public final class SpectrumFeatures {
    public final long timestampMillis;
    public final String phase;
    public final double rpm;
    public final String engine;
    public final double tmohHours;
    public final String knownIssueTags;
    public final String knownIssueNotes;
    public final double rmsDbfs;
    public final double peakDbfs;
    public final double crestFactorDb;
    public final double clippedPercent;
    public final double flatTopPercent;
    public final double dominantHz;
    public final double centroidHz;
    public final double band20To120;
    public final double band120To600;
    public final double band600To2500;
    public final double band2500To6000;
    public final double trendScore;
    public final float[] spectrum;

    public SpectrumFeatures(
            long timestampMillis,
            String phase,
            double rpm,
            String engine,
            double tmohHours,
            String knownIssueTags,
            String knownIssueNotes,
            double rmsDbfs,
            double peakDbfs,
            double crestFactorDb,
            double clippedPercent,
            double flatTopPercent,
            double dominantHz,
            double centroidHz,
            double band20To120,
            double band120To600,
            double band600To2500,
            double band2500To6000,
            double trendScore,
            float[] spectrum
    ) {
        this.timestampMillis = timestampMillis;
        this.phase = phase;
        this.rpm = rpm;
        this.engine = engine;
        this.tmohHours = tmohHours;
        this.knownIssueTags = knownIssueTags;
        this.knownIssueNotes = knownIssueNotes;
        this.rmsDbfs = rmsDbfs;
        this.peakDbfs = peakDbfs;
        this.crestFactorDb = crestFactorDb;
        this.clippedPercent = clippedPercent;
        this.flatTopPercent = flatTopPercent;
        this.dominantHz = dominantHz;
        this.centroidHz = centroidHz;
        this.band20To120 = band20To120;
        this.band120To600 = band120To600;
        this.band600To2500 = band600To2500;
        this.band2500To6000 = band2500To6000;
        this.trendScore = trendScore;
        this.spectrum = spectrum;
    }

    public SpectrumFeatures withContext(
            String nextPhase,
            double nextRpm,
            String nextEngine,
            double nextTmohHours,
            String nextKnownIssueTags,
            String nextKnownIssueNotes,
            double nextTrendScore
    ) {
        return new SpectrumFeatures(
                timestampMillis,
                nextPhase,
                nextRpm,
                nextEngine,
                nextTmohHours,
                nextKnownIssueTags,
                nextKnownIssueNotes,
                rmsDbfs,
                peakDbfs,
                crestFactorDb,
                clippedPercent,
                flatTopPercent,
                dominantHz,
                centroidHz,
                band20To120,
                band120To600,
                band600To2500,
                band2500To6000,
                nextTrendScore,
                spectrum
        );
    }

    public static String csvHeader() {
        return "timestampMillis,elapsedMillis,phase,rpm,rmsDbfs,clippedPercent,dominantHz,centroidHz,"
                + "band20_120,band120_600,band600_2500,band2500_6000,trendScore,"
                + "peakDbfs,crestFactorDb,flatTopPercent,signalQuality,engine,tmohHours,knownIssueTags,knownIssueNotes";
    }

    public String toCsvLine(long elapsedMillis) {
        return String.format(
                Locale.US,
                "%d,%d,%s,%.1f,%.3f,%.4f,%.2f,%.2f,%.6f,%.6f,%.6f,%.6f,%.3f,%.3f,%.3f,%.4f,%s,%s,%.1f,%s,%s",
                timestampMillis,
                elapsedMillis,
                sanitizeCsv(phase),
                rpm,
                rmsDbfs,
                clippedPercent,
                dominantHz,
                centroidHz,
                band20To120,
                band120To600,
                band600To2500,
                band2500To6000,
                trendScore,
                peakDbfs,
                crestFactorDb,
                flatTopPercent,
                sanitizeCsv(SignalQualityGate.classifyFrame(this).label),
                sanitizeCsv(engine),
                tmohHours,
                sanitizeCsv(knownIssueTags),
                sanitizeOptionalCsv(knownIssueNotes)
        );
    }

    static String sanitizeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "Unknown";
        }
        return value.replace(',', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    static String sanitizeOptionalCsv(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.replace(',', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
