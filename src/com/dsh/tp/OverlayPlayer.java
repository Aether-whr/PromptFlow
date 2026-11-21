package com.dsh.tp;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 悬浮窗播放：把提词画面挂在其他应用上面，可拖动，点一下暂停。 */
final class OverlayPlayer implements PromptView.Listener {

    private static OverlayPlayer current;

    private final Context ctx;
    private final WindowManager wm;
    private final FrameLayout root;
    private final PromptView prompt;
    private final WindowManager.LayoutParams params;

    private boolean dragging;
    private float downX;
    private float downY;
    private int startX;
    private int startY;
    private final int slop;

    private OverlayPlayer(Context c, int startChar) {
        ctx = c.getApplicationContext();
        Settings settings = new Settings(ctx);
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        boolean horizontal = settings.mode() == Settings.MODE_HORIZONTAL;

        int w = (int) (dm.widthPixels * (horizontal ? 0.92f : 0.74f));
        int h = (int) (dm.heightPixels * (horizontal ? 0.20f : 0.42f));
        int left = (dm.widthPixels - w) / 2;
        int top = (dm.heightPixels - h) / 2;

        int bg = settings.background();
        int fg = Ui.contrastText(bg);

        root = new FrameLayout(ctx);
        GradientDrawable back = Ui.round(bg, Ui.dp(ctx, 16));
        back.setStroke(Ui.dp(ctx, 1), Ui.luminance(bg) > 0.42 ? 0x22000000 : 0x33FFFFFF);
        root.setBackground(back);
        root.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6));
        root.setElevation(Ui.dp(ctx, 10));

        prompt = new PromptView(ctx, settings.text(), fg, settings.font(), settings.bold(),
                settings.size(), settings.speed(), settings.mirror(), settings.mode());
        prompt.setListener(this);
        prompt.setStartChar(startChar);
        root.addView(prompt, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView close = new TextView(ctx);
        close.setText("关闭");
        close.setTextSize(11);
        close.setTextColor((fg & 0x00FFFFFF) | 0xDD000000);
        int closeBg = Ui.luminance(bg) > 0.42 ? 0x1A000000 : 0x33FFFFFF;
        close.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 5), Ui.dp(ctx, 10), Ui.dp(ctx, 5));
        close.setBackground(Ui.ripple(closeBg, Ui.dp(ctx, 10), 0x44FFFFFF));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detach();
            }
        });
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        clp.topMargin = Ui.dp(ctx, 2);
        clp.rightMargin = Ui.dp(ctx, 2);
        root.addView(close, clp);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(w, h, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = left;
        params.y = top;

        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
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
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            togglePause();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    static boolean isShowing() {
        return current != null;
    }

    static void show(Context c, int startChar) {
        if (current != null) {
            Toast.makeText(c, "悬浮窗已经在播了", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            current = new OverlayPlayer(c, startChar);
            current.attach();
        } catch (Exception e) {
            current = null;
            Toast.makeText(c, "悬浮窗打不开：" + e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void attach() {
        wm.addView(root, params);
        prompt.play();
    }

    private void togglePause() {
        if (prompt.isFinished()) {
            prompt.restart();
        } else if (prompt.isPlaying()) {
            prompt.pause();
            Toast.makeText(ctx, "已暂停，点一下继续", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(ctx, "播放结束，点一下从头再来", Toast.LENGTH_SHORT).show();
    }
}
