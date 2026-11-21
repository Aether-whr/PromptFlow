package com.dsh.tp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 从 GitHub Release 拉最新版本、下载 APK 并交给系统安装器。 */
final class Updater {

    /** 用仓库 id 而不是 owner/name：账号或仓库改名后地址依然有效。 */
    static final String LATEST_RELEASE =
            "https://api.github.com/repositories/1466636341/releases/latest";

    private Updater() {
    }

    interface CheckCallback {
        /** tag 为 null 表示失败，此时 error 有值。 */
        void done(String tag, String notes, String url, String apiUrl, long size, String error);
    }

    interface Progress {
        void onProgress(int percent);
    }

    static void check(CheckCallback cb) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(LATEST_RELEASE).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "teleprompter-updater");
            int code = c.getResponseCode();
            if (code != 200) {
                cb.done(null, null, null, null, 0, "GitHub 返回 " + code + "（可能是网络或频率限制）");
                return;
            }
            JSONObject json = new JSONObject(readAll(c.getInputStream()));
            String tag = json.optString("tag_name", "");
            String notes = json.optString("body", "");
            String url = null;
            String apiUrl = null;
            long size = 0;
            JSONArray assets = json.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.optJSONObject(i);
                    if (a == null) {
                        continue;
                    }
                    String name = a.optString("name", "").toLowerCase();
                    if (name.endsWith(".apk")) {
                        url = a.optString("browser_download_url", null);
                        apiUrl = a.optString("url", null);
                        size = a.optLong("size", 0);
                        break;
                    }
                }
            }
            cb.done(tag, notes, url, apiUrl, size, null);
        } catch (Exception e) {
            cb.done(null, null, null, null, 0, "连不上 GitHub：" + e.getClass().getSimpleName());
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    static void download(String url, String apiUrl, File dest, Progress progress) throws IOException {
        try {
            fetch(url, false, dest, progress);
        } catch (IOException first) {
            // github.com 有时候下不动，改走 API 的资源地址
            if (apiUrl == null || apiUrl.equals(url)) {
                throw first;
            }
            fetch(apiUrl, true, dest, progress);
        }
    }

    private static void fetch(String url, boolean viaApi, File dest, Progress progress) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "teleprompter-updater");
            if (viaApi) {
                c.setRequestProperty("Accept", "application/octet-stream");
            }
            int code = c.getResponseCode();
            if (code != 200) {
                throw new IOException("下载失败，HTTP " + code);
            }
            long total = c.getContentLength();
            InputStream in = c.getInputStream();
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int last = -1;
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    int pct = total > 0 ? (int) (done * 100 / total) : 0;
                    if (pct != last) {
                        last = pct;
                        progress.onProgress(pct);
                    }
                }
                out.flush();
            } finally {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    /** 把 APK 写进 PackageInstaller 会话，随后由系统弹出安装确认。 */
    static void install(Activity activity, File apk) throws Exception {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(activity.getPackageName());
        int id = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(id);
        try {
            OutputStream out = session.openWrite("base.apk", 0, apk.length());
            FileInputStream in = new FileInputStream(apk);
            byte[] buf = new byte[64 * 1024];
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                session.fsync(out);
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
                out.close();
            }
            Intent result = new Intent(activity, UpdateReceiver.class);
            result.setAction(UpdateReceiver.ACTION);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pending = PendingIntent.getBroadcast(activity, id, result, flags);
            session.commit(pending.getIntentSender());
        } finally {
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
        return new String(bos.toByteArray(), "UTF-8");
    }
}
