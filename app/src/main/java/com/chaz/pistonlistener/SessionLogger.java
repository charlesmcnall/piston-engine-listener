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
import java.util.UUID;

public final class SessionLogger {
    private static final String SUMMARY_FILE_NAME = "summary.csv";
    private static final String LEGACY_ENGINE = "Jabiru 3300";
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private File sessionFile;
    private File audioFile;
    private File summaryFile;
    private String captureId;
    private long startedMillis;
    private String startedIso;
    private String phase;
    private String engine;
    private double tmohHours;
    private String knownIssueTags;
    private String knownIssueNotes;
    private long frameCount;
    private double sumRpm;
    private double sumRms;
    private double sumPeakDbfs;
    private double sumCrestFactorDb;
    private double sumDominantHz;
    private double sumCentroidHz;
    private double sumBand20To120;
    private double sumBand120To600;
    private double sumBand600To2500;
    private double sumBand2500To6000;
    private double maxClipPercent;
    private double maxFlatTopPercent;

    public void start(
            Context context,
            String sessionPhase,
            String sessionEngine,
            double sessionTmohHours,
            String sessionKnownIssueTags,
            String sessionKnownIssueNotes
    ) throws IOException {
        File sessionsDir = new File(context.getFilesDir(), "sessions");
        if (!sessionsDir.exists() && !sessionsDir.mkdirs()) {
            throw new IOException("Unable to create sessions directory.");
        }

        startedMillis = System.currentTimeMillis();
        startedIso = Instant.ofEpochMilli(startedMillis).toString();
        phase = SpectrumFeatures.sanitizeCsv(sessionPhase);
        engine = SpectrumFeatures.sanitizeCsv(sessionEngine);
        tmohHours = Math.max(0.0, sessionTmohHours);
        knownIssueTags = SpectrumFeatures.sanitizeCsv(sessionKnownIssueTags);
        knownIssueNotes = SpectrumFeatures.sanitizeOptionalCsv(sessionKnownIssueNotes);
        frameCount = 0L;
        sumRpm = 0.0;
        sumRms = 0.0;
        sumPeakDbfs = 0.0;
        sumCrestFactorDb = 0.0;
        sumDominantHz = 0.0;
        sumCentroidHz = 0.0;
        sumBand20To120 = 0.0;
        sumBand120To600 = 0.0;
        sumBand600To2500 = 0.0;
        sumBand2500To6000 = 0.0;
        maxClipPercent = 0.0;
        maxFlatTopPercent = 0.0;

        captureId = UUID.randomUUID().toString();
        String safePhase = phase.replace(' ', '-').toLowerCase(Locale.US);
        String safeEngine = fileSegment(engine);
        String baseName = "session-" + FILE_TIME.format(Instant.ofEpochMilli(startedMillis)) + "-" + safeEngine + "-" + safePhase;
        sessionFile = new File(sessionsDir, baseName + ".csv");
        audioFile = new File(sessionsDir, baseName + ".wav");
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
        sumPeakDbfs += features.peakDbfs;
        sumCrestFactorDb += features.crestFactorDb;
        sumDominantHz += features.dominantHz;
        sumCentroidHz += features.centroidHz;
        sumBand20To120 += features.band20To120;
        sumBand120To600 += features.band120To600;
        sumBand600To2500 += features.band600To2500;
        sumBand2500To6000 += features.band2500To6000;
        maxClipPercent = Math.max(maxClipPercent, features.clippedPercent);
        maxFlatTopPercent = Math.max(maxFlatTopPercent, features.flatTopPercent);
    }

    public synchronized CaptureSummary finish(SignalQualityGate.Snapshot signalQuality) throws IOException {
        if (summaryFile == null || frameCount == 0L) {
            return null;
        }

        ensureSummaryHeader();
        boolean writeHeader = !summaryFile.exists() || summaryFile.length() == 0L;
        long durationMillis = Math.max(0L, System.currentTimeMillis() - startedMillis);
        double frames = Math.max(1.0, frameCount);
        double avgRpm = sumRpm / frames;
        double avgRmsDbfs = sumRms / frames;
        double avgPeakDbfs = sumPeakDbfs / frames;
        double avgCrestFactorDb = sumCrestFactorDb / frames;
        double avgDominantHz = sumDominantHz / frames;
        double avgCentroidHz = sumCentroidHz / frames;
        double avgBand20To120 = sumBand20To120 / frames;
        double avgBand120To600 = sumBand120To600 / frames;
        double avgBand600To2500 = sumBand600To2500 / frames;
        double avgBand2500To6000 = sumBand2500To6000 / frames;
        String qualityLabel = SpectrumFeatures.sanitizeCsv(signalQuality == null ? "Unknown" : signalQuality.label);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(summaryFile, true))) {
            if (writeHeader) {
                writer.write(summaryHeader());
                writer.newLine();
            }
            writer.write(String.format(
                    Locale.US,
                    "%s,%s,%d,%d,%.1f,%.3f,%.4f,%.2f,%.2f,%.6f,%.6f,%.6f,%.6f,%.3f,%.3f,%.4f,%s,%s,%.1f,%s,%s,%s,%s,%s",
                    startedIso,
                    phase,
                    durationMillis,
                    frameCount,
                    avgRpm,
                    avgRmsDbfs,
                    maxClipPercent,
                    avgDominantHz,
                    avgCentroidHz,
                    avgBand20To120,
                    avgBand120To600,
                    avgBand600To2500,
                    avgBand2500To6000,
                    avgPeakDbfs,
                    avgCrestFactorDb,
                    maxFlatTopPercent,
                    qualityLabel,
                    engine,
                    tmohHours,
                    knownIssueTags,
                    knownIssueNotes,
                    captureId,
                    sessionFile == null ? "" : sessionFile.getName(),
                    audioFile == null ? "" : audioFile.getName()
            ));
            writer.newLine();
        }

        return new CaptureSummary(
                captureId,
                startedIso,
                phase,
                engine,
                tmohHours,
                knownIssueTags,
                knownIssueNotes,
                durationMillis,
                frameCount,
                avgRpm,
                avgRmsDbfs,
                maxClipPercent,
                avgDominantHz,
                avgCentroidHz,
                avgBand20To120,
                avgBand120To600,
                avgBand600To2500,
                avgBand2500To6000,
                avgPeakDbfs,
                avgCrestFactorDb,
                maxFlatTopPercent,
                qualityLabel,
                true,
                sessionFile,
                audioFile
        );
    }

    public String getSessionFileName() {
        return sessionFile == null ? "" : sessionFile.getName();
    }

    public File getSessionFile() {
        return sessionFile;
    }

    public File getAudioFile() {
        return audioFile;
    }

    public String getCaptureId() {
        return captureId == null ? "" : captureId;
    }

    public static Baseline loadBaseline(Context context, String phase, String engine) {
        File summary = new File(new File(context.getFilesDir(), "sessions"), SUMMARY_FILE_NAME);
        if (!summary.exists()) {
            return Baseline.empty();
        }

        List<double[]> rows = new ArrayList<>();
        String safePhase = SpectrumFeatures.sanitizeCsv(phase);
        String safeEngine = SpectrumFeatures.sanitizeCsv(engine);

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

                String rowEngine = parts.length >= 18 ? SpectrumFeatures.sanitizeCsv(parts[17]) : LEGACY_ENGINE;
                if (!safeEngine.equals(rowEngine)) {
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

        double[] latest = rows.get(rows.size() - 1);
        return new Baseline(
                count,
                centroid / count,
                band20To120 / count,
                band120To600 / count,
                band600To2500 / count,
                band2500To6000 / count,
                latest[0],
                latest[1],
                latest[2],
                latest[3],
                latest[4]
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
                + "avgDominantHz,avgCentroidHz,avgBand20_120,avgBand120_600,avgBand600_2500,avgBand2500_6000,"
                + "avgPeakDbfs,avgCrestFactorDb,maxFlatTopPercent,signalQuality,engine,tmohHours,knownIssueTags,knownIssueNotes,"
                + "captureId,featuresFileName,audioFileName";
    }

    private void ensureSummaryHeader() throws IOException {
        if (summaryFile == null || !summaryFile.exists() || summaryFile.length() == 0L) {
            return;
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(summaryFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        if (lines.isEmpty() || lines.get(0).endsWith(",audioFileName")) {
            return;
        }

        lines.set(0, summaryHeader());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(summaryFile, false))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private static String fileSegment(String value) {
        String clean = SpectrumFeatures.sanitizeCsv(value).toLowerCase(Locale.US);
        clean = clean.replaceAll("[^a-z0-9]+", "-");
        clean = clean.replaceAll("^-+|-+$", "");
        return clean.isEmpty() ? "unknown" : clean;
    }

    public static final class Baseline {
        public final int count;
        private final double centroidHz;
        private final double band20To120;
        private final double band120To600;
        private final double band600To2500;
        private final double band2500To6000;
        private final double latestCentroidHz;
        private final double latestBand20To120;
        private final double latestBand120To600;
        private final double latestBand600To2500;
        private final double latestBand2500To6000;

        private Baseline(
                int count,
                double centroidHz,
                double band20To120,
                double band120To600,
                double band600To2500,
                double band2500To6000,
                double latestCentroidHz,
                double latestBand20To120,
                double latestBand120To600,
                double latestBand600To2500,
                double latestBand2500To6000
        ) {
            this.count = count;
            this.centroidHz = centroidHz;
            this.band20To120 = band20To120;
            this.band120To600 = band120To600;
            this.band600To2500 = band600To2500;
            this.band2500To6000 = band2500To6000;
            this.latestCentroidHz = latestCentroidHz;
            this.latestBand20To120 = latestBand20To120;
            this.latestBand120To600 = latestBand120To600;
            this.latestBand600To2500 = latestBand600To2500;
            this.latestBand2500To6000 = latestBand2500To6000;
        }

        public static Baseline empty() {
            return new Baseline(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        public boolean isReady() {
            return count >= 3;
        }

        public boolean hasPrevious() {
            return count > 0;
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

        public double previousScore(SpectrumFeatures features) {
            if (!hasPrevious()) {
                return 0.0;
            }

            double bandDistance =
                    square(features.band20To120 - latestBand20To120)
                            + square(features.band120To600 - latestBand120To600)
                            + square(features.band600To2500 - latestBand600To2500)
                            + square(features.band2500To6000 - latestBand2500To6000);
            double centroidDistance = Math.abs(features.centroidHz - latestCentroidHz) / Math.max(latestCentroidHz, 1.0);
            return Math.min(99.0, Math.sqrt(bandDistance) * 120.0 + centroidDistance * 25.0);
        }

        private static double square(double value) {
            return value * value;
        }
    }
}
