package com.dsh.tp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

/**
 * 常驻通知：显示当前行和进度。
 * Android 16+ 额外尝试用 ProgressStyle + promoted ongoing，让系统把它提升成状态栏胶囊（不是所有机型都认）。
 */
final class NotificationHelper {

    private static final String CHANNEL = "live_chip";
    static final int ID = 0x7A11;
    private static long lastUpdate;

    private NotificationHelper() {
    }

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL, "提词进度",
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setShowBadge(false);
        ch.enableVibration(false);
        ch.setSound(null, null);
        ch.setDescription("播放时在通知栏显示当前行");
        nm.createNotificationChannel(ch);
    }

    static void update(Context c, String lineText, int line, int total, float fraction) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastUpdate < 900L) {
            return;
        }
        lastUpdate = now;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                nm.notify(ID, build(c, lineText, line, total, fraction));
            } catch (Throwable ignored) {
            }
        }
    }

    /** 组装通知；给前台服务和普通更新共用。 */
    static Notification build(Context c, String lineText, int line, int total, float fraction) {
        ensureChannel(c);

        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = open == null ? null
                : PendingIntent.getActivity(c, 0, open, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
        }
        b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(lineText == null || lineText.isEmpty() ? "提词中" : lineText)
                .setContentText("第 " + line + " / " + Math.max(total, 1) + " 行")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setProgress(1000, Math.round(Math.max(0f, Math.min(1f, fraction)) * 1000f), false);
        if (pi != null) {
            b.setContentIntent(pi);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
            } catch (Throwable ignored) {
            }
        }
        if (Build.VERSION.SDK_INT >= 36) {
            liveStyle(b, lineText);
        }
        Notification n = b.build();
        MiIsland.attach(n, lineText, line, total);
        return n;
    }

    /** 反射调用 API 36 才有的 ProgressStyle / promoted ongoing，失败就退回普通通知。 */
    private static void liveStyle(Notification.Builder b, String lineText) {
        try {
            Class<?> styleClass = Class.forName("android.app.Notification$ProgressStyle");
            Object style = styleClass.getDeclaredConstructor().newInstance();

            Class<?> pointClass = Class.forName("android.app.Notification$ProgressStyle$ProgressPoint");
            Object point = pointClass.getDeclaredConstructor(long.class, CharSequence.class)
                    .newInstance(System.currentTimeMillis(), lineText == null ? "" : lineText);
            java.util.List<Object> points = new java.util.ArrayList<>();
            points.add(point);
            styleClass.getMethod("setProgressPoints", java.util.List.class).invoke(style, points);

            Notification.Builder.class.getMethod("setStyle", Notification.Style.class)
                    .invoke(b, style);
            Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class)
                    .invoke(b, true);
            String shortText = lineText == null ? "" : lineText;
            if (shortText.length() > 6) {
                shortText = shortText.substring(0, 6);
            }
            Notification.Builder.class.getMethod("setShortCriticalText", String.class)
                    .invoke(b, shortText);
        } catch (Throwable ignored) {
        }
    }

    static void cancel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                nm.cancel(ID);
            } catch (Throwable ignored) {
            }
        }
    }
}
