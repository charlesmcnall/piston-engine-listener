package com.chaz.pistonlistener;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public final class SpectrumView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] spectrum = new float[64];

    public SpectrumView(Context context) {
        super(context);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setSpectrum(float[] nextSpectrum) {
        if (nextSpectrum == null || nextSpectrum.length == 0) {
            spectrum = new float[64];
        } else {
            spectrum = nextSpectrum.clone();
        }
        invalidate();
    }

    private void init() {
        setMinimumHeight(dp(180));
        barPaint.setColor(Color.rgb(15, 118, 110));
        gridPaint.setColor(Color.rgb(203, 213, 225));
        gridPaint.setStrokeWidth(1.0f);
        textPaint.setColor(Color.rgb(71, 85, 105));
        textPaint.setTextSize(dp(11));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int left = dp(8);
        int right = width - dp(8);
        int top = dp(10);
        int bottom = height - dp(24);

        canvas.drawColor(Color.rgb(248, 250, 252));

        for (int i = 0; i <= 3; i++) {
            float y = top + (bottom - top) * i / 3.0f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        if (spectrum.length == 0) {
            return;
        }

        float gap = dp(2);
        float barWidth = Math.max(2.0f, (right - left - gap * (spectrum.length - 1)) / spectrum.length);
        for (int i = 0; i < spectrum.length; i++) {
            float value = Math.max(0.0f, Math.min(1.0f, spectrum[i]));
            float x = left + i * (barWidth + gap);
            float y = bottom - value * (bottom - top);
            canvas.drawRoundRect(x, y, x + barWidth, bottom, dp(2), dp(2), barPaint);
        }

        canvas.drawText("0", left, height - dp(7), textPaint);
        canvas.drawText("3 kHz", width / 2.0f - dp(14), height - dp(7), textPaint);
        canvas.drawText("6 kHz", right - dp(32), height - dp(7), textPaint);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

