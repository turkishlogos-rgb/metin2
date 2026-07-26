package com.emir.sahabasvuru;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Importer {
    public static List<Db.Site> parse(ContentResolver resolver, Uri uri, String name) throws Exception {
        byte[] bytes;
        try (InputStream in = resolver.openInputStream(uri)) { bytes = readAll(in); }
        String lower = name.toLowerCase();
        List<List<String>> rows;
        if (lower.endsWith(".docx")) rows = docx(bytes);
        else if (lower.endsWith(".xlsx")) rows = xlsx(bytes);
        else rows = csv(new String(bytes, StandardCharsets.UTF_8));
        return mapRows(rows);
    }

    private static List<Db.Site> mapRows(List<List<String>> rows) {
        List<Db.Site> out = new ArrayList<>();
        if (rows.isEmpty()) return out;
        List<String> header = rows.get(0);
        int code = find(header, "LOKASYON KODU"), siteName = find(header, "ADI"), province = find(header, "İLİ");
        int delivery = find(header, "VERİLİŞ ŞEKLİ"), missing = find(header, "EKSİK VAR MI");
        if (code < 0 || siteName < 0 || province < 0) return out;
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String c = get(row, code).trim();
            if (c.isEmpty()) continue;
            Db.Site s = new Db.Site();
            s.code = c; s.name = get(row, siteName).trim(); s.province = get(row, province).trim();
            s.delivery = get(row, delivery).trim(); s.missing = get(row, missing).trim();
            out.add(s);
        }
        return out;
    }

    private static int find(List<String> header, String wanted) {
        String target = normalize(wanted);
        for (int i = 0; i < header.size(); i++) if (normalize(header.get(i)).contains(target)) return i;
        return -1;
    }

    private static String normalize(String s) {
        return s.toUpperCase().replace("İ", "I").replace("Ş", "S").replace("Ğ", "G")
            .replace("Ü", "U").replace("Ö", "O").replace("Ç", "C")
            .replace("?", "").replaceAll("\\s+", " ").trim();
    }

    private static String get(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }

    private static List<List<String>> csv(String text) {
        List<List<String>> rows = new ArrayList<>();
        String first = text.split("\\R", 2)[0];
        char sep = first.chars().filter(ch -> ch == ';').count() >= first.chars().filter(ch -> ch == ',').count() ? ';' : ',';
        List<String> row = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quote = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (quote && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quote = !quote;
            } else if (ch == sep && !quote) { row.add(cell.toString()); cell.setLength(0); }
            else if ((ch == '\n' || ch == '\r') && !quote) {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                if (!row.stream().allMatch(String::isEmpty)) rows.add(row);
                row = new ArrayList<>();
            } else cell.append(ch);
        }
        row.add(cell.toString()); if (!row.stream().allMatch(String::isEmpty)) rows.add(row);
        return rows;
    }

    private static List<List<String>> docx(byte[] bytes) throws Exception {
        Map<String, byte[]> zip = unzip(bytes);
        byte[] xml = zip.get("word/document.xml");
        if (xml == null) return new ArrayList<>();
        XmlPullParser p = Xml.newPullParser(); p.setInput(new ByteArrayInputStream(xml), "UTF-8");
        List<List<String>> rows = new ArrayList<>(); List<String> row = null; StringBuilder cell = null;
        int event;
        while ((event = p.next()) != XmlPullParser.END_DOCUMENT) {
            String tag = p.getName();
            if (event == XmlPullParser.START_TAG && "tr".equals(tag)) row = new ArrayList<>();
            else if (event == XmlPullParser.START_TAG && "tc".equals(tag)) cell = new StringBuilder();
            else if (event == XmlPullParser.START_TAG && "t".equals(tag) && cell != null) cell.append(p.nextText());
            else if (event == XmlPullParser.END_TAG && "tc".equals(tag) && row != null) row.add(cell == null ? "" : cell.toString());
            else if (event == XmlPullParser.END_TAG && "tr".equals(tag) && row != null) rows.add(row);
        }
        return rows;
    }

    private static List<List<String>> xlsx(byte[] bytes) throws Exception {
        Map<String, byte[]> zip = unzip(bytes);
        List<String> shared = new ArrayList<>();
        byte[] sharedXml = zip.get("xl/sharedStrings.xml");
        if (sharedXml != null) {
            XmlPullParser p = Xml.newPullParser(); p.setInput(new ByteArrayInputStream(sharedXml), "UTF-8");
            StringBuilder item = null; int event;
            while ((event = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "si".equals(p.getName())) item = new StringBuilder();
                else if (event == XmlPullParser.START_TAG && "t".equals(p.getName()) && item != null) item.append(p.nextText());
                else if (event == XmlPullParser.END_TAG && "si".equals(p.getName())) shared.add(item.toString());
            }
        }
        byte[] sheet = zip.get("xl/worksheets/sheet1.xml");
        List<List<String>> rows = new ArrayList<>();
        if (sheet == null) return rows;
        XmlPullParser p = Xml.newPullParser(); p.setInput(new ByteArrayInputStream(sheet), "UTF-8");
        List<String> row = null; String type = ""; String ref = ""; String value = ""; int event;
        while ((event = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "row".equals(p.getName())) row = new ArrayList<>();
            else if (event == XmlPullParser.START_TAG && "c".equals(p.getName())) { type = p.getAttributeValue(null, "t"); ref = p.getAttributeValue(null, "r"); value = ""; }
            else if (event == XmlPullParser.START_TAG && ("v".equals(p.getName()) || "t".equals(p.getName()))) value = p.nextText();
            else if (event == XmlPullParser.END_TAG && "c".equals(p.getName()) && row != null) {
                int col = column(ref); while (row.size() < col) row.add("");
                if ("s".equals(type) && !value.isEmpty()) value = shared.get(Integer.parseInt(value));
                row.add(value);
            } else if (event == XmlPullParser.END_TAG && "row".equals(p.getName()) && row != null) rows.add(row);
        }
        return rows;
    }

    private static int column(String ref) {
        int n = 0; if (ref == null) return 0;
        for (int i = 0; i < ref.length() && Character.isLetter(ref.charAt(i)); i++) n = n * 26 + Character.toUpperCase(ref.charAt(i)) - 'A' + 1;
        return Math.max(0, n - 1);
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) if (!entry.isDirectory()) out.put(entry.getName(), readAll(zin));
        }
        return out;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
