package com.chaz.pistonlistener;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class CloudflareUploader {
    public interface Listener {
        void onUploadStatus(String status);
    }

    private static final int BATCH_LIMIT = 5;
    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 60000;

    private static boolean running;

    private CloudflareUploader() {
    }

    public static void uploadPending(Context context, Config config, Listener listener) {
        if (config == null || !config.isReady()) {
            post(listener, "Cloudflare not configured");
            return;
        }
        if (!claimRun()) {
            post(listener, "Sync already running");
            return;
        }

        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                UploadQueueDatabase database = new UploadQueueDatabase(appContext);
                List<UploadQueueDatabase.PendingUpload> uploads = database.loadPending(BATCH_LIMIT);
                if (uploads.isEmpty()) {
                    mainHandler.post(() -> listener.onUploadStatus("No pending uploads"));
                    return;
                }

                int uploaded = 0;
                for (UploadQueueDatabase.PendingUpload upload : uploads) {
                    try {
                        uploadOne(config, upload);
                        database.markUploaded(upload.captureId);
                        CaptureRetention.prune(appContext);
                        uploaded++;
                        int pending = database.pendingCount();
                        String status = String.format(Locale.US, "Uploaded %d/%d, %d pending", uploaded, uploads.size(), pending);
                        mainHandler.post(() -> listener.onUploadStatus(status));
                    } catch (IOException exception) {
                        database.markFailed(upload.captureId, exception.getMessage());
                        String status = "Upload failed: " + safeError(exception.getMessage());
                        mainHandler.post(() -> listener.onUploadStatus(status));
                    }
                }
            } finally {
                releaseRun();
            }
        }, "cloudflare-upload").start();
    }

    private static void uploadOne(Config config, UploadQueueDatabase.PendingUpload upload) throws IOException {
        if (!upload.audioFile.exists()) {
            throw new IOException("missing WAV " + upload.audioFile.getName());
        }
        if (!upload.featuresFile.exists()) {
            throw new IOException("missing CSV " + upload.featuresFile.getName());
        }

        String captureId = urlEncode(upload.captureId);
        postJson(config.url + "/v1/captures", upload.metadataJson, config.uploadToken);
        putFile(config.url + "/v1/captures/" + captureId + "/audio", upload.audioFile, "audio/wav", config.uploadToken);
        putFile(config.url + "/v1/captures/" + captureId + "/features", upload.featuresFile, "text/csv", config.uploadToken);
    }

    private static void postJson(String url, String json, String token) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = open(url, "POST", token, "application/json");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }
        requireSuccess(connection);
    }

    private static void putFile(String url, File file, String contentType, String token) throws IOException {
        HttpURLConnection connection = open(url, "PUT", token, contentType);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(file.length());
        try (OutputStream output = connection.getOutputStream();
             InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        requireSuccess(connection);
    }

    private static HttpURLConnection open(String url, String method, String token, String contentType) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static void requireSuccess(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        String body = readBody(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " " + safeError(body));
        }
    }

    private static String readBody(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        try (InputStream body = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            while ((read = body.read(buffer)) != -1 && output.size() < 4096) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }

    private static String urlEncode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private static synchronized boolean claimRun() {
        if (running) {
            return false;
        }
        running = true;
        return true;
    }

    private static synchronized void releaseRun() {
        running = false;
    }

    private static void post(Listener listener, String status) {
        if (listener == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> listener.onUploadStatus(status));
    }

    private static String safeError(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "unknown error";
        }
        String clean = message.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 180 ? clean.substring(0, 180) : clean;
    }

    public static final class Config {
        public final String url;
        public final String uploadToken;

        public Config(String url, String uploadToken) {
            this.url = normalizeUrl(url);
            this.uploadToken = uploadToken == null ? "" : uploadToken.trim();
        }

        public boolean isReady() {
            return url.startsWith("https://") && uploadToken.length() >= 12;
        }

        private static String normalizeUrl(String value) {
            if (value == null) {
                return "";
            }
            String clean = value.trim();
            while (clean.endsWith("/")) {
                clean = clean.substring(0, clean.length() - 1);
            }
            return clean;
        }
    }
}
