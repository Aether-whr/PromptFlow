package com.dsh.tp;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 从 .docx 里取纯文本：解 zip，读 word/document.xml，拼起所有 <w:t>。 */
final class WordImport {

    private WordImport() {
    }

    static String read(Context c, Uri uri) throws Exception {
        InputStream raw = c.getContentResolver().openInputStream(uri);
        if (raw == null) {
            return "";
        }
        ZipInputStream zip = new ZipInputStream(raw);
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(zip, "UTF-8");
                StringBuilder sb = new StringBuilder();
                int event = parser.getEventType();
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        String name = parser.getName();
                        if ("t".equals(name)) {
                            String text = parser.nextText();
                            if (text != null) {
                                sb.append(text);
                            }
                        } else if ("tab".equals(name)) {
                            sb.append('\t');
                        } else if ("br".equals(name) || "cr".equals(name)) {
                            sb.append('\n');
                        }
                    } else if (event == XmlPullParser.END_TAG && "p".equals(parser.getName())) {
                        sb.append('\n');
                    }
                    event = parser.next();
                }
                return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
            }
        } finally {
            try {
                zip.close();
            } catch (Exception ignored) {
            }
            try {
                raw.close();
            } catch (Exception ignored) {
            }
        }
        return "";
    }
}
