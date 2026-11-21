package com.dsh.tp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

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
