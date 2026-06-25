package com.chaz.pistonlistener;

public final class FftAnalyzer {
    private static final double MIN_ANALYZED_HZ = 20.0;
    private static final double MAX_ANALYZED_HZ = 6000.0;
    private static final int SPECTRUM_BARS = 64;

    private final int sampleRate;
    private final int fftSize;
    private final double[] window;
    private final double[] real;
    private final double[] imaginary;

    public FftAnalyzer(int sampleRate, int fftSize) {
        this.sampleRate = sampleRate;
        this.fftSize = fftSize;
        this.window = new double[fftSize];
        this.real = new double[fftSize];
        this.imaginary = new double[fftSize];

        for (int i = 0; i < fftSize; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (fftSize - 1));
        }
    }

    public synchronized SpectrumFeatures analyze(short[] pcm) {
        double sumSquares = 0.0;
        double peakAbs = 0.0;
        int clipped = 0;
        int flatTop = 0;

        for (int i = 0; i < fftSize; i++) {
            double sample = pcm[i] / 32768.0;
            double absSample = Math.abs(sample);
            sumSquares += sample * sample;
            peakAbs = Math.max(peakAbs, absSample);
            if (Math.abs(pcm[i]) >= 32760) {
                clipped++;
            }
            if (i > 0 && Math.abs(pcm[i]) >= 32000 && Math.abs(pcm[i] - pcm[i - 1]) <= 1) {
                flatTop++;
            }
            real[i] = sample * window[i];
            imaginary[i] = 0.0;
        }

        fft(real, imaginary);

        double totalEnergy = 0.0;
        double centroidWeighted = 0.0;
        double dominantPower = 0.0;
        double dominantHz = 0.0;
        double band20To120 = 0.0;
        double band120To600 = 0.0;
        double band600To2500 = 0.0;
        double band2500To6000 = 0.0;
        double[] barPower = new double[SPECTRUM_BARS];

        for (int bin = 1; bin < fftSize / 2; bin++) {
            double hz = bin * (double) sampleRate / fftSize;
            if (hz < MIN_ANALYZED_HZ || hz > MAX_ANALYZED_HZ) {
                continue;
            }

            double power = real[bin] * real[bin] + imaginary[bin] * imaginary[bin];
            totalEnergy += power;
            centroidWeighted += hz * power;

            if (power > dominantPower) {
                dominantPower = power;
                dominantHz = hz;
            }

            if (hz < 120.0) {
                band20To120 += power;
            } else if (hz < 600.0) {
                band120To600 += power;
            } else if (hz < 2500.0) {
                band600To2500 += power;
            } else {
                band2500To6000 += power;
            }

            int bar = (int) Math.floor((hz / MAX_ANALYZED_HZ) * SPECTRUM_BARS);
            if (bar >= 0 && bar < SPECTRUM_BARS && power > barPower[bar]) {
                barPower[bar] = power;
            }
        }

        float[] bars = normalizeBars(barPower);
        double safeTotal = Math.max(totalEnergy, 1.0e-12);
        double rms = Math.sqrt(sumSquares / fftSize);
        double rmsDbfs = 20.0 * Math.log10(Math.max(rms, 1.0e-9));
        double peakDbfs = 20.0 * Math.log10(Math.max(peakAbs, 1.0e-9));
        double crestFactorDb = peakDbfs - rmsDbfs;
        double clippedPercent = 100.0 * clipped / fftSize;
        double flatTopPercent = 100.0 * flatTop / fftSize;
        double centroidHz = centroidWeighted / safeTotal;

        return new SpectrumFeatures(
                System.currentTimeMillis(),
                "Unknown",
                0.0,
                "Unknown",
                0.0,
                "None known",
                "",
                rmsDbfs,
                peakDbfs,
                crestFactorDb,
                clippedPercent,
                flatTopPercent,
                dominantHz,
                centroidHz,
                band20To120 / safeTotal,
                band120To600 / safeTotal,
                band600To2500 / safeTotal,
                band2500To6000 / safeTotal,
                0.0,
                bars
        );
    }

    private static float[] normalizeBars(double[] power) {
        double max = 0.0;
        for (double value : power) {
            if (value > max) {
                max = value;
            }
        }

        float[] bars = new float[power.length];
        if (max <= 0.0) {
            return bars;
        }

        for (int i = 0; i < power.length; i++) {
            double relativeDb = 10.0 * Math.log10(Math.max(power[i] / max, 1.0e-9));
            bars[i] = (float) clamp((relativeDb + 60.0) / 60.0, 0.0, 1.0);
        }
        return bars;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void fft(double[] real, double[] imaginary) {
        int n = real.length;

        for (int j = 1, i = 0; j < n; j++) {
            int bit = n >> 1;
            while ((i & bit) != 0) {
                i ^= bit;
                bit >>= 1;
            }
            i ^= bit;

            if (j < i) {
                double tempReal = real[j];
                real[j] = real[i];
                real[i] = tempReal;

                double tempImaginary = imaginary[j];
                imaginary[j] = imaginary[i];
                imaginary[i] = tempImaginary;
            }
        }

        for (int length = 2; length <= n; length <<= 1) {
            double angle = -2.0 * Math.PI / length;
            double wLengthReal = Math.cos(angle);
            double wLengthImaginary = Math.sin(angle);

            for (int i = 0; i < n; i += length) {
                double wReal = 1.0;
                double wImaginary = 0.0;

                for (int j = 0; j < length / 2; j++) {
                    int even = i + j;
                    int odd = even + length / 2;

                    double oddReal = real[odd] * wReal - imaginary[odd] * wImaginary;
                    double oddImaginary = real[odd] * wImaginary + imaginary[odd] * wReal;

                    real[odd] = real[even] - oddReal;
                    imaginary[odd] = imaginary[even] - oddImaginary;
                    real[even] += oddReal;
                    imaginary[even] += oddImaginary;

                    double nextWReal = wReal * wLengthReal - wImaginary * wLengthImaginary;
                    wImaginary = wReal * wLengthImaginary + wImaginary * wLengthReal;
                    wReal = nextWReal;
                }
            }
        }
    }
}
