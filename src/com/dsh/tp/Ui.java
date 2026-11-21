package com.dsh.tp;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** 一些画界面用的小工具。 */
final class Ui {

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int sp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    static GradientDrawable roundStroke(int fill, int strokeColor, int strokeWidth, int radius) {
        GradientDrawable d = round(fill, radius);
        d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static GradientDrawable circleStroke(int fill, int strokeColor, int strokeWidth) {
        GradientDrawable d = circle(fill);
        d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    static Drawable ripple(int fill, int radius, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), round(fill, radius), null);
    }

    static void margins(View v, int l, int t, int r, int b) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.setMargins(l, t, r, b);
            v.setLayoutParams(lp);
        }
    }

    /** 给整棵界面树上所有可点控件加点按回弹 + 轻振动。 */
    static void bounceAll(View root) {
        if (root == null) {
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                bounceAll(group.getChildAt(i));
            }
            return;
        }
        if (!root.isClickable()) {
            return;
        }
        if (root instanceof android.widget.Switch
                || root instanceof android.widget.SeekBar
                || root instanceof android.widget.EditText) {
            return;
        }
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (v.isEnabled()) {
                            v.animate().cancel();
                            v.animate().scaleX(0.93f).scaleY(0.93f)
                                    .setDuration(80L)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .start();
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            buzz(v.getContext());
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().cancel();
                        v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(220L)
                                .setInterpolator(new OvershootInterpolator(2.4f))
                                .start();
                        return false;
                    default:
                        return false;
                }
            }
        });
    }

    static void buzz(Context c) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(14, 150));
            } else {
                v.vibrate(14);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 按背景亮度自动选黑字或白字。 */
    static int contrastText(int bg) {
        return luminance(bg) > 0.42 ? 0xFF111111 : 0xFFFFFFFF;
    }

    static double luminance(int color) {
        return 0.2126 * lin((color >> 16) & 0xFF)
                + 0.7152 * lin((color >> 8) & 0xFF)
                + 0.0722 * lin(color & 0xFF);
    }

    private static double lin(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
