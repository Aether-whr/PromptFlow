package com.dsh.tp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 保存下来的文案，存在 SharedPreferences 的 JSON 里。 */
final class Scripts {

    static final class Item {
        final String id;
        final String title;
        final String text;
        final long at;

        Item(String id, String title, String text, long at) {
            this.id = id;
            this.title = title;
            this.text = text;
            this.at = at;
        }
    }

    private Scripts() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("teleprompter", Context.MODE_PRIVATE);
    }

    static List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("scripts", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Item(o.optString("id"), o.optString("title"),
                        o.optString("text"), o.optLong("at")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static void add(Context c, String title, String text) {
        List<Item> items = list(c);
        long now = System.currentTimeMillis();
        items.add(0, new Item(String.valueOf(now), title, text, now));
        while (items.size() > 60) {
            items.remove(items.size() - 1);
        }
        write(c, items);
    }

    static void remove(Context c, String id) {
        List<Item> items = list(c);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                items.remove(i);
                break;
            }
        }
        write(c, items);
    }

    private static void write(Context c, List<Item> items) {
        JSONArray arr = new JSONArray();
        for (Item it : items) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", it.id);
                o.put("title", it.title);
                o.put("text", it.text);
                o.put("at", it.at);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        prefs(c).edit().putString("scripts", arr.toString()).apply();
    }
}
