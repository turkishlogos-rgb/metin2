package com.emir.sahabasvuru;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int OPEN_LIST = 10, CREATE_BACKUP = 11, OPEN_BACKUP = 12, CREATE_CSV = 13;
    private final int NAVY = Color.rgb(23, 74, 103), TEAL = Color.rgb(14, 124, 114);
    private Db db;
    private LinearLayout root, content;
    private Db.Trip currentTrip;
    private Uri pendingUri;
    private String pendingName;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Db(this);
        seed();
        showTrips();
    }

    private void frame(String title, boolean back) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(242, 245, 247));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(10));
        bar.setBackgroundColor(NAVY);
        if (back) {
            Button b = button("‹", Color.TRANSPARENT, Color.WHITE);
            b.setTextSize(28); b.setOnClickListener(v -> showTrips());
            bar.addView(b, new LinearLayout.LayoutParams(dp(52), dp(52)));
        }
        TextView heading = text(title, 20, Color.WHITE, true);
        bar.addView(heading, new LinearLayout.LayoutParams(0, dp(56), 1));
        root.addView(bar);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(28));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void showTrips() {
        currentTrip = null;
        frame("Saha Başvuru Takip", false);
        TextView lead = text("Çalışmalar", 22, NAVY, true);
        content.addView(lead);
        content.addView(text("Her seyahati ayrı tutun; yeni listeyi DOCX, XLSX veya CSV olarak içe aktarın.", 13, Color.DKGRAY, false), margin(-1, -2, 0, 4, 0, 14));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button add = button("+ Yeni liste", TEAL, Color.WHITE);
        add.setOnClickListener(v -> openList());
        Button backup = button("Yedekle", Color.WHITE, NAVY);
        backup.setOnClickListener(v -> createBackup());
        Button restore = button("Geri yükle", Color.WHITE, NAVY);
        restore.setOnClickListener(v -> openBackup());
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(space(dp(7), 1));
        actions.addView(backup, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(space(dp(7), 1));
        actions.addView(restore, new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(actions);

        List<Db.Trip> trips = db.trips();
        for (Db.Trip trip : trips) {
            LinearLayout card = card();
            TextView name = text(trip.name, 17, Color.rgb(21, 42, 58), true);
            card.addView(name);
            card.addView(text(trip.count + " kayıt  •  " + trip.created, 12, Color.GRAY, false), margin(-1, -2, 0, 4, 0, 0));
            card.setOnClickListener(v -> showSites(trip));
            card.setOnLongClickListener(v -> { confirmDelete(trip); return true; });
            content.addView(card, margin(-1, -2, 0, 12, 0, 0));
        }
        if (trips.isEmpty()) content.addView(text("Henüz çalışma yok. İlk listenizi içe aktarın.", 15, Color.GRAY, false), margin(-1, -2, 0, 30, 0, 0));
    }

    private void showSites(Db.Trip trip) {
        currentTrip = trip;
        frame(trip.name, true);
        List<Db.Site> all = db.sites(trip.id);

        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER);
        stats.setBackgroundColor(NAVY);
        int completed = 0, active = 0;
        for (Db.Site s : all) {
            if ("Tamamlandı".equals(s.status)) completed++;
            else if (!"Bekliyor".equals(s.status)) active++;
        }
        stats.addView(stat("Toplam", all.size()), new LinearLayout.LayoutParams(0, dp(72), 1));
        stats.addView(stat("İşlemde", active), new LinearLayout.LayoutParams(0, dp(72), 1));
        stats.addView(stat("Tamam", completed), new LinearLayout.LayoutParams(0, dp(72), 1));
        content.addView(stats);

        EditText search = input("Lokasyon veya kod ara");
        content.addView(search, margin(-1, dp(48), 0, 12, 0, 7));

        LinearLayout filters = new LinearLayout(this);
        Spinner province = spinner(provinces(all));
        Spinner status = spinner(new String[]{"Tüm durumlar", "Bekliyor", "Görüşüldü", "Başvuru Yapıldı", "Tamamlandı"});
        filters.addView(province, new LinearLayout.LayoutParams(0, dp(48), 1));
        filters.addView(space(dp(7), 1));
        filters.addView(status, new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(filters);

        Button export = button("CSV dışa aktar", Color.WHITE, NAVY);
        export.setOnClickListener(v -> createCsv());
        content.addView(export, margin(-1, dp(44), 0, 8, 0, 10));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        Runnable render = () -> renderSites(list, all, search.getText().toString(), province.getSelectedItem().toString(), status.getSelectedItem().toString());
        search.addTextChangedListener(new SimpleWatcher(render));
        province.setOnItemSelectedListener(new Selected(render));
        status.setOnItemSelectedListener(new Selected(render));
        render.run();
    }

    private void renderSites(LinearLayout list, List<Db.Site> all, String query, String province, String status) {
        list.removeAllViews();
        String q = query.toLowerCase(new Locale("tr", "TR"));
        for (Db.Site s : all) {
            if (!"Tüm iller".equals(province) && !s.province.equals(province)) continue;
            if (!"Tüm durumlar".equals(status) && !s.status.equals(status)) continue;
            if (!q.isEmpty() && !(s.name + " " + s.code + " " + s.missing).toLowerCase(new Locale("tr", "TR")).contains(q)) continue;
            LinearLayout card = card();
            LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(s.name, 16, Color.rgb(21, 42, 58), true);
            line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView badge = text(s.status, 11, statusColor(s.status), true);
            badge.setPadding(dp(9), dp(5), dp(9), dp(5));
            line.addView(badge);
            card.addView(line);
            card.addView(text(s.code + "  •  " + s.province, 11, Color.GRAY, false), margin(-1, -2, 0, 4, 0, 0));
            if (!s.missing.isEmpty() && !"Hayır".equals(s.missing)) card.addView(text("Eksik: " + s.missing, 12, Color.rgb(138, 84, 0), true), margin(-1, -2, 0, 7, 0, 0));
            if (!s.note.isEmpty()) card.addView(text(s.note, 13, Color.DKGRAY, false), margin(-1, -2, 0, 7, 0, 0));
            card.setOnClickListener(v -> editSite(s));
            list.addView(card, margin(-1, -2, 0, 8, 0, 0));
        }
    }

    private void editSite(Db.Site site) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), 0, dp(20), 0);
        Spinner status = spinner(new String[]{"Bekliyor", "Görüşüldü", "Başvuru Yapıldı", "Tamamlandı"});
        for (int i = 0; i < status.getCount(); i++) if (status.getItemAtPosition(i).equals(site.status)) status.setSelection(i);
        EditText contact = input("Yetkili kişi"); contact.setText(site.contact);
        EditText phone = input("Telefon"); phone.setText(site.phone); phone.setInputType(3);
        EditText follow = input("Takip tarihi"); follow.setText(site.followUp); follow.setFocusable(false);
        follow.setOnClickListener(v -> chooseDate(follow));
        EditText note = input("Görüşme notu"); note.setText(site.note); note.setMinLines(4); note.setGravity(Gravity.TOP);
        box.addView(label("Durum")); box.addView(status, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(label("Yetkili kişi")); box.addView(contact);
        box.addView(label("Telefon")); box.addView(phone);
        box.addView(label("Takip tarihi")); box.addView(follow);
        box.addView(label("Görüşme notu")); box.addView(note);
        new AlertDialog.Builder(this).setTitle(site.name).setView(box)
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Kaydet", (d, w) -> {
                site.status = status.getSelectedItem().toString(); site.contact = contact.getText().toString().trim();
                site.phone = phone.getText().toString().trim(); site.followUp = follow.getText().toString().trim();
                site.note = note.getText().toString().trim(); site.updated = now();
                db.updateSite(site); showSites(currentTrip);
            }).show();
    }

    private void chooseDate(EditText target) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> target.setText(String.format(Locale.US, "%02d.%02d.%04d", d, m + 1, y)),
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void openList() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/plain"});
        i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, OPEN_LIST);
    }

    private void importList(Uri uri) {
        String filename = filename(uri);
        try {
            List<Db.Site> sites = Importer.parse(getContentResolver(), uri, filename);
            if (sites.isEmpty()) { toast("Tablo okunamadı veya kayıt bulunamadı."); return; }
            EditText name = input("Çalışma adı");
            name.setText(filename.replaceFirst("\\.[^.]+$", ""));
            new AlertDialog.Builder(this).setTitle(sites.size() + " kayıt bulundu").setView(name)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("İçe aktar", (d, w) -> {
                    db.addTrip(name.getText().toString().trim(), today(), sites);
                    showTrips(); toast("Yeni çalışma oluşturuldu.");
                }).show();
        } catch (Exception e) { toast("Dosya okunamadı: " + e.getMessage()); }
    }

    private void createBackup() {
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "saha-basvuru-yedek-" + todayFile() + ".json"), CREATE_BACKUP);
    }

    private void openBackup() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE), OPEN_BACKUP);
    }

    private void createCsv() {
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv")
            .putExtra(Intent.EXTRA_TITLE, currentTrip.name + ".csv"), CREATE_CSV);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (request == OPEN_LIST) importList(uri);
            else if (request == CREATE_BACKUP) {
                write(uri, db.backup().toString(2)); toast("Yedek kaydedildi.");
            } else if (request == OPEN_BACKUP) {
                String json = read(uri); new AlertDialog.Builder(this).setTitle("Yedeği geri yükle")
                    .setMessage("Mevcut kayıtlar silinip yedekteki kayıtlarla değiştirilecek.")
                    .setNegativeButton("Vazgeç", null).setPositiveButton("Geri yükle", (d, w) -> {
                        try { db.restore(new JSONObject(json)); showTrips(); toast("Yedek geri yüklendi."); }
                        catch (Exception e) { toast("Yedek yüklenemedi."); }
                    }).show();
            } else if (request == CREATE_CSV) {
                write(uri, csv(db.sites(currentTrip.id))); toast("CSV kaydedildi.");
            }
        } catch (Exception e) { toast("İşlem tamamlanamadı: " + e.getMessage()); }
    }

    private String csv(List<Db.Site> sites) {
        StringBuilder out = new StringBuilder("\uFEFFİl;Lokasyon Kodu;Adı;Veriliş Şekli;Eksik;Durum;Yetkili;Telefon;Takip Tarihi;Not;Son Güncelleme\n");
        for (Db.Site s : sites) out.append(join(s.province,s.code,s.name,s.delivery,s.missing,s.status,s.contact,s.phone,s.followUp,s.note,s.updated)).append('\n');
        return out.toString();
    }

    private String join(String... cells) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < cells.length; i++) { if (i > 0) b.append(';'); b.append('"').append(cells[i].replace("\"", "\"\"")).append('"'); }
        return b.toString();
    }

    private void confirmDelete(Db.Trip trip) {
        new AlertDialog.Builder(this).setTitle("Çalışmayı sil").setMessage(trip.name + " ve içindeki tüm notlar silinecek.")
            .setNegativeButton("Vazgeç", null).setPositiveButton("Sil", (d,w) -> { db.deleteTrip(trip.id); showTrips(); }).show();
    }

    private void seed() {
        if (!db.trips().isEmpty()) return;
        try {
            InputStream in = getAssets().open("initial.csv");
            List<Db.Site> sites = new ArrayList<>();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line; br.readLine();
            while ((line = br.readLine()) != null) {
                String[] c = line.split(";", -1); if (c.length < 5) continue;
                Db.Site s = new Db.Site(); s.code=c[0]; s.name=c[1]; s.province=c[2]; s.delivery=c[3]; s.missing=c[4]; sites.add(s);
            }
            if (!sites.isEmpty()) db.addTrip("İlk çalışma", today(), sites);
        } catch (Exception ignored) {}
    }

    private String[] provinces(List<Db.Site> sites) {
        Set<String> p = new LinkedHashSet<>(); p.add("Tüm iller"); for (Db.Site s : sites) p.add(s.province);
        return p.toArray(new String[0]);
    }

    private LinearLayout stat(String label, int count) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        box.addView(text(String.valueOf(count), 22, Color.WHITE, true)); box.addView(text(label, 11, Color.LTGRAY, false)); return box;
    }
    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(14),dp(14),dp(14),dp(14));
        v.setBackgroundColor(Color.WHITE); v.setElevation(dp(2)); return v;
    }
    private TextView label(String s) { return text(s, 12, Color.DKGRAY, true); }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private EditText input(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(16); e.setSingleLine(false); e.setPadding(dp(12),dp(8),dp(12),dp(8));
        e.setBackgroundColor(Color.WHITE); return e;
    }
    private Button button(String label, int bg, int fg) {
        Button b = new Button(this); b.setText(label); b.setTextColor(fg); b.setTextSize(13); b.setAllCaps(false); b.setBackgroundColor(bg); return b;
    }
    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this); s.setBackgroundColor(Color.WHITE);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); return s;
    }
    private View space(int width, int height) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(width,height)); return v; }
    private int statusColor(String s) {
        if ("Tamamlandı".equals(s)) return Color.rgb(40,122,80);
        if ("Başvuru Yapıldı".equals(s)) return Color.rgb(47,120,183);
        if ("Görüşüldü".equals(s)) return Color.rgb(210,127,0);
        return Color.GRAY;
    }
    private LinearLayout.LayoutParams margin(int w,int h,int l,int t,int r,int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private String today() { return new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(new Date()); }
    private String todayFile() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
    private String now() { return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date()); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private String filename(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        }
        return "liste";
    }
    private void write(Uri uri, String text) throws Exception { try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(text.getBytes(StandardCharsets.UTF_8)); } }
    private String read(Uri uri) throws Exception {
        StringBuilder b = new StringBuilder(); try (BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) b.append(line);
        } return b.toString();
    }

    private static class SimpleWatcher implements TextWatcher {
        private final Runnable r; SimpleWatcher(Runnable r){this.r=r;}
        public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){r.run();} public void afterTextChanged(Editable e){}
    }
    private static class Selected implements AdapterView.OnItemSelectedListener {
        private final Runnable r; Selected(Runnable r){this.r=r;}
        public void onItemSelected(AdapterView<?> p,View v,int pos,long id){r.run();} public void onNothingSelected(AdapterView<?> p){}
    }
}
