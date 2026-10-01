package com.mayuto.dev.muziki;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.view.View;

/**
 * Dependency-free real-time player visualizer.
 * Uses Android's built-in Visualizer API: waveform + FFT spectrum + circular pulse.
 */
public class AudioVisualizerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path waveform = new Path();
    private final float[] bars = new float[32];
    private byte[] wave;
    private byte[] fft;
    private Visualizer visualizer;
    private int audioSession = -1;
    private boolean running;
    private float level;
    private long lastFrame;

    public AudioVisualizerView(Context context) { super(context); init(); }
    public AudioVisualizerView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public AudioVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setVisibility(VISIBLE);
    }

    public void attachToSession(int sessionId) {
        if (sessionId <= 0 || sessionId == audioSession) return;
        releaseVisualizer();
        audioSession = sessionId;
        try {
            visualizer = new Visualizer(sessionId);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] bytes, int samplingRate) {
                    wave = bytes.clone();
                    updateLevelFromWave(bytes);
                    invalidate();
                }
                @Override public void onFftDataCapture(Visualizer v, byte[] bytes, int samplingRate) {
                    fft = bytes.clone();
                    calculateBars(bytes);
                    invalidate();
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true);
            visualizer.setEnabled(true);
            running = true;
            invalidate();
        } catch (Throwable ignored) {
            visualizer = null;
            running = false;
        }
    }

    private void updateLevelFromWave(byte[] data) {
        if (data == null || data.length == 0) return;
        float sum = 0f;
        int step = Math.max(1, data.length / 64);
        for (int i = 0; i < data.length; i += step) {
            float x = (data[i] & 0xFF) - 128f;
            sum += x * x;
        }
        float rms = (float)Math.sqrt(sum / Math.max(1, data.length / step)) / 128f;
        level = Math.max(0f, Math.min(1f, rms));
    }

    private void calculateBars(byte[] data) {
        if (data == null || data.length < 4) return;
        for (int i = 0; i < bars.length; i++) {
            int idx = 2 + (i * (data.length - 2)) / bars.length;
            if (idx + 1 >= data.length) idx = data.length - 2;
            float real = data[idx];
            float imag = data[idx + 1];
            float mag = (float)Math.sqrt(real * real + imag * imag);
            float normalized = Math.min(1f, (float)(Math.log10(1 + mag) / 2.0));
            bars[i] = bars[i] * 0.68f + normalized * 0.32f;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (!running) return;

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float baseRadius = Math.min(getWidth(), getHeight()) * 0.34f;
        float pulse = baseRadius * (0.035f + level * 0.12f);

        // Circular visualizer ring.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(0x55FF4081);
        canvas.drawCircle(cx, cy, baseRadius + pulse, paint);
        paint.setStrokeWidth(5f);
        paint.setColor(0xAAFF4081);
        canvas.drawArc(new RectF(cx - baseRadius, cy - baseRadius, cx + baseRadius, cy + baseRadius),
                -90, 360f * Math.min(1f, level * 1.8f + 0.08f), false, paint);

        // FFT spectrum bars around the circle.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(0x99FF4081);
        for (int i = 0; i < bars.length; i++) {
            double angle = (Math.PI * 2.0 * i / bars.length) - Math.PI / 2.0;
            float inner = baseRadius + 7f;
            float outer = inner + 8f + bars[i] * baseRadius * 0.48f;
            float x1 = cx + (float)Math.cos(angle) * inner;
            float y1 = cy + (float)Math.sin(angle) * inner;
            float x2 = cx + (float)Math.cos(angle) * outer;
            float y2 = cy + (float)Math.sin(angle) * outer;
            canvas.drawLine(x1, y1, x2, y2, paint);
        }

        // Real-time waveform across the lower portion.
        if (wave != null && wave.length > 1) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(0xDDFF4081);
            waveform.reset();
            float yMid = getHeight() - Math.min(52f, getHeight() * 0.18f);
            float half = Math.min(24f, getHeight() * 0.09f) + level * 16f;
            for (int i = 0; i < wave.length; i++) {
                float x = (i * 1f / (wave.length - 1)) * getWidth();
                float y = yMid + (((wave[i] & 0xFF) - 128f) / 128f) * half;
                if (i == 0) waveform.moveTo(x, y); else waveform.lineTo(x, y);
            }
            canvas.drawPath(waveform, paint);
        }

        // Gentle idle decay keeps effects smooth between callbacks.
        level *= (float)Math.pow(0.94, dt * 60f);
        for (int i = 0; i < bars.length; i++) bars[i] *= (float)Math.pow(0.985, dt * 60f);
        postInvalidateDelayed(33);
    }

    public float getLevel() { return level; }

    public void releaseVisualizer() {
        running = false;
        if (visualizer != null) {
            try { visualizer.setEnabled(false); } catch (Throwable ignored) { }
            try { visualizer.release(); } catch (Throwable ignored) { }
            visualizer = null;
        }
        audioSession = -1;
        wave = null;
        fft = null;
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        releaseVisualizer();
        super.onDetachedFromWindow();
    }
}
