package com.dsh.tp;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** 全屏提词界面。点一下屏幕才会出现底部控制条。 */
public class PromptActivity extends Activity implements PromptView.Listener {

    private Settings settings;
    private PromptView view;

    private LinearLayout controls;
    private TextView playBtn;
    private TextView posLabel;
    private TextView speedLabel;
    private SeekBar speedBar;
    private SeekBar posBar;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };

    private boolean seeking;

    private VoiceFollower follower;
    private boolean voiceOn;
    private boolean gate = true;
    private TextView voiceBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUi();
        } catch (Throwable t) {
            Crash.show(this, t);
        }
    }

    private void buildUi() {
        settings = new Settings(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 真正铺满：允许画进刘海/挖孔区域，并且不受系统栏留白限制
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams wlp = getWindow().getAttributes();
            wlp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(wlp);
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setRequestedOrientation(settings.orientation() == Settings.LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        applyImmersive();

        int bg = settings.background();
        int fg = Ui.contrastText(bg);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);

        view = new PromptView(this, settings.text(), fg, settings.font(), settings.bold(),
                settings.size(), settings.speed(), settings.mirror(), settings.mode());
        view.setListener(this);
        view.setStartChar(getIntent().getIntExtra("start", 0));
        final android.view.GestureDetector gestures = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        onScreenTap();
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        togglePlay();
                        return true;
                    }
                });
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestures.onTouchEvent(event);
            }
        });
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        controls = buildControls();
        controls.setVisibility(View.GONE);
        controls.setAlpha(0f);
        controls.setTranslationY(Ui.dp(this, 28));
        root.addView(controls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        setContentView(root);
    }

    private void applyImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                getWindow().setDecorFitsSystemWindows(false);
            } catch (Throwable ignored) {
            }
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                WindowManager.LayoutParams wlp = getWindow().getAttributes();
                wlp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                        ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(wlp);
            } catch (Throwable ignored) {
            }
        }
        try {
            getWindow().getDecorView().setFitsSystemWindows(false);
        } catch (Throwable ignored) {
        }
        final View decor = getWindow().getDecorView();
        if (!decor.isAttachedToWindow()) {
            // 还没 attach 时 getInsetsController() 内部会空指针，等挂上再隐藏
            decor.post(new Runnable() {
                @Override
                public void run() {
                    applyImmersive();
                }
            });
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            } else {
                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersive();
        }
    }

    private LinearLayout buildControls() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showControls();
            }
        });
        int hpad = Ui.dp(this, 12);
        panel.setPadding(hpad, Ui.dp(this, 8), hpad, Ui.dp(this, 10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEC141414);
        int r = Ui.dp(this, 18);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(bg);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView headBtn = chip("从头", 0xFFFFFFFF, 0x1FFFFFFF);
        headBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                view.restart();
                if (voiceOn) {
                    gate = true;
                    view.pause();
                }
                updatePlayLabel();
                scheduleHide();
            }
        });
        row1.addView(headBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 38)));

        playBtn = chip("暂停", 0xFFFFFFFF, 0x33FFFFFF);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 38));
        plp.leftMargin = Ui.dp(this, 8);
        row1.addView(playBtn, plp);

        speedLabel = new TextView(this);
        speedLabel.setText("速度 " + settings.speed());
        speedLabel.setTextSize(12);
        speedLabel.setTextColor(0xB3FFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 12);
        row1.addView(speedLabel, slp);

        speedBar = new SeekBar(this);
        speedBar.setMax(9);
        speedBar.setProgress(settings.speed() - 1);
        speedBar.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        speedBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x40FFFFFF));
        speedBar.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1f);
        blp.leftMargin = Ui.dp(this, 4);
        row1.addView(speedBar, blp);

        TextView exitBtn = chip("退出", 0xFFFF8A8A, 0x33FF5A5F);
        exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 38));
        elp.leftMargin = Ui.dp(this, 8);
        row1.addView(exitBtn, elp);

        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);

        posBar = new SeekBar(this);
        posBar.setMax(1000);
        posBar.setProgressTintList(ColorStateList.valueOf(0x80FFFFFF));
        posBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x26FFFFFF));
        posBar.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        row2.addView(posBar, new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1f));

        voiceBtn = chip("跟读", 0xFFE6F4FF, 0x1FFFFFFF);
        voiceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleVoice();
            }
        });
        LinearLayout.LayoutParams vbp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36));
        vbp.leftMargin = Ui.dp(this, 8);
        row2.addView(voiceBtn, vbp);

        posLabel = new TextView(this);
        posLabel.setText(settings.mode() == Settings.MODE_HORIZONTAL ? "0%" : "第 1 / 1 行");
        posLabel.setTextSize(11);
        posLabel.setTextColor(0xB3FFFFFF);
        posLabel.setGravity(Gravity.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(this, 92), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.dp(this, 6);
        row2.addView(posLabel, tlp);

        panel.addView(row2);

        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int v = progress + 1;
                settings.speed(v);
                view.setCps(v);
                speedLabel.setText("速度 " + v);
                scheduleHide();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                handler.removeCallbacks(hideRunnable);
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
                view.pause();
                view.seekToFraction(progress / 1000f);
                updatePlayLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                seeking = true;
                handler.removeCallbacks(hideRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                seeking = false;
                scheduleHide();
            }
        });

        return panel;
    }

    private TextView chip(String text, int color, int bgColor) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        t.setBackground(Ui.ripple(bgColor, Ui.dp(this, 10), 0x33FFFFFF));
        return t;
    }

    private void onScreenTap() {
        if (controls.getVisibility() == View.VISIBLE && controls.getAlpha() > 0.5f) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        handler.removeCallbacks(hideRunnable);
        controls.animate().cancel();
        controls.setVisibility(View.VISIBLE);
        controls.animate().alpha(1f).translationY(0f).setDuration(150).start();
        scheduleHide();
    }

    private void hideControls() {
        handler.removeCallbacks(hideRunnable);
        controls.animate().cancel();
        controls.animate().alpha(0f).translationY(Ui.dp(this, 28)).setDuration(150)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        controls.setVisibility(View.GONE);
                    }
                }).start();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideRunnable);
        if (view.isPlaying() && !view.isFinished()) {
            handler.postDelayed(hideRunnable, 4000);
        }
    }

    private void togglePlay() {
        if (voiceOn) {
            gate = !gate;
            view.pause();
            updatePlayLabel();
            scheduleHide();
            return;
        }
        if (view.isPlaying()) {
            view.pause();
        } else if (view.isFinished() || view.fraction() >= 0.999f) {
            view.restart();
        } else {
            view.play();
        }
        updatePlayLabel();
        scheduleHide();
    }

    private void updatePlayLabel() {
        if (voiceOn) {
            playBtn.setText(gate ? "暂停" : "继续");
        } else {
            playBtn.setText(view.isPlaying() ? "暂停" : "继续");
        }
    }

    // ------------------------------------------------------------- 跟读

    private void toggleVoice() {
        if (voiceOn) {
            voiceOn = false;
            stopVoice();
            updateVoiceBtn();
            view.pause();
            updatePlayLabel();
            scheduleHide();
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 41);
            return;
        }
        startVoice();
    }

    private void startVoice() {
        if (!openMic()) {
            Toast.makeText(this, "麦克风打不开，可能被别的应用占用了", Toast.LENGTH_SHORT).show();
            return;
        }
        voiceOn = true;
        gate = true;
        view.pause();
        updateVoiceBtn();
        updatePlayLabel();
        scheduleHide();
        Toast.makeText(this, "跟读已开启：开口就滚，停下就停", Toast.LENGTH_SHORT).show();
    }

    private boolean openMic() {
        if (follower != null) {
            return true;
        }
        VoiceFollower f = new VoiceFollower(new VoiceFollower.Listener() {
            @Override
            public void onSpeaking(boolean speaking) {
                onVoiceState(speaking);
            }
        });
        if (!f.start()) {
            return false;
        }
        follower = f;
        return true;
    }

    private void stopVoice() {
        if (follower != null) {
            follower.stop();
            follower = null;
        }
    }

    private void onVoiceState(boolean speaking) {
        if (!voiceOn || !gate || view.isFinished()) {
            return;
        }
        if (speaking) {
            view.play();
        } else {
            view.pause();
        }
    }

    private void updateVoiceBtn() {
        if (voiceBtn == null) {
            return;
        }
        if (voiceOn) {
            voiceBtn.setText("跟读中");
            voiceBtn.setTextColor(0xFF12321F);
            voiceBtn.setBackground(Ui.ripple(0xFF7BE3B4, Ui.dp(this, 10), 0x33FFFFFF));
        } else {
            voiceBtn.setText("跟读");
            voiceBtn.setTextColor(0xFFE6F4FF);
            voiceBtn.setBackground(Ui.ripple(0x1FFFFFFF, Ui.dp(this, 10), 0x33FFFFFF));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 41) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoice();
            } else {
                Toast.makeText(this, "没有麦克风权限，用不了跟读", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onProgress(float fraction, int line, int total) {
        if (settings.chipNotify()) {
            NotificationHelper.update(this, view.currentLineText(), line, total, fraction);
        }
        if (!seeking) {
            posBar.setProgress(Math.round(fraction * 1000f));
        }
        if (view.horizontal()) {
            posLabel.setText(Math.round(fraction * 100f) + "%");
        } else {
            posLabel.setText("第 " + line + " / " + Math.max(total, 1) + " 行");
        }
    }

    @Override
    public void onFinished() {
        updatePlayLabel();
        posBar.setProgress(1000);
        showControls();
        Toast.makeText(this, "播放结束 · 点「从头」重新播放", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view == null) {
            return;
        }
        applyImmersive();
        if (settings.chipNotify()) {
            ChipService.start(this);
        }
        if (voiceOn) {
            if (!openMic()) {
                voiceOn = false;
                updateVoiceBtn();
            }
            view.pause();
            updatePlayLabel();
            return;
        }
        if (!view.isFinished()) {
            view.play();
        }
        updatePlayLabel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (view == null) {
            return;
        }
        stopVoice();
        view.pause();
        handler.removeCallbacks(hideRunnable);
    }

    @Override
    protected void onDestroy() {
        ChipService.stop(this);
        voiceOn = false;
        stopVoice();
        handler.removeCallbacks(hideRunnable);
        super.onDestroy();
    }
}
