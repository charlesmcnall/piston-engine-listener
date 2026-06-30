package com.chaz.pistonlistener;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class UploadQueueDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "capture_uploads.db";
    private static final int DB_VERSION = 1;

    public UploadQueueDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pending_uploads ("
                + "capture_id TEXT PRIMARY KEY,"
                + "metadata_json TEXT NOT NULL,"
                + "audio_path TEXT NOT NULL,"
                + "features_path TEXT NOT NULL,"
                + "status TEXT NOT NULL DEFAULT 'pending',"
                + "attempts INTEGER NOT NULL DEFAULT 0,"
                + "last_error TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE INDEX pending_uploads_status_created_idx ON pending_uploads(status, created_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void enqueue(CaptureSummary summary, String deviceLabel, String appVersion, String deviceId) {
        if (summary == null || summary.audioFile == null || summary.featuresFile == null) {
            return;
        }

        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("capture_id", summary.captureId);
        values.put("metadata_json", summary.toMetadataJson(deviceLabel, appVersion, deviceId));
        values.put("audio_path", summary.audioFile.getAbsolutePath());
        values.put("features_path", summary.featuresFile.getAbsolutePath());
        values.put("status", "pending");
        values.put("last_error", "");
        values.put("created_at", now);
        values.put("updated_at", now);
        getWritableDatabase().insertWithOnConflict("pending_uploads", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<PendingUpload> loadPending(int limit) {
        List<PendingUpload> uploads = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "pending_uploads",
                new String[]{"capture_id", "metadata_json", "audio_path", "features_path", "attempts"},
                "status <> ?",
                new String[]{"uploaded"},
                null,
                null,
                "created_at ASC",
                String.valueOf(Math.max(1, limit))
        )) {
            while (cursor.moveToNext()) {
                uploads.add(new PendingUpload(
                        cursor.getString(0),
                        cursor.getString(1),
                        new File(cursor.getString(2)),
                        new File(cursor.getString(3)),
                        cursor.getInt(4)
                ));
            }
        }
        return uploads;
    }

    public int pendingCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM pending_uploads WHERE status <> ?",
                new String[]{"uploaded"}
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        }
    }

    public Set<String> pendingFilePaths() {
        Set<String> paths = new HashSet<>();
        try (Cursor cursor = getReadableDatabase().query(
                "pending_uploads",
                new String[]{"audio_path", "features_path"},
                "status <> ?",
                new String[]{"uploaded"},
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                addPath(paths, cursor.getString(0));
                addPath(paths, cursor.getString(1));
            }
        }
        return paths;
    }

    public void markUploaded(String captureId) {
        ContentValues values = new ContentValues();
        values.put("status", "uploaded");
        values.put("last_error", "");
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("pending_uploads", values, "capture_id = ?", new String[]{captureId});
    }

    public void markFailed(String captureId, String error) {
        getWritableDatabase().execSQL(
                "UPDATE pending_uploads SET status = ?, attempts = attempts + 1, last_error = ?, updated_at = ? WHERE capture_id = ?",
                new Object[]{"failed", trimError(error), System.currentTimeMillis(), captureId}
        );
    }

    private static String trimError(String error) {
        if (error == null || error.trim().isEmpty()) {
            return "unknown upload error";
        }
        String clean = error.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 180 ? clean.substring(0, 180) : clean;
    }

    private static void addPath(Set<String> paths, String path) {
        if (path != null && !path.trim().isEmpty()) {
            paths.add(new File(path).getAbsolutePath());
        }
    }

    public static final class PendingUpload {
        public final String captureId;
        public final String metadataJson;
        public final File audioFile;
        public final File featuresFile;
        public final int attempts;

        private PendingUpload(String captureId, String metadataJson, File audioFile, File featuresFile, int attempts) {
            this.captureId = captureId;
            this.metadataJson = metadataJson;
            this.audioFile = audioFile;
            this.featuresFile = featuresFile;
            this.attempts = attempts;
        }
    }
}
