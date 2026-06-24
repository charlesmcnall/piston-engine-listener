package com.chaz.pistonlistener;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SessionLogger {
    private static final String SUMMARY_FILE_NAME = "summary.csv";
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private File sessionFile;
    private File summaryFile;
    private long startedMillis;
    private String startedIso;
    private String phase;
    private long frameCount;
    private double sumRpm;
    private double sumRms;
    private double sumDominantHz;
    private double sumCentroidHz;
    private double sumBand20To120;
    private double sumBand120To600;
    private double sumBand600To2500;
    private double sumBand2500To6000;
    private double maxClipPercent;

    public void start(Context context, String sessionPhase) throws IOException {
        File sessionsDir = new File(context.getFilesDir(), "sessions");
        if (!sessionsDir.exists() && !sessionsDir.mkdirs()) {
            throw new IOException("Unable to create sessions directory.");
        }

        startedMillis = System.currentTimeMillis();
        startedIso = Instant.ofEpochMilli(startedMillis).toString();
        phase = SpectrumFeatures.sanitizeCsv(sessionPhase);
        frameCount = 0L;
        sumRpm = 0.0;
        sumRms = 0.0;
        sumDominantHz = 0.0;
        sumCentroidHz = 0.0;
        sumBand20To120 = 0.0;
        sumBand120To600 = 0.0;
        sumBand600To2500 = 0.0;
        sumBand2500To6000 = 0.0;
        maxClipPercent = 0.0;

        String safePhase = phase.replace(' ', '-').toLowerCase(Locale.US);
        sessionFile = new File(sessionsDir, "session-" + FILE_TIME.format(Instant.ofEpochMilli(startedMillis)) + "-" + safePhase + ".csv");
        summaryFile = new File(sessionsDir, SUMMARY_FILE_NAME);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(sessionFile, false))) {
            writer.write(SpectrumFeatures.csvHeader());
            writer.newLine();
        }
    }

    public synchronized void append(SpectrumFeatures features) throws IOException {
        if (sessionFile == null) {
            return;
        }

        long elapsedMillis = Math.max(0L, features.timestampMillis - startedMillis);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(sessionFile, true))) {
            writer.write(features.toCsvLine(elapsedMillis));
            writer.newLine();
        }

        frameCount++;
        sumRpm += features.rpm;
        sumRms += features.rmsDbfs;
        sumDominantHz += features.dominantHz;
        sumCentroidHz += features.centroidHz;
        sumBand20To120 += features.band20To120;
        sumBand120To600 += features.band120To600;
        sumBand600To2500 += features.band600To2500;
        sumBand2500To6000 += features.band2500To6000;
        maxClipPercent = Math.max(maxClipPercent, features.clippedPercent);
    }

    public synchronized void finish() throws IOException {
        if (summaryFile == null || frameCount == 0L) {
            return;
        }

        boolean writeHeader = !summaryFile.exists();
        long durationMillis = Math.max(0L, System.currentTimeMillis() - startedMillis);
        double frames = Math.max(1.0, frameCount);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(summaryFile, true))) {
            if (writeHeader) {
                writer.write(summaryHeader());
                writer.newLine();
            }
            writer.write(String.format(
                    Locale.US,
                    "%s,%s,%d,%d,%.1f,%.3f,%.4f,%.2f,%.2f,%.6f,%.6f,%.6f,%.6f",
                    startedIso,
                    phase,
                    durationMillis,
                    frameCount,
                    sumRpm / frames,
                    sumRms / frames,
                    maxClipPercent,
                    sumDominantHz / frames,
                    sumCentroidHz / frames,
                    sumBand20To120 / frames,
                    sumBand120To600 / frames,
                    sumBand600To2500 / frames,
                    sumBand2500To6000 / frames
            ));
            writer.newLine();
        }
    }

    public String getSessionFileName() {
        return sessionFile == null ? "" : sessionFile.getName();
    }

    public static Baseline loadBaseline(Context context, String phase) {
        File summary = new File(new File(context.getFilesDir(), "sessions"), SUMMARY_FILE_NAME);
        if (!summary.exists()) {
            return Baseline.empty();
        }

        List<double[]> rows = new ArrayList<>();
        String safePhase = SpectrumFeatures.sanitizeCsv(phase);

        try (BufferedReader reader = new BufferedReader(new FileReader(summary))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length < 13 || !safePhase.equals(parts[1])) {
                    continue;
                }

                rows.add(new double[]{
                        parse(parts[8]),
                        parse(parts[9]),
                        parse(parts[10]),
                        parse(parts[11]),
                        parse(parts[12])
                });
            }
        } catch (IOException ignored) {
            return Baseline.empty();
        }

        int start = Math.max(0, rows.size() - 20);
        double centroid = 0.0;
        double band20To120 = 0.0;
        double band120To600 = 0.0;
        double band600To2500 = 0.0;
        double band2500To6000 = 0.0;
        int count = rows.size() - start;

        for (int i = start; i < rows.size(); i++) {
            double[] row = rows.get(i);
            centroid += row[0];
            band20To120 += row[1];
            band120To600 += row[2];
            band600To2500 += row[3];
            band2500To6000 += row[4];
        }

        if (count <= 0) {
            return Baseline.empty();
        }

        return new Baseline(
                count,
                centroid / count,
                band20To120 / count,
                band120To600 / count,
                band600To2500 / count,
                band2500To6000 / count
        );
    }

    private static double parse(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }

    private static String summaryHeader() {
        return "startedAt,phase,durationMillis,frameCount,avgRpm,avgRmsDbfs,maxClipPercent,"
                + "avgDominantHz,avgCentroidHz,avgBand20_120,avgBand120_600,avgBand600_2500,avgBand2500_6000";
    }

    public static final class Baseline {
        public final int count;
        private final double centroidHz;
        private final double band20To120;
        private final double band120To600;
        private final double band600To2500;
        private final double band2500To6000;

        private Baseline(
                int count,
                double centroidHz,
                double band20To120,
                double band120To600,
                double band600To2500,
                double band2500To6000
        ) {
            this.count = count;
            this.centroidHz = centroidHz;
            this.band20To120 = band20To120;
            this.band120To600 = band120To600;
            this.band600To2500 = band600To2500;
            this.band2500To6000 = band2500To6000;
        }

        public static Baseline empty() {
            return new Baseline(0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        public boolean isReady() {
            return count >= 3;
        }

        public double score(SpectrumFeatures features) {
            if (!isReady()) {
                return 0.0;
            }

            double bandDistance =
                    square(features.band20To120 - band20To120)
                            + square(features.band120To600 - band120To600)
                            + square(features.band600To2500 - band600To2500)
                            + square(features.band2500To6000 - band2500To6000);
            double centroidDistance = Math.abs(features.centroidHz - centroidHz) / Math.max(centroidHz, 1.0);
            return Math.min(99.0, Math.sqrt(bandDistance) * 120.0 + centroidDistance * 25.0);
        }

        private static double square(double value) {
            return value * value;
        }
    }
}

