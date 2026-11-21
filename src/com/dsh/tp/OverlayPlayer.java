package com.dsh.tp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** 悬浮窗播放：挂在其他应用上面，可拖动、双指缩放、点一下出控制条。 */
final class OverlayPlayer implements PromptView.Listener {

    private static OverlayPlayer current;

    private final Context ctx;
    private final WindowManager wm;
    private final FrameLayout root;
    private final PromptView prompt;
    private final WindowManager.LayoutParams params;
    private final Settings settings;

    private final LinearLayout controls;
    private final TextView playBtn;
    private final TextView posLabel;
    private final SeekBar speedBar;
    private final SeekBar posBar;

    private final ScaleGestureDetector scale;
    private int winW;
    private int winH;
    private final int minW;
    private final int minH;
    private final int maxW;
    private final int maxH;

    private boolean dragging;
    private boolean scaled;
    private long lastTap;
    private final Runnable singleTapTask = new Runnable() {
        @Override
        public void run() {
            onTap();
        }
    };
    private boolean seeking;
    private float downX;
    private float downY;
    private int startX;
    private int startY;
    private final int slop;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideTask = new Runnable() {
        @Override
        public void run() {
            controls.setVisibility(View.GONE);
        }
    };

    private OverlayPlayer(Context c, int startChar) {
        ctx = c.getApplicationContext();
        settings = new Settings(ctx);
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        boolean horizontal = settings.mode() == Settings.MODE_HORIZONTAL;

        winW = (int) (dm.widthPixels * (horizontal ? 0.92f : 0.74f));
        winH = (int) (dm.heightPixels * (horizontal ? 0.20f : 0.42f));
        minW = Ui.dp(ctx, 180);
        minH = Ui.dp(ctx, 96);
        maxW = dm.widthPixels;
        maxH = dm.heightPixels;
        int left = (dm.widthPixels - winW) / 2;
        int top = (dm.heightPixels - winH) / 2;

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

        // 控制条
        controls = new LinearLayout(ctx);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setVisibility(View.GONE);
        controls.setClickable(true);
        controls.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(0xE6141414);
        int r = Ui.dp(ctx, 12);
        cbg.setCornerRadii(new float[]{r, r, r, r, r, r, r, r});
        controls.setBackground(cbg);

        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView headBtn = chip("从头", 0xFFFFFFFF, 0x1FFFFFFF);
        headBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prompt.restart();
                updatePlayLabel();
                scheduleHide();
            }
        });
        row1.addView(headBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(ctx, 30)));

        playBtn = chip("暂停", 0xFFFFFFFF, 0x33FFFFFF);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(ctx, 30));
        plp.leftMargin = Ui.dp(ctx, 6);
        row1.addView(playBtn, plp);

        TextView speedLabel = new TextView(ctx);
        speedLabel.setText("速度");
        speedLabel.setTextSize(11);
        speedLabel.setTextColor(0xB3FFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(ctx, 8);
        row1.addView(speedLabel, slp);

        speedBar = new SeekBar(ctx);
        speedBar.setMax(9);
        speedBar.setProgress(settings.overlaySpeed() - 1);
        speedBar.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        speedBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x40FFFFFF));
        speedBar.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, Ui.dp(ctx, 28), 1f);
        blp.leftMargin = Ui.dp(ctx, 4);
        row1.addView(speedBar, blp);
        controls.addView(row1);

        LinearLayout row2 = new LinearLayout(ctx);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);

        posBar = new SeekBar(ctx);
        posBar.setMax(1000);
        posBar.setProgressTintList(ColorStateList.valueOf(0x80FFFFFF));
        posBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x26FFFFFF));
        posBar.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        row2.addView(posBar, new LinearLayout.LayoutParams(0, Ui.dp(ctx, 28), 1f));

        posLabel = new TextView(ctx);
        posLabel.setText("第 1 / 1 行");
        posLabel.setTextSize(10);
        posLabel.setTextColor(0xB3FFFFFF);
        posLabel.setGravity(Gravity.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(ctx, 66),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.dp(ctx, 4);
        row2.addView(posLabel, tlp);
        controls.addView(row2);

        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int v = progress + 1;
                settings.overlaySpeed(v);
                prompt.setCps(v);
                scheduleHide();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                handler.removeCallbacks(hideTask);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                scheduleHide();
            }
        });

        posBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                seeking = true;
                prompt.pause();
                prompt.seekToFraction(progress / 1000f);
                updatePlayLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                seeking = true;
                handler.removeCallbacks(hideTask);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                seeking = false;
                scheduleHide();
            }
        });

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        cp.leftMargin = Ui.dp(ctx, 2);
        cp.rightMargin = Ui.dp(ctx, 2);
        cp.bottomMargin = Ui.dp(ctx, 2);
        root.addView(controls, cp);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(winW, winH, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = left;
        params.y = top;

        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        scale = new ScaleGestureDetector(ctx, new ScaleGestureDetector.OnScaleGestureListener() {
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

        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                scale.onTouchEvent(e);
                if (scale.isInProgress()) {
                    return true;
                }
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = params.x;
                        startY = params.y;
                        dragging = false;
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
                        if (!dragging && !scaled) {
                            long now = SystemClock.uptimeMillis();
                            if (now - lastTap < 320L) {
                                lastTap = 0L;
                                handler.removeCallbacks(singleTapTask);
                                togglePlay();
                            } else {
                                lastTap = now;
                                handler.postDelayed(singleTapTask, 260);
                            }
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
        if (settings.chipNotify()) {
            ChipService.start(ctx);
        }
        prompt.play();
    }

    private void onTap() {
        if (controls.getVisibility() == View.VISIBLE) {
            controls.setVisibility(View.GONE);
            handler.removeCallbacks(hideTask);
        } else {
            controls.setVisibility(View.VISIBLE);
            updatePlayLabel();
            scheduleHide();
        }
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideTask);
        if (prompt.isPlaying()) {
            handler.postDelayed(hideTask, 4000);
        }
    }

    private void togglePlay() {
        if (prompt.isPlaying()) {
            prompt.pause();
        } else if (prompt.isFinished()) {
            prompt.restart();
        } else {
            prompt.play();
        }
        updatePlayLabel();
        scheduleHide();
    }

    private void updatePlayLabel() {
        if (playBtn != null) {
            playBtn.setText(prompt.isPlaying() ? "暂停" : "继续");
        }
    }

    private void detach() {
        ChipService.stop(ctx);
        handler.removeCallbacks(hideTask);
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

    private TextView chip(String label, int color, int bg) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(ctx, 9), 0, Ui.dp(ctx, 9), 0);
        t.setBackground(Ui.ripple(bg, Ui.dp(ctx, 8), 0x33FFFFFF));
        return t;
    }

    @Override
    public void onProgress(float fraction, int line, int total) {
        if (settings.chipNotify()) {
            NotificationHelper.update(ctx, prompt.currentLineText(), line, total, fraction);
        }
        if (posBar == null || seeking) {
            return;
        }
        posBar.setProgress(Math.round(fraction * 1000f));
        if (posLabel != null) {
            if (prompt.horizontal()) {
                posLabel.setText(Math.round(fraction * 100f) + "%");
            } else {
                posLabel.setText("第 " + line + " / " + Math.max(total, 1) + " 行");
            }
        }
    }

    @Override
    public void onFinished() {
        updatePlayLabel();
        Toast.makeText(ctx, "播放结束，点「从头」再播", Toast.LENGTH_SHORT).show();
    }
}
