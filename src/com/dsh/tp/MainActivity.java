package com.dsh.tp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 编辑页：写内容、改设置、开始播放。 */
public class MainActivity extends android.app.Activity {

    private static final int PAGE = 0xFFF4F5F7;
    private static final int CARD = 0xFFFFFFFF;
    private static final int INK = 0xFF16181D;
    private static final int MUTED = 0xFF848A94;
    private static final int ACCENT = 0xFF2F6FED;
    private static final int TRACK = 0xFFE3E6EB;

    private Settings settings;
    private EditText input;
    private TextView counter;
    private TextView libraryBtn;
    private Sheet librarySheet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Crash.install(getApplicationContext());
        settings = new Settings(this);
        edgeToEdge();

        final int pad = Ui.dp(this, 16);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAGE);
        root.setPadding(pad, Ui.dp(this, 10), pad, Ui.dp(this, 14));
        // 画到状态栏后面，再用 insets 把内容让开，避免顶部一条深色带
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top = 0;
                int bottom = 0;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    top = bars.top;
                    bottom = bars.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(pad, Ui.dp(MainActivity.this, 10) + top, pad,
                        Ui.dp(MainActivity.this, 14) + bottom);
                return insets;
            }
        });

        root.addView(header());
        root.addView(inputCard(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(footer());

        setContentView(root);
        root.requestApplyInsets();
        updateLibraryCount();
    }

    /** 状态栏透明、内容铺到顶部，图标改成深色。 */
    private void edgeToEdge() {
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        win.setStatusBarColor(Color.TRANSPARENT);
        win.setNavigationBarColor(Color.TRANSPARENT);
        View decor = win.getDecorView();
        int flags = decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            win.setDecorFitsSystemWindows(false);
            WindowInsetsController c = win.getInsetsController();
            if (c != null) {
                c.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        }
        decor.setSystemUiVisibility(flags);
    }

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 2), 0, 0, Ui.dp(this, 8));

        TextView title = new TextView(this);
        title.setText("提词器");
        title.setTextSize(24);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setTextColor(INK);
        row.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView save = new TextView(this);
        save.setText("保存");
        save.setTextSize(14);
        save.setTextColor(ACCENT);
        save.setGravity(Gravity.CENTER);
        save.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        save.setBackground(Ui.ripple(0xFFE9F0FE, Ui.dp(this, 20), 0x332F6FED));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveScript();
            }
        });
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.rightMargin = Ui.dp(this, 8);
        row.addView(save, ulp);

        TextView clear = new TextView(this);
        clear.setText("清空");
        clear.setTextSize(14);
        clear.setTextColor(0xFF8A6A22);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        clear.setBackground(Ui.ripple(0xFFFDF3DC, Ui.dp(this, 20), 0x33A57500));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.rightMargin = Ui.dp(this, 8);
        row.addView(clear, clp);

        TextView gear = new TextView(this);
        gear.setText("设置");
        gear.setTextSize(14);
        gear.setTextColor(0xFF4A5160);
        gear.setGravity(Gravity.CENTER);
        gear.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        gear.setBackground(Ui.ripple(0xFFFFFFFF, Ui.dp(this, 20), 0x22000000));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });
        row.addView(gear);
        return row;
    }

    private View inputCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.roundStroke(CARD, 0xFFE1E4EA, Ui.dp(this, 1), Ui.dp(this, 18)));

        input = new EditText(this);
        input.setBackground(null);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setTextSize(17);
        input.setTextColor(INK);
        input.setHintTextColor(0xFFABB1BB);
        input.setHint("在这里输入或粘贴要念的内容…");
        input.setLineSpacing(Ui.dp(this, 4), 1f);
        input.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        input.setText(settings.text());
        input.setSelection(input.getText().length());
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                updateCounter(s.length());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        card.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Ui.margins(card, 0, 0, 0, Ui.dp(this, 12));
        return card;
    }

    private View footer() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);

        counter = new TextView(this);
        counter.setTextSize(12);
        counter.setTextColor(MUTED);
        info.addView(counter, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView cursorPlay = new TextView(this);
        cursorPlay.setText("从光标处播放");
        cursorPlay.setTextSize(13);
        cursorPlay.setTextColor(ACCENT);
        cursorPlay.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        cursorPlay.setBackground(Ui.ripple(0x00000000, Ui.dp(this, 8), 0x222F6FED));
        cursorPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = input.getSelectionStart();
                if (pos <= 0) {
                    Toast.makeText(MainActivity.this, "先把光标放到要开始的那一段", Toast.LENGTH_SHORT).show();
                    return;
                }
                startPlayback(true);
            }
        });
        libraryBtn = new TextView(this);
        libraryBtn.setText("保存的文案");
        libraryBtn.setTextSize(13);
        libraryBtn.setTextColor(ACCENT);
        libraryBtn.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        libraryBtn.setBackground(Ui.ripple(0x00000000, Ui.dp(this, 8), 0x222F6FED));
        libraryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLibrary();
            }
        });
        info.addView(libraryBtn);

        info.addView(cursorPlay);

        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.bottomMargin = Ui.dp(this, 8);
        col.addView(info, ilp);

        TextView start = new TextView(this);
        start.setText("开始播放");
        start.setTextSize(17);
        start.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        start.setTextColor(Color.WHITE);
        start.setGravity(Gravity.CENTER);
        start.setBackground(Ui.ripple(ACCENT, Ui.dp(this, 16), 0x55FFFFFF));
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPlayback(false);
            }
        });
        col.addView(start, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54)));

        updateCounter(input.getText().length());
        return col;
    }

    private void updateCounter(int len) {
        if (counter == null) {
            return;
        }
        counter.setText("共 " + len + " 字 · 每行约 " + settings.charsPerLine() + " 字");
    }

    // ---------------------------------------------------------------- 文案

    private void saveScript() {
        final String text = input.getText().toString();
        if (text.trim().isEmpty()) {
            toast("先输入要保存的内容");
            return;
        }
        final EditText name = new EditText(this);
        name.setText(suggestTitle(text));
        name.setTextSize(16);
        name.setSingleLine(true);
        name.setTextColor(INK);
        name.setSelection(name.getText().length());
        name.setHint("给这段文案起个名字");
        name.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        name.setBackground(Ui.roundStroke(0xFFF7F8FA, 0xFFE1E4EA, Ui.dp(this, 1), Ui.dp(this, 12)));

        final Sheet sheet = Sheet.create(this, "保存文案");
        sheet.add(name);
        sheet.cancel("取消");
        sheet.primary("保存", new Runnable() {
            @Override
            public void run() {
                String title = name.getText().toString().trim();
                if (title.isEmpty()) {
                    title = suggestTitle(text);
                }
                Scripts.add(MainActivity.this, title, text);
                updateLibraryCount();
                sheet.dismiss();
                toast("已保存「" + title + "」");
            }
        });
        sheet.show();
        sheet.showKeyboard(name);
    }

    private void showLibrary() {
        final List<Scripts.Item> items = Scripts.list(this);
        if (items.isEmpty()) {
            toast("还没有保存的文案，先点右上角「保存」");
            return;
        }
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 4);
        list.setPadding(pad, pad, pad, pad);

        for (int i = 0; i < items.size(); i++) {
            final Scripts.Item it = items.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
            row.setBackground(Ui.ripple(0x00000000, Ui.dp(this, 10), 0x222F6FED));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadScript(it);
                }
            });

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(this);
            title.setText(it.title);
            title.setTextSize(15);
            title.setTextColor(INK);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            TextView prev = new TextView(this);
            String preview = it.text.replace('\n', ' ').trim();
            if (preview.length() > 40) {
                preview = preview.substring(0, 40) + "…";
            }
            prev.setText(preview + "  ·  " + fmtTime(it.at));
            prev.setTextSize(12);
            prev.setTextColor(MUTED);
            prev.setMaxLines(1);
            prev.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(title);
            col.addView(prev);
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = new TextView(this);
            del.setText("删除");
            del.setTextSize(13);
            del.setTextColor(0xFFD14343);
            del.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Scripts.remove(MainActivity.this, it.id);
                    if (librarySheet != null) {
                        librarySheet.dismiss();
                    }
                    updateLibraryCount();
                    toast("已删除「" + it.title + "」");
                }
            });
            row.addView(del);

            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        librarySheet = Sheet.create(this, "保存的文案（" + items.size() + "）");
        librarySheet.addScrollable(list);
        librarySheet.done("关闭");
        librarySheet.show();
    }

    private void loadScript(Scripts.Item it) {
        input.setText(it.text);
        settings.text(it.text);
        input.setSelection(input.getText().length());
        updateCounter(input.getText().length());
        if (librarySheet != null) {
            librarySheet.dismiss();
        }
        toast("已加载「" + it.title + "」");
    }

    private void updateLibraryCount() {
        if (libraryBtn != null) {
            libraryBtn.setText("保存的文案");
        }
    }

    private String suggestTitle(String text) {
        String first = text.trim().split("\n", 2)[0].trim();
        if (first.isEmpty()) {
            first = "未命名";
        }
        return first.length() > 12 ? first.substring(0, 12) : first;
    }

    private static String fmtTime(long at) {
        if (at <= 0) {
            return "";
        }
        return new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(at));
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- 更新

    private void checkUpdate() {
        final AlertDialog checking = new AlertDialog.Builder(this)
                .setTitle("检查更新")
                .setMessage("正在查询 GitHub 上的最新版本…")
                .setCancelable(false)
                .create();
        checking.show();
        final Handler handler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                Updater.check(new Updater.CheckCallback() {
                    @Override
                    public void done(final String tag, final String notes, final String url,
                                     final String apiUrl, final long size, final String error) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                checking.dismiss();
                                if (error != null) {
                                    alert("检查更新失败", error);
                                    return;
                                }
                                String current = currentVersion();
                                if (!isNewer(tag, current)) {
                                    alert("已是最新版本", "当前 " + current + "，GitHub 上最新是 " + tag + "。");
                                    return;
                                }
                                if (url == null) {
                                    alert("没有安装包", "最新版本 " + tag + " 没有附带 APK 文件。");
                                    return;
                                }
                                String body = notes == null ? "" : notes.trim();
                                if (body.length() > 600) {
                                    body = body.substring(0, 600) + "…";
                                }
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("发现新版本 " + tag)
                                        .setMessage(body.isEmpty() ? "是否下载并安装？" : body + "\n\n是否下载并安装？")
                                        .setNegativeButton("以后再说", null)
                                        .setPositiveButton("下载并安装", new android.content.DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(android.content.DialogInterface d, int w) {
                                                startUpdate(url, apiUrl);
                                            }
                                        })
                                        .show();
                            }
                        });
                    }
                });
            }
        }).start();
    }

    private void startUpdate(final String url, final String apiUrl) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要安装权限")
                    .setMessage("系统会打开设置页，请允许「提词器」安装未知应用，然后回来再点一次「更新」。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去设置", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            try {
                                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + getPackageName())));
                            } catch (Exception e) {
                                alert("打开设置失败", String.valueOf(e.getMessage()));
                            }
                        }
                    })
                    .show();
            return;
        }

        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("正在下载更新")
                .setMessage("0%")
                .setCancelable(false)
                .create();
        progress.show();
        final Handler handler = new Handler(Looper.getMainLooper());
        final File dest = new File(getCacheDir(), "update.apk");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Updater.download(url, apiUrl, dest, new Updater.Progress() {
                        @Override
                        public void onProgress(final int percent) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    progress.setMessage(percent + "%");
                                }
                            });
                        }
                    });
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            progress.dismiss();
                            installApk(dest);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            progress.dismiss();
                            alert("下载失败", String.valueOf(e.getMessage()));
                        }
                    });
                }
            }
        }).start();
    }

    private void installApk(File apk) {
        try {
            Updater.install(this, apk);
        } catch (Exception e) {
            alert("安装失败", String.valueOf(e.getMessage()));
        }
    }

    private String currentVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** tag 比 current 新才返回 true；支持 "v1.5" 这种写法。 */
    private static boolean isNewer(String tag, String current) {
        int[] a = nums(tag);
        int[] b = nums(current);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return a[i] > b[i];
            }
        }
        return false;
    }

    private static int[] nums(String value) {
        int[] out = new int[3];
        if (value == null) {
            return out;
        }
        String[] parts = value.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < parts.length && i < 3; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private void alert(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    /** 清空编辑框里的内容，先确认一下免得误触。 */
    private void confirmClear() {
        if (input.getText().length() == 0) {
            return;
        }
        final Sheet sheet = Sheet.create(this, "清空输入的内容？");
        sheet.text("正文会被清空，字号、速度这些设置不受影响。");
        sheet.cancel("取消");
        sheet.primary("清空", new Runnable() {
            @Override
            public void run() {
                input.setText("");
                settings.text("");
                input.requestFocus();
                sheet.dismiss();
            }
        });
        sheet.show();
    }

    private void startPlayback(boolean fromCursor) {
        String text = input.getText().toString();
        if (text.trim().length() == 0) {
            Toast.makeText(this, "先输入要念的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        settings.text(text);
        InputMethodManager ime = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (ime != null) {
            ime.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        int start = 0;
        if (fromCursor) {
            start = Math.max(0, Math.min(input.getSelectionStart(), text.length()));
        }
        if (settings.playMode() == Settings.PLAY_OVERLAY) {
            if (Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this)) {
                final Sheet sheet = Sheet.create(this, "需要悬浮窗权限");
                sheet.text("要在其他应用上面显示台词，得先允许「提词器」显示在其他应用的上层。设置好之后回来再点「开始播放」。");
                sheet.cancel("取消");
                sheet.primary("去设置", new Runnable() {
                    @Override
                    public void run() {
                        sheet.dismiss();
                        try {
                            startActivity(new Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception e) {
                            alert("打不开设置", String.valueOf(e.getMessage()));
                        }
                    }
                });
                sheet.show();
                return;
            }
            OverlayPlayer.show(this, start);
            moveTaskToBack(true);
            return;
        }

        Intent i = new Intent(this, PromptActivity.class);
        i.putExtra("start", start);
        startActivity(i);
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        settings.text(input.getText().toString());
    }

    // ---------------------------------------------------------------- 设置

    private Dialog dialog;
    private TextView preview;
    private SeekBar sizeBar;
    private TextView sizeLabel;
    private SeekBar speedBar;
    private TextView speedLabel;
    private final List<TextView> modeChips = new ArrayList<>();
    private final List<TextView> playChips = new ArrayList<>();
    private final List<TextView> orientChips = new ArrayList<>();
    private final List<TextView> fontChips = new ArrayList<>();
    private final List<View> bgDots = new ArrayList<>();

    private void showSettings() {
        dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        int r = Ui.dp(this, 22);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 18));

        View handle = new View(this);
        handle.setBackground(Ui.round(0xFFD8DCE3, Ui.dp(this, 2)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = Ui.dp(this, 12);
        sheet.addView(handle, hlp);

        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView head = new TextView(this);
        head.setText("设置");
        head.setTextSize(20);
        head.setTextColor(INK);
        head.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        headRow.addView(head, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView done = new TextView(this);
        done.setText("完成");
        done.setTextSize(14);
        done.setTextColor(Color.WHITE);
        done.setGravity(Gravity.CENTER);
        done.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 8));
        done.setBackground(Ui.ripple(ACCENT, Ui.dp(this, 18), 0x55FFFFFF));
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        headRow.addView(done);
        sheet.addView(headRow);

        ScrollView scroll = new ScrollView(this) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.82f);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, Ui.dp(this, 6), 0, 0);

        // 滚动方式
        body.addView(sectionLabel("滚动方式"));
        modeChips.clear();
        String[] modeNames = {"整屏上滚", "单行横移"};
        for (int idx = 0; idx < modeNames.length; idx++) {
            final int value = idx;
            TextView c = chip(modeNames[idx], settings.mode() == idx);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settings.mode(value);
                    refreshChips(modeChips, value, 2);
                }
            });
            modeChips.add(c);
        }
        body.addView(chipRow(modeChips));

        // 播放方式
        body.addView(sectionLabel("播放方式"));
        playChips.clear();
        String[] playNames = {"全屏", "悬浮窗"};
        for (int idx = 0; idx < playNames.length; idx++) {
            final int value = idx;
            TextView c = chip(playNames[idx], settings.playMode() == idx);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settings.playMode(value);
                    refreshChips(playChips, value, 2);
                }
            });
            playChips.add(c);
        }
        body.addView(chipRow(playChips));

        // 方向
        body.addView(sectionLabel("播放方向"));
        orientChips.clear();
        for (int idx = 0; idx < 2; idx++) {
            final int value = idx;
            TextView c = chip(idx == 0 ? "竖屏" : "横屏", settings.orientation() == idx);
            final TextView self = c;
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settings.orientation(value);
                    refreshChips(orientChips, value, 2);
                    refreshPreview();
                }
            });
            orientChips.add(c);
        }
        body.addView(chipRow(orientChips));

        // 字号
        sizeLabel = sectionLabel("");
        body.addView(sizeLabel);
        sizeBar = new SeekBar(this);
        sizeBar.setMax(Settings.SIZE_MAX - Settings.SIZE_MIN);
        sizeBar.setProgress(settings.size() - Settings.SIZE_MIN);
        tintBar(sizeBar, ACCENT);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                settings.size(progress + Settings.SIZE_MIN);
                refreshSizeLabel();
                refreshPreview();
                updateCounter(input.getText().length());
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        body.addView(sizeBar);
        refreshSizeLabel();

        // 速度
        speedLabel = sectionLabel("");
        body.addView(speedLabel);
        speedBar = new SeekBar(this);
        speedBar.setMax(9);
        speedBar.setProgress(settings.speed() - 1);
        tintBar(speedBar, ACCENT);
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                settings.speed(progress + 1);
                refreshSpeedLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        body.addView(speedBar);
        refreshSpeedLabel();

        // 字体
        body.addView(sectionLabel("字体"));
        fontChips.clear();
        String[] fontNames = {"系统", "黑体", "宋体", "等宽"};
        for (int idx = 0; idx < fontNames.length; idx++) {
            final int value = idx;
            TextView c = chip(fontNames[idx], settings.font() == idx);
            c.setTypeface(Settings.typeface(idx, false));
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settings.font(value);
                    refreshChips(fontChips, value, 4);
                    refreshPreview();
                }
            });
            fontChips.add(c);
        }
        body.addView(chipRow(fontChips));

        // 加粗
        body.addView(switchRow("加粗", settings.bold(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                settings.bold(checked);
                refreshPreview();
            }
        }));

        // 背景色
        body.addView(sectionLabel("背景颜色"));
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        bgDots.clear();
        for (int i = 0; i < Settings.BG.length; i++) {
            final int color = Settings.BG[i];
            final View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38));
            lp.rightMargin = Ui.dp(this, 10);
            dot.setTag(Integer.valueOf(color));
            dot.setBackground(dotDrawable(color, color == settings.background()));
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settings.background(color);
                    for (View d : bgDots) {
                        int c = ((Integer) d.getTag()).intValue();
                        d.setBackground(dotDrawable(c, c == color));
                    }
                    refreshPreview();
                }
            });
            bgDots.add(dot);
            dots.addView(dot, lp);
        }
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(dots);
        body.addView(hsv);

        // 镜像
        body.addView(switchRow("镜像翻转（对着提词镜时用）", settings.mirror(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        settings.mirror(checked);
                    }
                }));

        // 检查更新（原来在顶栏的「更新」挪到了这里）
        TextView updateRow = new TextView(this);
        updateRow.setText("检查更新 · 当前 " + currentVersion());
        updateRow.setTextSize(14);
        updateRow.setTextColor(ACCENT);
        updateRow.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        updateRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                checkUpdate();
            }
        });
        body.addView(updateRow);

        // 预览
        body.addView(sectionLabel("预览"));
        preview = new TextView(this);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18));
        body.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refreshPreview();

        scroll.addView(body);
        sheet.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(sheet);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = w.getAttributes();
            attrs.dimAmount = 0.45f;
            w.setAttributes(attrs);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
        Window w2 = dialog.getWindow();
        if (w2 != null) {
            w2.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void refreshSizeLabel() {
        sizeLabel.setText("字号 " + settings.size() + "% · 一行约 " + settings.charsPerLine() + " 字");
    }

    private void refreshSpeedLabel() {
        speedLabel.setText("滚动速度 " + settings.speed() + " 字/秒");
    }

    private void refreshPreview() {
        if (preview == null) {
            return;
        }
        int bg = settings.background();
        preview.setBackground(Ui.roundStroke(bg, 0x33000000, Ui.dp(this, 1), Ui.dp(this, 12)));
        preview.setTextColor(Ui.contrastText(bg));
        preview.setTypeface(Settings.typeface(settings.font(), settings.bold()));
        preview.setTextSize(10 + settings.size() * 0.14f);
        preview.setText("提词 预览");
    }

    private void refreshChips(List<TextView> chips, int selected, int count) {
        for (int i = 0; i < chips.size() && i < count; i++) {
            styleChip(chips.get(i), i == selected);
        }
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(MUTED);
        t.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 6));
        return t;
    }

    private LinearLayout chipRow(List<TextView> chips) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < chips.size(); i++) {
            TextView c = chips.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 38));
            if (i > 0) {
                lp.leftMargin = Ui.dp(this, 8);
            }
            row.addView(c, lp);
        }
        return row;
    }

    private TextView chip(String text, boolean selected) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        styleChip(t, selected);
        return t;
    }

    private void styleChip(TextView t, boolean selected) {
        if (selected) {
            t.setTextColor(Color.WHITE);
            t.setBackground(Ui.ripple(ACCENT, Ui.dp(this, 19), 0x55FFFFFF));
        } else {
            t.setTextColor(0xFF4A5160);
            t.setBackground(Ui.ripple(0xFFFFFFFF, Ui.dp(this, 19), 0x22000000));
            GradientDrawable d = Ui.roundStroke(0xFFFFFFFF, 0xFFDDE1E7, Ui.dp(this, 1), Ui.dp(this, 19));
            t.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(0x22000000), d, null));
        }
    }

    private Drawable dotDrawable(int color, boolean selected) {
        if (selected) {
            return Ui.circleStroke(color, ACCENT, Ui.dp(this, 3));
        }
        return Ui.circleStroke(color, 0x66000000, Ui.dp(this, 1));
    }

    private View switchRow(String label, boolean value, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 12), 0, 0);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setTextColor(INK);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(value);
        sw.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        sw.setTrackTintList(ColorStateList.valueOf(value ? ACCENT : TRACK));
        final Switch self = sw;
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                self.setTrackTintList(ColorStateList.valueOf(checked ? ACCENT : TRACK));
                l.onCheckedChanged(b, checked);
            }
        });
        row.addView(sw);
        return row;
    }

    private void tintBar(SeekBar bar, int color) {
        bar.setProgressTintList(ColorStateList.valueOf(color));
        bar.setThumbTintList(ColorStateList.valueOf(color));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(TRACK));
    }
}
