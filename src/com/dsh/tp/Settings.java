package com.dsh.tp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

/** 所有设置都存在 SharedPreferences 里，改完立刻生效。 */
final class Settings {

    static final int PORTRAIT = 0;
    static final int LANDSCAPE = 1;

    /** 播放方式：全屏播放 / 悬浮窗播放。 */
    static final int PLAY_FULLSCREEN = 0;
    static final int PLAY_OVERLAY = 1;
    static final int PLAY_CAPSULE = 2;

    /** 滚动方式：整屏向上滚 / 所有文字连成一行横着滚。 */
    static final int MODE_VERTICAL = 0;
    static final int MODE_HORIZONTAL = 1;

    static final int SIZE_MIN = 20;
    static final int SIZE_MAX = 100;

    static final int[] BG = {
            0xFF000000, 0xFFFFFFFF, 0xFFF3E8D0, 0xFF0B2033, 0xFF00B140, 0xFF2A2A2A
    };
    static final String[] BG_NAMES = {"黑", "白", "米黄", "深蓝", "绿幕", "深灰"};

    private final SharedPreferences sp;

    Settings(Context c) {
        sp = c.getSharedPreferences("teleprompter", Context.MODE_PRIVATE);
    }

    String text() {
        return sp.getString("text", "");
    }

    void text(String t) {
        sp.edit().putString("text", t).apply();
    }

    /** 字号百分比 20..100，100 时一行正好 4 个字。 */
    int size() {
        return sp.getInt("size", 100);
    }

    void size(int v) {
        sp.edit().putInt("size", clamp(v, SIZE_MIN, SIZE_MAX)).apply();
    }

    /** 滚动速度：字/秒，1..10。 */
    int speed() {
        return sp.getInt("speed", 4);
    }

    void speed(int v) {
        sp.edit().putInt("speed", clamp(v, 1, 10)).apply();
    }

    /** 悬浮窗单独的速度，默认和全屏一样。 */
    int overlaySpeed() {
        return sp.getInt("overlaySpeed", speed());
    }

    void overlaySpeed(int v) {
        sp.edit().putInt("overlaySpeed", clamp(v, 1, 10)).apply();
    }

    /** 是否在通知/状态栏显示当前行（实验性）。 */
    boolean chipNotify() {
        return sp.getBoolean("chipNotify", false);
    }

    void chipNotify(boolean v) {
        sp.edit().putBoolean("chipNotify", v).apply();
    }

    int playMode() {
        return sp.getInt("playMode", PLAY_FULLSCREEN);
    }

    void playMode(int v) {
        sp.edit().putInt("playMode", v).apply();
    }

    int mode() {
        return sp.getInt("mode", MODE_VERTICAL);
    }

    void mode(int v) {
        sp.edit().putInt("mode", v).apply();
    }

    int orientation() {
        return sp.getInt("orientation", PORTRAIT);
    }

    void orientation(int v) {
        sp.edit().putInt("orientation", v).apply();
    }

    /** 0 系统 1 黑体 2 宋体 3 等宽。 */
    int font() {
        return sp.getInt("font", 0);
    }

    void font(int v) {
        sp.edit().putInt("font", v).apply();
    }

    boolean bold() {
        return sp.getBoolean("bold", true);
    }

    void bold(boolean v) {
        sp.edit().putBoolean("bold", v).apply();
    }

    int background() {
        return sp.getInt("bg", BG[0]);
    }

    void background(int v) {
        sp.edit().putInt("bg", v).apply();
    }

    boolean mirror() {
        return sp.getBoolean("mirror", false);
    }

    void mirror(boolean v) {
        sp.edit().putBoolean("mirror", v).apply();
    }

    /** 只用来在设置里提示大概一行几个字。 */
    int charsPerLine() {
        return Math.max(1, Math.round(4f * 100f / size()));
    }

    static Typeface typeface(int font, boolean bold) {
        Typeface tf;
        switch (font) {
            case 1:
                tf = Typeface.create("sans-serif-black", Typeface.NORMAL);
                break;
            case 2:
                tf = Typeface.create("serif", Typeface.NORMAL);
                break;
            case 3:
                tf = Typeface.create("monospace", Typeface.NORMAL);
                break;
            default:
                tf = Typeface.create("sans-serif", Typeface.NORMAL);
                break;
        }
        if (bold) {
            tf = Typeface.create(tf, Typeface.BOLD);
        }
        return tf;
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
