package com.chaz.pistonlistener;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CaptureRetention {
    private static final String SUMMARY_FILE_NAME = "summary.csv";

    private CaptureRetention() {
    }

    public static Result prune(Context context) {
        File sessionsDir = new File(context.getFilesDir(), "sessions");
        if (!sessionsDir.exists()) {
            return Result.empty();
        }

        Set<String> retainedNames = latestAcceptedDetailNames(sessionsDir);
        Set<String> retainedPaths = new UploadQueueDatabase(context).pendingFilePaths();
        File[] files = sessionsDir.listFiles();
        if (files == null) {
            return Result.empty();
        }

        int deletedFiles = 0;
        long deletedBytes = 0L;
        for (File file : files) {
            if (!isDetailFile(file)) {
                continue;
            }
            if (retainedNames.contains(file.getName()) || retainedPaths.contains(file.getAbsolutePath())) {
                continue;
            }

            long length = file.length();
            if (file.delete()) {
                deletedFiles++;
                deletedBytes += length;
            }
        }
        return new Result(deletedFiles, deletedBytes);
    }

    private static Set<String> latestAcceptedDetailNames(File sessionsDir) {
        Set<String> retained = new HashSet<>();
        File summary = new File(sessionsDir, SUMMARY_FILE_NAME);
        if (!summary.exists()) {
            return retained;
        }

        Map<String, SummaryRow> latestRows = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(summary))) {
            String header = reader.readLine();
            if (header == null) {
                return retained;
            }

            Map<String, Integer> columns = columns(header);
            int startedAtIndex = index(columns, "startedAt", 0);
            int phaseIndex = index(columns, "phase", 1);
            int engineIndex = index(columns, "engine", 17);
            int featuresIndex = index(columns, "featuresFileName", -1);
            int audioIndex = index(columns, "audioFileName", -1);

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (featuresIndex < 0 || audioIndex < 0 || parts.length <= Math.max(featuresIndex, audioIndex)) {
                    continue;
                }

                String key = safePart(parts, engineIndex) + "|" + safePart(parts, phaseIndex);
                SummaryRow candidate = new SummaryRow(
                        safePart(parts, startedAtIndex),
                        safeFileName(parts[featuresIndex]),
                        safeFileName(parts[audioIndex])
                );
                if (candidate.featuresFileName.isEmpty() && candidate.audioFileName.isEmpty()) {
                    continue;
                }

                SummaryRow current = latestRows.get(key);
                if (current == null || candidate.startedAt.compareTo(current.startedAt) >= 0) {
                    latestRows.put(key, candidate);
                }
            }
        } catch (IOException ignored) {
            return retained;
        }

        for (SummaryRow row : latestRows.values()) {
            if (!row.featuresFileName.isEmpty()) {
                retained.add(row.featuresFileName);
            }
            if (!row.audioFileName.isEmpty()) {
                retained.add(row.audioFileName);
            }
        }
        return retained;
    }

    private static Map<String, Integer> columns(String header) {
        Map<String, Integer> columns = new HashMap<>();
        String[] parts = header.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            columns.put(parts[i], i);
        }
        return columns;
    }

    private static int index(Map<String, Integer> columns, String name, int fallback) {
        Integer index = columns.get(name);
        return index == null ? fallback : index;
    }

    private static String safePart(String[] parts, int index) {
        if (index < 0 || index >= parts.length) {
            return "";
        }
        return SpectrumFeatures.sanitizeCsv(parts[index]).toLowerCase(Locale.US);
    }

    private static String safeFileName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String clean = value.trim();
        if (clean.contains("/") || clean.contains("\\") || clean.contains("..")) {
            return "";
        }
        return clean;
    }

    private static boolean isDetailFile(File file) {
        String name = file.getName();
        return file.isFile()
                && name.startsWith("session-")
                && (name.endsWith(".wav") || name.endsWith(".csv"));
    }

    private static final class SummaryRow {
        private final String startedAt;
        private final String featuresFileName;
        private final String audioFileName;

        private SummaryRow(String startedAt, String featuresFileName, String audioFileName) {
            this.startedAt = startedAt;
            this.featuresFileName = featuresFileName;
            this.audioFileName = audioFileName;
        }
    }

    public static final class Result {
        public final int deletedFiles;
        public final long deletedBytes;

        private Result(int deletedFiles, long deletedBytes) {
            this.deletedFiles = deletedFiles;
            this.deletedBytes = deletedBytes;
        }

        private static Result empty() {
            return new Result(0, 0L);
        }

        public String label() {
            if (deletedFiles <= 0) {
                return "";
            }
            return String.format(Locale.US, "Pruned %d old files, %.1f MB", deletedFiles, deletedBytes / 1048576.0);
        }
    }
}
