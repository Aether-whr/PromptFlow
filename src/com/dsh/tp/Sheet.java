package com.dsh.tp;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 和主界面同一套配色的圆角弹窗，替掉系统 AlertDialog。 */
final class Sheet {

    private final Activity activity;
    private final Dialog dialog;
    private final LinearLayout panel;
    private final LinearLayout body;
    private final LinearLayout buttons;

    static Sheet create(Activity a, CharSequence title) {
        return new Sheet(a, title);
    }

    private Sheet(Activity a, CharSequence title) {
        activity = a;
        dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        int pad = Ui.dp(a, 20);
        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.round(0xFFFFFFFF, Ui.dp(a, 22)));
        panel.setPadding(pad, Ui.dp(a, 16), pad, Ui.dp(a, 14));

        View handle = new View(a);
        handle.setBackground(Ui.round(0xFFD8DCE3, Ui.dp(a, 2)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dp(a, 38), Ui.dp(a, 4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = Ui.dp(a, 14);
        panel.addView(handle, hl);

        TextView head = new TextView(a);
        head.setText(title);
        head.setTextSize(18);
        head.setTextColor(0xFF16181D);
        head.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        panel.addView(head);

        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(a, 12);
        panel.addView(body, bl);

        buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        LinearLayout.LayoutParams bul = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bul.topMargin = Ui.dp(a, 16);
        panel.addView(buttons, bul);

        FrameLayout outer = new FrameLayout(a);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.leftMargin = Ui.dp(a, 14);
        flp.rightMargin = Ui.dp(a, 14);
        outer.addView(panel, flp);
        dialog.setContentView(outer);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.42f;
            w.setAttributes(lp);
        }
    }

    Sheet add(View view) {
        body.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return this;
    }

    Sheet text(String text) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(0xFF848A94);
        t.setLineSpacing(Ui.dp(activity, 3), 1f);
        return add(t);
    }

    Sheet addScrollable(View content) {
        ScrollView sv = new ScrollView(activity) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content);
        return add(sv);
    }

    Sheet cancel(String label) {
        TextView b = button(label, false);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        buttons.addView(b, btnParams());
        return this;
    }

    /** 蓝色按钮，只负责关掉弹窗。 */
    Sheet done(String label) {
        TextView b = button(label, true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        buttons.addView(b, btnParams());
        return this;
    }

    Sheet primary(String label, final Runnable action) {
        TextView b = button(label, true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (action != null) {
                    action.run();
                }
            }
        });
        buttons.addView(b, btnParams());
        return this;
    }

    void show() {
        Ui.bounceAll(panel);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
    }

    void dismiss() {
        dialog.dismiss();
    }

    void showKeyboard(View focus) {
        focus.requestFocus();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        InputMethodManager ime =
                (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (ime != null) {
            ime.showSoftInput(focus, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private TextView button(String label, boolean primary) {
        TextView t = new TextView(activity);
        t.setText(label);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 10), Ui.dp(activity, 20), Ui.dp(activity, 10));
        if (primary) {
            t.setTextColor(Color.WHITE);
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            t.setBackground(Ui.ripple(0xFF2F6FED, Ui.dp(activity, 20), 0x55FFFFFF));
        } else {
            t.setTextColor(0xFF5A6070);
            t.setBackground(Ui.ripple(0xFFF1F2F5, Ui.dp(activity, 20), 0x22000000));
        }
        return t;
    }

    private LinearLayout.LayoutParams btnParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(activity, 10);
        return lp;
    }
}
