package com.dsh.tp;

import android.app.Notification;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONObject;

/**
 * 小米超级岛（焦点通知）客户端接入：
 * 官方文档允许不走 MiPush，直接在通知 extras 里放 "miui.focus.param" 的 JSON。
 */
final class MiIsland {

    private MiIsland() {
    }

    /** 1/2 = OS1/OS2 焦点通知，3 = OS3 超级岛，0 = 不支持。 */
    static int protocol(Context c) {
        try {
            return android.provider.Settings.System.getInt(
                    c.getContentResolver(), "notification_focus_protocol", 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    static boolean systemSupportsIsland() {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Object v = clazz.getDeclaredMethod("getBoolean", String.class, boolean.class)
                    .invoke(null, "persist.sys.feature.island", false);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 应用有没有被小米开通焦点通知权限（没开通时系统会当普通通知处理）。 */
    static boolean hasFocusPermission(Context c) {
        try {
            Uri uri = Uri.parse("content://miui.statusbar.notification.public");
            Bundle extras = new Bundle();
            extras.putString("package", c.getPackageName());
            Bundle out = c.getContentResolver().call(uri, "canShowFocus", null, extras);
            return out != null && out.getBoolean("canShowFocus", false);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 把岛参数挂到通知上，失败就算了，通知照常显示。 */
    static void attach(Notification n, String lineText, int line, int total) {
        if (n == null || n.extras == null) {
            return;
        }
        try {
            n.extras.putString("miui.focus.param", params(lineText, line, total));
        } catch (Throwable ignored) {
        }
    }

    private static String params(String lineText, int line, int total) {
        String title = shortText(lineText);
        String content = "第 " + line + " / " + Math.max(total, 1) + " 行";
        try {
            JSONObject island = new JSONObject();
            island.put("islandProperty", 1);
            island.put("islandTimeout", 3600);
            island.put("dismissIsland", false);

            JSONObject textInfo = new JSONObject();
            textInfo.put("frontTitle", "");
            textInfo.put("title", title);
            textInfo.put("content", content);
            textInfo.put("useHighLight", false);

            JSONObject left = new JSONObject();
            left.put("type", 1);
            left.put("textInfo", textInfo);

            JSONObject big = new JSONObject();
            big.put("imageTextInfoLeft", left);
            island.put("bigIslandArea", big);

            JSONObject smallText = new JSONObject();
            smallText.put("title", title);
            JSONObject smallLeft = new JSONObject();
            smallLeft.put("type", 1);
            smallLeft.put("textInfo", smallText);
            JSONObject small = new JSONObject();
            small.put("imageTextInfoLeft", smallLeft);
            island.put("smallIslandArea", small);

            JSONObject baseInfo = new JSONObject();
            baseInfo.put("title", title);
            baseInfo.put("content", content);
            baseInfo.put("type", 2);

            JSONObject v2 = new JSONObject();
            v2.put("protocol", 1);
            v2.put("business", "teleprompter");
            v2.put("islandFirstFloat", false);
            v2.put("enableFloat", false);
            v2.put("updatable", true);
            v2.put("ticker", title);
            v2.put("aodTitle", title);
            v2.put("param_island", island);
            v2.put("baseInfo", baseInfo);

            JSONObject root = new JSONObject();
            root.put("param_v2", v2);
            return root.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String shortText(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) {
            return "提词中";
        }
        return s.length() > 8 ? s.substring(0, 8) : s;
    }
}
