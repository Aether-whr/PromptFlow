package com.dsh.tp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏提词视图：把文字按屏幕宽度折行，匀速向上滚动，滚到底就停在末尾。
 */
class PromptView extends View {

    interface Listener {
        void onProgress(float fraction, int line, int total);

        void onFinished();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint.FontMetrics fm = new Paint.FontMetrics();
    private final List<String> lines = new ArrayList<>();

    private final String raw;

    private float padX;
    private float padTop;
    private float lineH;
    private float offset;
    private float maxOffset;
    private float charsPerLine = 4f;
    private float cps = 4f;
    private final float sizePercent;
    private final boolean mirror;
    private final int mode;
    private String flat = "";
    private float flatWidth;
    private float baselineY;
    private float startOffset;
    private boolean singleInit;

    private boolean playing;
    private boolean finished;
    private boolean pendingPlay;
    private boolean ready;
    private long lastNanos;
    private long lastProgressNanos;
    private int startChar;

    private Listener listener;
    private boolean reported;

    PromptView(Context c, String text, int textColor, int font, boolean bold,
               int sizePercent, int cps, boolean mirror, int mode) {
        super(c);
        raw = text == null ? "" : text;
        this.sizePercent = sizePercent;
        this.cps = cps;
        this.mirror = mirror;
        this.mode = mode;
        paint.setColor(textColor);
        paint.setTypeface(Settings.typeface(font, bold));
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setStartChar(int charOffset) {
        startChar = Math.max(0, charOffset);
    }

    void setCps(int v) {
        cps = v;
    }

    void play() {
        if (maxOffset <= 0f) {
            // 还没排版：等 onSizeChanged 排好版再真正开始
            pendingPlay = true;
            playing = false;
            return;
        }
        if (offset >= maxOffset) {
            offset = startOffset;
            finished = false;
        }
        playing = true;
        lastNanos = 0L;
        invalidate();
    }

    void pause() {
        playing = false;
        lastNanos = 0L;
    }

    void restart() {
        offset = startOffset;
        finished = false;
        playing = true;
        lastNanos = 0L;
        notifyProgress(true);
        invalidate();
    }

    boolean isPlaying() {
        return playing;
    }

    boolean isFinished() {
        return finished;
    }

    float fraction() {
        return maxOffset > 0f ? offset / maxOffset : 1f;
    }

    int currentLine() {
        return lineH > 0f ? Math.min(Math.max(1, lines.size()), (int) (offset / lineH) + 1) : 1;
    }

    /** 当前屏幕顶部那一行的文字，给通知/胶囊用。 */
    String currentLineText() {
        if (horizontal() || lines.isEmpty() || lineH <= 0f) {
            return "";
        }
        int idx = Math.max(0, Math.min(lines.size() - 1, (int) (offset / lineH)));
        return lines.get(idx).trim();
    }

    int totalLines() {
        return lines.size();
    }

    void seekToFraction(float f) {
        if (maxOffset <= 0f) {
            return;
        }
        float v = Math.max(0f, Math.min(1f, f));
        offset = v * maxOffset;
        finished = offset >= maxOffset;
        lastNanos = 0L;
        notifyProgress(true);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        ready = w > 0 && h > 0;
        if (ready) {
            try {
                layoutText(w, h);
            } catch (Throwable t) {
                report(t);
            }
        }
    }

    boolean horizontal() {
        return mode == Settings.MODE_HORIZONTAL;
    }

    private void layoutText(int w, int h) {
        if (mode == Settings.MODE_HORIZONTAL) {
            layoutSingleLine(w, h);
            return;
        }
        padX = Math.max(Ui.dp(getContext(), 10), w * 0.055f);
        padTop = h * 0.08f;
        float usable = Math.max(1f, w - padX * 2f);

        // 100% 时一行 4 个字；同时保证单行高度不超过屏幕
        float maxFont = Math.min(usable / 4f, h * 0.62f);
        float size = Math.max(Ui.sp(getContext(), 14f), maxFont * sizePercent / 100f);
        paint.setTextSize(size);
        paint.getFontMetrics(fm);
        lineH = (fm.bottom - fm.top) * 1.18f;
        charsPerLine = Math.max(1f, usable / size);

        lines.clear();
        String[] paras = raw.replace("\r", "").split("\n", -1);
        for (String p : paras) {
            if (p.length() == 0) {
                lines.add("");
                continue;
            }
            int i = 0;
            while (i < p.length()) {
                int n = paint.breakText(p, i, p.length(), true, usable, null);
                if (n <= 0) {
                    n = 1;
                }
                lines.add(p.substring(i, i + n));
                i += n;
            }
        }

        float content = lines.size() * lineH + h * 0.4f;
        maxOffset = Math.max(0f, content - h);
        if (maxOffset <= 0f) {
            offset = 0f;
        }
        if (startChar > 0) {
            applyStartLine(h);
        }
        offset = Math.min(offset, maxOffset);
        invalidate();
        if (pendingPlay && maxOffset > 0f) {
            pendingPlay = false;
            play();
        }
    }

    /** 单行横移：所有文字连成一条线，从右往左滚。 */
    private void layoutSingleLine(int w, int h) {
        padX = Ui.dp(getContext(), 12);
        float size = Math.min(w / 4f, h * 0.55f) * sizePercent / 100f;
        size = Math.max(Ui.sp(getContext(), 14f), size);
        paint.setTextSize(size);
        paint.getFontMetrics(fm);
        lineH = fm.bottom - fm.top;
        charsPerLine = Math.max(1f, w / size);
        flat = raw.replace("\r", "").replace('\n', ' ');
        flatWidth = paint.measureText(flat);
        baselineY = h / 2f - (fm.ascent + fm.descent) / 2f;
        maxOffset = Math.max(0f, w + flatWidth);
        startOffset = Math.min(w, maxOffset);
        if (!singleInit) {
            singleInit = true;
            offset = startOffset;
        }
        if (startChar > 0) {
            int cut = Math.min(startChar, flat.length());
            offset = Math.min(maxOffset, w + paint.measureText(flat.substring(0, cut)));
            startChar = 0;
            finished = false;
        }
        offset = Math.min(offset, maxOffset);
        invalidate();
        if (pendingPlay && maxOffset > 0f) {
            pendingPlay = false;
            play();
        }
    }

    private void applyStartLine(int h) {
        int acc = 0;
        int idx = 0;
        for (; idx < lines.size(); idx++) {
            int len = lines.get(idx).length();
            if (acc + len > startChar) {
                break;
            }
            acc += len;
        }
        offset = Math.max(0f, Math.min(maxOffset, idx * lineH - h * 0.12f));
        startChar = 0;
        finished = false;
    }

    private float speedPx() {
        if (mode == Settings.MODE_HORIZONTAL) {
            return paint.getTextSize() * cps;
        }
        return lineH * cps / Math.max(1f, charsPerLine);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            drawFrame(canvas);
        } catch (Throwable t) {
            report(t);
        }
    }

    private void report(Throwable t) {
        if (reported) {
            return;
        }
        reported = true;
        Crash.save(getContext(), t);
        if (getContext() instanceof android.app.Activity) {
            try {
                Crash.show((android.app.Activity) getContext(), t);
            } catch (Throwable ignored) {
            }
        }
    }

    private void drawFrame(Canvas canvas) {
        if (mode == Settings.MODE_HORIZONTAL) {
            drawSingleLine(canvas);
            return;
        }
        if (!ready || lines.isEmpty()) {
            return;
        }

        if (playing) {
            long now = System.nanoTime();
            float dt = lastNanos == 0L ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            offset += speedPx() * dt;
            if (offset >= maxOffset) {
                offset = maxOffset;
                playing = false;
                finished = true;
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onFinished();
                        }
                    }
                });
            }
            postInvalidateOnAnimation();
            notifyProgress(false);
        } else {
            lastNanos = 0L;
        }

        int first = Math.max(0, (int) (offset / lineH) - 2);
        int last = Math.min(lines.size() - 1, (int) ((offset + getHeight()) / lineH) + 2);

        canvas.save();
        if (mirror) {
            canvas.scale(-1f, 1f, getWidth() / 2f, 0f);
        }
        float top = padTop - offset;
        for (int i = first; i <= last; i++) {
            canvas.drawText(lines.get(i), padX, top + i * lineH - fm.top, paint);
        }
        canvas.restore();
    }

    private void drawSingleLine(Canvas canvas) {
        if (!ready || flat.length() == 0) {
            return;
        }
        if (playing) {
            long now = System.nanoTime();
            float dt = lastNanos == 0L ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            offset += speedPx() * dt;
            if (offset >= maxOffset) {
                offset = maxOffset;
                playing = false;
                finished = true;
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onFinished();
                        }
                    }
                });
            }
            postInvalidateOnAnimation();
            notifyProgress(false);
        } else {
            lastNanos = 0L;
        }

        canvas.save();
        if (mirror) {
            canvas.scale(-1f, 1f, getWidth() / 2f, 0f);
        }
        canvas.drawText(flat, getWidth() - offset, baselineY, paint);
        canvas.restore();
    }

    private void notifyProgress(boolean force) {
        if (listener == null) {
            return;
        }
        long now = System.nanoTime();
        if (!force && now - lastProgressNanos < 90000000L) {
            return;
        }
        lastProgressNanos = now;
        listener.onProgress(fraction(), currentLine(), lines.size());
    }
}
