package com.chaz.pistonlistener;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

public final class EngineAudioRecorder {
    public interface Listener {
        void onPcm(short[] samples, int sampleCount);

        void onFeatures(SpectrumFeatures features);

        void onStatus(String status);

        void onError(String message);
    }

    public static final int SAMPLE_RATE = 48000;
    public static final int FFT_SIZE = 4096;

    private static final int READ_BUFFER_SAMPLES = 1024;
    private static final long PUBLISH_INTERVAL_MILLIS = 250L;

    private final FftAnalyzer analyzer = new FftAnalyzer(SAMPLE_RATE, FFT_SIZE);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean running;
    private Thread worker;
    private AudioRecord audioRecord;

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void start(Listener listener) {
        if (running) {
            return;
        }

        try {
            int activeSource = preferredAudioSource();
            audioRecord = createAudioRecord(activeSource);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release();
                activeSource = MediaRecorder.AudioSource.MIC;
                audioRecord = createAudioRecord(activeSource);
            }

            audioRecord.startRecording();
            running = true;
            worker = new Thread(() -> recordLoop(listener, audioRecord), "engine-audio-recorder");
            worker.start();
            postStatus(listener, audioSourceName(activeSource) + " at 48 kHz");
        } catch (SecurityException exception) {
            running = false;
            postError(listener, "Microphone permission is required.");
        } catch (RuntimeException exception) {
            running = false;
            postError(listener, "Audio recorder failed: " + exception.getMessage());
        }
    }

    public synchronized void stop() {
        running = false;

        AudioRecord record = audioRecord;
        if (record != null) {
            try {
                record.stop();
            } catch (RuntimeException ignored) {
            }
        }

        Thread thread = worker;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(750L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        worker = null;
        audioRecord = null;
    }

    private void recordLoop(Listener listener, AudioRecord record) {
        short[] readBuffer = new short[READ_BUFFER_SAMPLES];
        short[] frame = new short[FFT_SIZE];
        int frameIndex = 0;
        long lastPublishMillis = 0L;

        try {
            while (running) {
                int read = record.read(readBuffer, 0, readBuffer.length, AudioRecord.READ_BLOCKING);
                if (read <= 0) {
                    continue;
                }

                short[] pcmSnapshot = new short[read];
                System.arraycopy(readBuffer, 0, pcmSnapshot, 0, read);
                listener.onPcm(pcmSnapshot, read);

                for (int i = 0; i < read; i++) {
                    frame[frameIndex++] = readBuffer[i];
                    if (frameIndex == FFT_SIZE) {
                        long now = System.currentTimeMillis();
                        if (now - lastPublishMillis >= PUBLISH_INTERVAL_MILLIS) {
                            short[] snapshot = frame.clone();
                            SpectrumFeatures features = analyzer.analyze(snapshot);
                            mainHandler.post(() -> listener.onFeatures(features));
                            lastPublishMillis = now;
                        }
                        frameIndex = 0;
                    }
                }
            }
        } catch (RuntimeException exception) {
            if (running) {
                postError(listener, "Audio capture stopped: " + exception.getMessage());
            }
        } finally {
            try {
                record.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static AudioRecord createAudioRecord(int source) {
        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBufferSize <= 0) {
            minBufferSize = FFT_SIZE * 2;
        }

        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build();

        return new AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(format)
                .setBufferSizeInBytes(Math.max(minBufferSize, FFT_SIZE * 4))
                .build();
    }

    private static int preferredAudioSource() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return MediaRecorder.AudioSource.UNPROCESSED;
        }
        return MediaRecorder.AudioSource.MIC;
    }

    private static String audioSourceName(int source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && source == MediaRecorder.AudioSource.UNPROCESSED) {
            return "Built-in mic raw path";
        }
        return "Built-in mic";
    }

    private void postStatus(Listener listener, String status) {
        mainHandler.post(() -> listener.onStatus(status));
    }

    private void postError(Listener listener, String message) {
        mainHandler.post(() -> listener.onError(message));
    }
}
