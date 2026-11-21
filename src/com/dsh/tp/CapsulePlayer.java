package com.dsh.tp;

import android.content.Context;
import android.graphics.PixelFormat;
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
import android.widget.Toast;

/**
 * 顶部胶囊（自绘浮动条）：一块 Canvas 画完所有内容，可拖动、双指缩放、点按暂停。
 */
final class CapsulePlayer {

    private static CapsulePlayer current;

    private final Context ctx;
    private final WindowManager wm;
    private final CapsuleView view;
    private final WindowManager.LayoutParams params;
    private final Settings settings;
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaleDetector;
    private final int baseH;
    private final int minW;
    private final int minH;
    private final int maxW;
    private final int maxH;
    private final int slop;

    private int winW;
    private int winH;
    private boolean dragging;
    private boolean longPressed;
    private boolean scaled;
    private long lastTap;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable singleTapTask = new Runnable() {
        @Override
        public void run() {
            if (current == CapsulePlayer.this) {
                view.toggle();
            }
        }
    };
    private float downX;
    private float downY;
    private int startX;
    private int startY;

    private CapsulePlayer(Context c, int startChar) {
        ctx = c.getApplicationContext();
        settings = new Settings(ctx);
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();

        float userScale = settings.capsuleScale();
        int baseW = Math.min((int) (dm.widthPixels * 0.66f), Ui.dp(ctx, 300));
        baseH = Ui.dp(ctx, 48);
        winW = Math.min(dm.widthPixels, Math.round(baseW * userScale));
        winH = Math.max(Ui.dp(ctx, 40), Math.round(baseH * userScale));
        minW = Ui.dp(ctx, 180);
        minH = Ui.dp(ctx, 40);
        maxW = dm.widthPixels;
        maxH = (int) (dm.heightPixels * 0.4f);

        int ringSize = Ui.dp(ctx, 28);
        int ringMargin = Ui.dp(ctx, 9);
        float fontSize = winH * 0.42f;

        view = new CapsuleView(ctx, settings.text(), fontSize, settings.overlaySpeed(),
                ringSize, ringMargin, Ui.dp(ctx, 18));
        view.seekToChar(startChar);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(winW, winH, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = (dm.widthPixels - winW) / 2;
        params.y = Ui.dp(ctx, 18);

        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();

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
                    wm.updateViewLayout(view, params);
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
                settings.capsuleScale((float) winH / baseH);
            }
        });

        view.setOnTouchListener(new View.OnTouchListener() {
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
                                wm.updateViewLayout(view, params);
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
                        return false;
                }
            }
        });
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
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
        view.pause();
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        view.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f)
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

    static void show(Context c, int startChar) {
        if (current != null) {
            Toast.makeText(c, "胶囊已经在显示了", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            current = new CapsulePlayer(c, startChar);
            current.wm.addView(current.view, current.params);
            current.view.setAlpha(0f);
            current.view.setScaleX(0.86f);
            current.view.setScaleY(0.86f);
            current.view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            current.view.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(200L)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
            current.view.play();
            Toast.makeText(c, "点一下暂停，双击放大，长按关闭", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            current = null;
            Toast.makeText(c, "胶囊打不开：" + e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void detach() {
        handler.removeCallbacks(singleTapTask);
        try {
            view.pause();
        } catch (Exception ignored) {
        }
        try {
            wm.removeView(view);
        } catch (Exception ignored) {
        }
        if (current == this) {
            current = null;
        }
    }
}
