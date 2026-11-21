package com.dsh.tp;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
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
    private final WindowManager.LayoutParams params;
    private final GestureDetector gestures;

    private boolean dragging;
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

        root = new FrameLayout(ctx);
        GradientDrawable back = new GradientDrawable();
        back.setColor(0xF2000000);
        back.setCornerRadius(h / 2f);
        back.setStroke(Ui.dp(ctx, 1), 0x33FFFFFF);
        root.setBackground(back);
        root.setPadding(Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4), 0);

        // 胶囊里固定用单行横移，字号由高度决定
        prompt = new PromptView(ctx, settings.text(), 0xFFFFFFFF, settings.font(),
                settings.bold(), 75, Math.max(settings.overlaySpeed(), 3), false,
                Settings.MODE_HORIZONTAL);
        prompt.setListener(this);
        prompt.setStartChar(startChar);
        root.addView(prompt, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(w, h, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = (dm.widthPixels - w) / 2;
        params.y = Ui.dp(ctx, 18);

        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        gestures = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                togglePause();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Toast.makeText(ctx, "胶囊已关闭", Toast.LENGTH_SHORT).show();
                detach();
            }
        });

        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                gestures.onTouchEvent(e);
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = params.x;
                        startY = params.y;
                        dragging = false;
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
                    default:
                        return true;
                }
            }
        });
    }

    static void show(Context c, int startChar) {
        if (current != null) {
            Toast.makeText(c, "胶囊已经在显示了", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            current = new CapsulePlayer(c, startChar);
            current.wm.addView(current.root, current.params);
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
    }

    @Override
    public void onFinished() {
        // 胶囊里循环滚动，不打断
        prompt.restart();
    }
}
