package com.dsh.tp;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/** 出问题时把堆栈写进 Download/DSH-Go/ 并显示在屏幕上，方便排查。 */
final class Crash {

    private Crash() {
    }

    static String describe(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("device=" + Build.MANUFACTURER + " " + Build.MODEL
                + " sdk=" + Build.VERSION.SDK_INT);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    /** 写到 /sdcard/Download/DSH-Go/tp-crash.txt（Android 10+ 走 MediaStore）。 */
    static void save(Context ctx, Throwable t) {
        String text = describe(t);
        Context app = ctx.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, "tp-crash.txt");
                v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                v.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/DSH-Go");
                Uri uri = app.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri != null) {
                    OutputStream os = app.getContentResolver().openOutputStream(uri, "wt");
                    if (os != null) {
                        os.write(text.getBytes("UTF-8"));
                        os.close();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            File dir = new File("/sdcard/Download/DSH-Go");
            File f = new File(dir, "tp-crash.txt");
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(text.getBytes("UTF-8"));
            fo.close();
        } catch (Throwable ignored) {
        }
    }

    /** 把错误直接铺满屏幕，用户截图就能看到。 */
    static void show(Activity a, Throwable t) {
        save(a, t);
        String text;
        try {
            text = describe(t);
        } catch (Throwable x) {
            text = String.valueOf(t);
        }
        if (text.length() > 4000) {
            text = text.substring(0, 4000);
        }
        TextView tv = new TextView(a);
        tv.setText("出错了，请把这一屏截图发给 AI\n\n" + text);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(0xFF111111);
        tv.setBackgroundColor(0xFFFFFFFF);
        tv.setPadding(24, 64, 24, 64);
        tv.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(0xFFFFFFFF);
        sv.addView(tv);
        try {
            a.setContentView(sv);
        } catch (Throwable ignored) {
        }
    }

    /** 兜住所有线程的崩溃：先存文件，再交给系统。 */
    static void install(final Context ctx) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread th, Throwable t) {
                try {
                    save(ctx, t);
                } catch (Throwable ignored) {
                }
                if (prev != null) {
                    prev.uncaughtException(th, t);
                }
            }
        });
    }
}
