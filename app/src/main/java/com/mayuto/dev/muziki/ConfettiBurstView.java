package com.mayuto.dev.muziki;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.Random;

/**
 * MUZIKI Video's little surprise: a quick, tasteful confetti burst that
 * plays when a video is watched all the way to the end. Nobody asked for
 * it - it's just a small reward for finishing what you started, and a
 * bit of personality for a screen that's otherwise all business (seek
 * bars, brightness, volume). Pure canvas drawing, no external animation
 * library, self-contained and easy to lift out if it's ever not wanted.
 */
public class ConfettiBurstView extends View {

    private static final int PARTICLE_COUNT = 60;
    private static final int[] COLORS = {
            0xFFFF4081, // MUZIKI pink
            0xFFC238FF, // MUZIKI purple
            0xFF5B6DFF, // MUZIKI blue
            0xFFFFD54F, // gold
            0xFF69F0AE  // mint
    };

    private final ArrayList<Particle> particles = new ArrayList<Particle>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private ValueAnimator animator;
    private Runnable onFinished;

    public ConfettiBurstView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public ConfettiBurstView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setOnFinishedListener(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    /** Fires the burst outward from the given point (usually screen center). */
    public void burst(final float originX, final float originY) {
        particles.clear();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(new Particle(originX, originY, random));
        }

        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1.4f); // seconds of flight time
        animator.setDuration(1400);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float seconds = (Float) animation.getAnimatedValue();
                for (Particle p : particles) {
                    p.advance(seconds);
                }
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                particles.clear();
                invalidate();
                if (onFinished != null) onFinished.run();
            }
        });
        animator.start();
    }

    public void cancelBurst() {
        if (animator != null) animator.cancel();
        particles.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Particle p : particles) {
            paint.setColor(p.color);
            paint.setAlpha(p.alpha);
            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.rotation);
            canvas.drawRect(p.rect, paint);
            canvas.restore();
        }
    }

    /**
     * One confetti rectangle: simple projectile motion (initial velocity
     * + gravity) starting from the burst origin, fading out near the end
     * of its flight so it reads as a quick sparkle rather than debris
     * falling forever off-screen.
     */
    private static final class Particle {
        final int color;
        final float originX, originY;
        final float vx, vy;
        final float gravity;
        final float rotationSpeed;
        final RectF rect;

        float x, y, rotation;
        int alpha = 255;

        Particle(float originX, float originY, Random random) {
            this.originX = originX;
            this.originY = originY;
            this.x = originX;
            this.y = originY;

            color = ConfettiBurstView.COLORS[random.nextInt(ConfettiBurstView.COLORS.length)];
            double angle = random.nextDouble() * Math.PI * 2;
            float speed = 260 + random.nextFloat() * 340;
            vx = (float) Math.cos(angle) * speed;
            vy = (float) Math.sin(angle) * speed - 260; // bias upward like a real burst
            gravity = 520 + random.nextFloat() * 200;
            rotationSpeed = (random.nextFloat() - 0.5f) * 900;

            float sizePx = 6 + random.nextFloat() * 7;
            rect = new RectF(-sizePx / 2f, -sizePx / 4f, sizePx / 2f, sizePx / 4f);
        }

        void advance(float seconds) {
            x = originX + vx * seconds;
            y = originY + vy * seconds + 0.5f * gravity * seconds * seconds;
            rotation = rotationSpeed * seconds;

            float fadeStart = 0.9f;
            float fadeEnd = 1.4f;
            if (seconds <= fadeStart) {
                alpha = 255;
            } else {
                float fadeT = (seconds - fadeStart) / (fadeEnd - fadeStart);
                alpha = (int) (255 * (1f - Math.min(1f, Math.max(0f, fadeT))));
            }
        }
    }
}
