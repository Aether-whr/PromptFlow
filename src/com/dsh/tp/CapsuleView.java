package com.dsh.tp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * 胶囊本体：全部用一块 Canvas 画（背景、文字、渐隐、进度环），
 * 不再叠子 View，避免圆角处露出方形黑边。
 */
class CapsuleView extends View {

    private static final int BG = 0xF2000000;
    private static final int BORDER = 0x33FFFFFF;
    private static final int FADE_W_DP = 26;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringFgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final RectF ringRect = new RectF();
    private final Paint.FontMetrics fm = new Paint.FontMetrics();

    private final String text;
    private final float speedPx;
    private final int ringSize;
    private final int ringMargin;
    private final int padLeft;
    private final int fadeWidth;

    private float textWidth;
    private float offset;
    private boolean playing = true;
    private long lastNanos;
    private float ringTarget;
    private float ringShown;

    CapsuleView(Context c, String raw, float fontSize, float charsPerSecond,
                int ringSize, int ringMargin, int padLeft) {
        super(c);
        text = raw == null ? "" : raw.replace('\n', ' ').replace('\r', ' ').trim();
        this.ringSize = ringSize;
        this.ringMargin = ringMargin;
        this.padLeft = padLeft;
        fadeWidth = Ui.dp(c, FADE_W_DP);

        textPaint.setColor(0xFFF3F4F6);
        textPaint.setTextSize(fontSize);
        textPaint.getFontMetrics(fm);
        speedPx = Math.max(24f, fontSize * charsPerSecond * 1.6f);

        bgPaint.setColor(BG);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Ui.dp(c, 1));
        borderPaint.setColor(BORDER);

        ringBgPaint.setStyle(Paint.Style.STROKE);
        ringBgPaint.setStrokeWidth(Ui.dp(c, 2.5f));
        ringBgPaint.setColor(0x33FFFFFF);
        ringFgPaint.setStyle(Paint.Style.STROKE);
        ringFgPaint.setStrokeWidth(Ui.dp(c, 2.5f));
        ringFgPaint.setStrokeCap(Paint.Cap.ROUND);
        ringFgPaint.setColor(0xFF4ADE80);

        textWidth = textPaint.measureText(text);
    }

    boolean isPlaying() {
        return playing;
    }

    void toggle() {
        playing = !playing;
        lastNanos = 0L;
        invalidate();
    }

    void play() {
        playing = true;
        lastNanos = 0L;
        invalidate();
    }

    void pause() {
        playing = false;
        invalidate();
    }

    void setProgress(float p) {
        ringTarget = Math.max(0f, Math.min(1f, p));
        postInvalidateOnAnimation();
    }

    void seekToChar(int charOffset) {
        if (charOffset <= 0 || text.length() == 0) {
            return;
        }
        int cut = Math.min(charOffset, text.length());
        offset = textPaint.measureText(text.substring(0, cut));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        textWidth = textPaint.measureText(text);
        offset = Math.min(offset, textWidth);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        if (playing) {
            long now = System.nanoTime();
            float dt = lastNanos == 0L ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            offset += speedPx * dt;
            if (offset > textWidth + padLeft + w) {
                offset = 0f;
            }
            ringTarget = textWidth > 0f ? Math.min(1f, offset / textWidth) : 0f;
            postInvalidateOnAnimation();
        } else {
            lastNanos = 0L;
        }

        float radius = h / 2f;
        rect.set(0, 0, w, h);
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        int save = canvas.save();
        canvas.clipPath(clipPath);

        canvas.drawRect(rect, bgPaint);

        float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, padLeft - offset, baseline, textPaint);

        // 左边缘渐隐
        fadePaint.setShader(new LinearGradient(0, 0, fadeWidth, 0,
                BG, 0x00F20000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, fadeWidth, h, fadePaint);
        // 右边缘渐隐（进度环之前）
        float right = w - ringSize - ringMargin * 2;
        fadePaint.setShader(new LinearGradient(right - fadeWidth, 0, right, 0,
                0x00F20000, BG, Shader.TileMode.CLAMP));
        canvas.drawRect(Math.max(0, right - fadeWidth), 0, Math.max(0, right), h, fadePaint);
        fadePaint.setShader(null);

        // 进度环
        float cx = w - ringMargin - ringSize / 2f;
        float cy = h / 2f;
        float r = ringSize / 2f - ringBgPaint.getStrokeWidth() / 2f - 1f;
        ringRect.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawArc(ringRect, -90f, 360f, false, ringBgPaint);
        if (ringShown > 0.004f) {
            canvas.drawArc(ringRect, -90f, 360f * ringShown, false, ringFgPaint);
        }

        canvas.restoreToCount(save);

        // 描边画在裁剪外，保持 1px 完整
        canvas.drawRoundRect(rect, radius, radius, borderPaint);

        if (Math.abs(ringTarget - ringShown) > 0.003f) {
            ringShown += (ringTarget - ringShown) * 0.28f;
            postInvalidateOnAnimation();
        } else if (ringShown != ringTarget) {
            ringShown = ringTarget;
            postInvalidateOnAnimation();
        }
    }
}
