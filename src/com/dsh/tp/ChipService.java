package com.dsh.tp;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * 前台服务：只是为了满足 Android 16 Live Updates 的“提升为胶囊”资格，
 * 本身不干活，通知内容由 NotificationHelper 更新。
 */
public class ChipService extends Service {

    static void start(Context c) {
        Intent i = new Intent(c, ChipService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    static void stop(Context c) {
        try {
            c.stopService(new Intent(c, ChipService.class));
        } catch (Throwable ignored) {
        }
        NotificationHelper.cancel(c);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = NotificationHelper.build(this, "提词中", 1, 1, 0f);
        boolean posted = false;
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                startForeground(NotificationHelper.ID, n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                posted = true;
            } catch (Throwable ignored) {
            }
        }
        if (!posted) {
            try {
                startForeground(NotificationHelper.ID, n);
            } catch (Throwable ignored) {
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
