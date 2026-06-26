package com.chaz.pistonlistener;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class WavFileWriter {
    private static final int HEADER_BYTES = 44;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;

    private final File file;
    private RandomAccessFile output;
    private long dataBytes;

    public WavFileWriter(File file) {
        this.file = file;
    }

    public synchronized void start() throws IOException {
        output = new RandomAccessFile(file, "rw");
        output.setLength(0L);
        dataBytes = 0L;
        writeHeader(output, 0L);
    }

    public synchronized void append(short[] samples, int sampleCount) throws IOException {
        if (output == null || sampleCount <= 0) {
            return;
        }

        byte[] bytes = new byte[sampleCount * 2];
        for (int i = 0; i < sampleCount; i++) {
            short sample = samples[i];
            int offset = i * 2;
            bytes[offset] = (byte) (sample & 0xff);
            bytes[offset + 1] = (byte) ((sample >> 8) & 0xff);
        }
        output.write(bytes);
        dataBytes += bytes.length;
    }

    public synchronized File finish() throws IOException {
        if (output == null) {
            return file;
        }

        try {
            output.seek(0L);
            writeHeader(output, dataBytes);
        } finally {
            output.close();
            output = null;
        }
        return file;
    }

    public synchronized void abort() {
        if (output != null) {
            try {
                output.close();
            } catch (IOException ignored) {
            }
            output = null;
        }
        if (file.exists()) {
            file.delete();
        }
    }

    public File getFile() {
        return file;
    }

    private static void writeHeader(RandomAccessFile out, long dataBytes) throws IOException {
        long riffSize = HEADER_BYTES - 8L + dataBytes;
        int byteRate = EngineAudioRecorder.SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;
        int blockAlign = CHANNELS * BITS_PER_SAMPLE / 8;

        out.writeBytes("RIFF");
        writeLittleEndianInt(out, riffSize);
        out.writeBytes("WAVE");
        out.writeBytes("fmt ");
        writeLittleEndianInt(out, 16L);
        writeLittleEndianShort(out, 1);
        writeLittleEndianShort(out, CHANNELS);
        writeLittleEndianInt(out, EngineAudioRecorder.SAMPLE_RATE);
        writeLittleEndianInt(out, byteRate);
        writeLittleEndianShort(out, blockAlign);
        writeLittleEndianShort(out, BITS_PER_SAMPLE);
        out.writeBytes("data");
        writeLittleEndianInt(out, dataBytes);
    }

    private static void writeLittleEndianInt(RandomAccessFile out, long value) throws IOException {
        out.write((int) (value & 0xff));
        out.write((int) ((value >> 8) & 0xff));
        out.write((int) ((value >> 16) & 0xff));
        out.write((int) ((value >> 24) & 0xff));
    }

    private static void writeLittleEndianShort(RandomAccessFile out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }
}
