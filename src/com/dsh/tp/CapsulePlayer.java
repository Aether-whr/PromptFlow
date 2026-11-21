package com.dsh.tp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * 顶部胶囊：贴在状态栏位置的一条黑色小胶囊，像灵动岛。
 * 内容用单行横移方式滚动，点一下暂停，长按关闭，可拖动。
 */
final class CapsulePlayer implements PromptView.Listener {

    private static CapsulePlayer current;

    private final Context ctx;
    private final WindowManager wm;
    private final FrameLayout root;
    private final PromptView prompt;
    private RingView ring;
    private final WindowManager.LayoutParams params;
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaleDetector;
    private int winW;
    private int winH;
    private final int minW;
    private final int minH;
    private final int maxW;
    private final int maxH;
    private boolean scaled;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean dragging;
    private boolean longPressed;
    private long lastTap;
    private final Runnable singleTapTask = new Runnable() {
        @Override
        public void run() {
            togglePause();
        }
    };
    private float downX;
    private float downY;
    private int startX;
    private int startY;
    private final int slop;

    private CapsulePlayer(Context c, int startChar) {
        ctx = c.getApplicationContext();
        Settings settings = new Settings(ctx);
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();

        int w = Math.min((int) (dm.widthPixels * 0.66f), Ui.dp(ctx, 300));
        int h = Ui.dp(ctx, 48);
        winW = w;
        winH = h;
        minW = Ui.dp(ctx, 180);
        minH = Ui.dp(ctx, 40);
        maxW = dm.widthPixels;
        maxH = (int) (dm.heightPixels * 0.4f);

        root = new RoundClip(ctx, h / 2f);
        GradientDrawable back = new GradientDrawable();
        back.setColor(0xF2000000);
        back.setCornerRadius(h / 2f);
        back.setStroke(Ui.dp(ctx, 1), 0x33FFFFFF);
        root.setBackground(back);
        root.setPadding(Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4), 0);
        // 关键：把子 View 裁进圆角里，否则渐隐方块会从圆角处露出直角
        root.setClipToOutline(true);

        // 胶囊里固定用单行横移，字号由高度决定
        prompt = new PromptView(ctx, settings.text(), 0xFFFFFFFF, settings.font(),
                settings.bold(), 75, Math.max(settings.overlaySpeed(), 3), false,
                Settings.MODE_HORIZONTAL);
        prompt.setListener(this);
        prompt.setStartChar(startChar);

        int ringSize = Ui.dp(ctx, 30);
        int gap = Ui.dp(ctx, 10);
        ring = new RingView(ctx, Ui.dp(ctx, 2.5f));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        plp.rightMargin = ringSize + gap + Ui.dp(ctx, 2);
        root.addView(prompt, plp);

        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(
                ringSize, ringSize, Gravity.END | Gravity.CENTER_VERTICAL);
        rlp.rightMargin = gap;
        root.addView(ring, rlp);

        // 两侧渐隐，文字进出更自然
        View leftFade = new View(ctx);
        leftFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF2000000, 0x00F20000}));
        FrameLayout.LayoutParams lf = new FrameLayout.LayoutParams(
                Ui.dp(ctx, 26), FrameLayout.LayoutParams.MATCH_PARENT);
        lf.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        root.addView(leftFade, lf);

        View rightFade = new View(ctx);
        rightFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,
                new int[]{0xF2000000, 0x00F20000}));
        FrameLayout.LayoutParams rf = new FrameLayout.LayoutParams(
                Ui.dp(ctx, 26), FrameLayout.LayoutParams.MATCH_PARENT);
        rf.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        rf.rightMargin = ringSize + gap;
        root.addView(rightFade, rf);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(winW, winH, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = (dm.widthPixels - w) / 2;
        params.y = Ui.dp(ctx, 18);

        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float f = detector.getScaleFactor();
                int cx = params.x + winW / 2;
                int cy = params.y + winH / 2;
                winW = clamp(Math.round(winW * f), minW, maxW);
                winH = clamp(Math.round(winH * f), minH, maxH);
                params.width = winW;
                params.height = winH;
                params.x = cx - winW / 2;
                params.y = cy - winH / 2;
                try {
                    wm.updateViewLayout(root, params);
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                dragging = false;
                scaled = true;
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }
        });
        gestures = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                longPressed = true;
                Toast.makeText(ctx, "胶囊已关闭", Toast.LENGTH_SHORT).show();
                detach();
            }
        });

        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                scaleDetector.onTouchEvent(e);
                if (scaleDetector.isInProgress()) {
                    return true;
                }
                gestures.onTouchEvent(e);
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = params.x;
                        startY = params.y;
                        dragging = false;
                        longPressed = false;
                        scaled = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > slop) {
                            dragging = true;
                        }
                        if (dragging) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            try {
                                wm.updateViewLayout(root, params);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging && !longPressed && !scaled) {
                            onTap();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    /** 单击暂停；双击放大成悬浮窗。 */
    private void onTap() {
        long now = System.currentTimeMillis();
        if (now - lastTap < 300L) {
            lastTap = 0L;
            handler.removeCallbacks(singleTapTask);
            expandToOverlay();
        } else {
            lastTap = now;
            handler.postDelayed(singleTapTask, 280L);
        }
    }

    private void expandToOverlay() {
        prompt.pause();
        root.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f)
                .setDuration(130L)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        detach();
                        OverlayPlayer.show(ctx, 0);
                    }
                }).start();
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static void show(Context c, int startChar) {
        if (current != null) {
            Toast.makeText(c, "胶囊已经在显示了", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            current = new CapsulePlayer(c, startChar);
            current.wm.addView(current.root, current.params);
            current.root.setAlpha(0f);
            current.root.setScaleX(0.86f);
            current.root.setScaleY(0.86f);
            current.root.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            current.root.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(200L)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
            current.prompt.play();
            Toast.makeText(c, "点一下暂停，长按关闭", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            current = null;
            Toast.makeText(c, "胶囊打不开：" + e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePause() {
        if (prompt.isPlaying()) {
            prompt.pause();
        } else {
            prompt.play();
        }
    }

    private void detach() {
        handler.removeCallbacks(singleTapTask);
        try {
            prompt.pause();
        } catch (Exception ignored) {
        }
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
        if (current == this) {
            current = null;
        }
    }

    @Override
    public void onProgress(float fraction, int line, int total) {
        if (ring != null) {
            ring.setTarget(fraction);
        }
    }

    @Override
    public void onFinished() {
        // 胶囊里循环滚动，不打断
        prompt.restart();
        if (ring != null) {
            ring.setTarget(0f);
        }
    }

    /** 把子 View 画进圆角路径里，比 clipToOutline 更保险（部分机型对硬件层不生效）。 */
    private static final class RoundClip extends FrameLayout {
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final float radius;

        RoundClip(Context c, float radius) {
            super(c);
            this.radius = radius;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            rect.set(0, 0, getWidth(), getHeight());
            path.reset();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(path);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }
    }

    /** 环形进度：细描边，数值变化时平滑追上去。 */
    private static final class RingView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float target;
        private float current;

        RingView(Context c, int strokeWidth) {
            super(c);
            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setStrokeWidth(strokeWidth);
            bgPaint.setColor(0x33FFFFFF);
            fgPaint.setStyle(Paint.Style.STROKE);
            fgPaint.setStrokeWidth(strokeWidth);
            fgPaint.setStrokeCap(Paint.Cap.ROUND);
            fgPaint.setColor(0xFF4ADE80);
        }

        void setTarget(float p) {
            target = Math.max(0f, Math.min(1f, p));
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float inset = fgPaint.getStrokeWidth() / 2f + 1f;
            oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawArc(oval, -90f, 360f, false, bgPaint);
            if (current > 0.001f) {
                canvas.drawArc(oval, -90f, 360f * current, false, fgPaint);
            }
            if (Math.abs(target - current) > 0.003f) {
                current += (target - current) * 0.28f;
                postInvalidateOnAnimation();
            } else if (current != target) {
                current = target;
                postInvalidateOnAnimation();
            }
        }
    }
}
