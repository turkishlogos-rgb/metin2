package com.emir.sahabasvuru;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Db extends SQLiteOpenHelper {
    public static class Trip {
        public long id;
        public String name;
        public String created;
        public int count;
    }

    public static class Site {
        public long id;
        public long tripId;
        public String code = "";
        public String name = "";
        public String province = "";
        public String delivery = "";
        public String missing = "";
        public String status = "Bekliyor";
        public String contact = "";
        public String phone = "";
        public String followUp = "";
        public String note = "";
        public String updated = "";
    }

    public Db(Context context) {
        super(context, "saha_basvuru.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE trips(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,created TEXT NOT NULL)");
        db.execSQL("CREATE TABLE sites(id INTEGER PRIMARY KEY AUTOINCREMENT,trip_id INTEGER NOT NULL,code TEXT,name TEXT,province TEXT,delivery TEXT,missing TEXT,status TEXT,contact TEXT,phone TEXT,follow_up TEXT,note TEXT,updated TEXT)");
        db.execSQL("CREATE INDEX idx_sites_trip ON sites(trip_id)");
        db.execSQL("CREATE INDEX idx_sites_code ON sites(code)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public long addTrip(String name, String created, List<Site> sites) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues trip = new ContentValues();
            trip.put("name", name);
            trip.put("created", created);
            long tripId = db.insertOrThrow("trips", null, trip);
            for (Site site : sites) {
                site.tripId = tripId;
                db.insertOrThrow("sites", null, values(site));
            }
            db.setTransactionSuccessful();
            return tripId;
        } finally {
            db.endTransaction();
        }
    }

    private ContentValues values(Site s) {
        ContentValues v = new ContentValues();
        v.put("trip_id", s.tripId); v.put("code", s.code); v.put("name", s.name);
        v.put("province", s.province); v.put("delivery", s.delivery); v.put("missing", s.missing);
        v.put("status", s.status); v.put("contact", s.contact); v.put("phone", s.phone);
        v.put("follow_up", s.followUp); v.put("note", s.note); v.put("updated", s.updated);
        return v;
    }

    public void updateSite(Site s) {
        getWritableDatabase().update("sites", values(s), "id=?", new String[]{String.valueOf(s.id)});
    }

    public List<Trip> trips() {
        List<Trip> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT t.id,t.name,t.created,COUNT(s.id) FROM trips t LEFT JOIN sites s ON s.trip_id=t.id GROUP BY t.id ORDER BY t.id DESC", null);
        while (c.moveToNext()) {
            Trip t = new Trip();
            t.id = c.getLong(0); t.name = c.getString(1); t.created = c.getString(2); t.count = c.getInt(3);
            out.add(t);
        }
        c.close();
        return out;
    }

    public List<Site> sites(long tripId) {
        List<Site> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("sites", null, "trip_id=?", new String[]{String.valueOf(tripId)}, null, null, "province,name");
        while (c.moveToNext()) out.add(readSite(c));
        c.close();
        return out;
    }

    private Site readSite(Cursor c) {
        Site s = new Site();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.tripId = c.getLong(c.getColumnIndexOrThrow("trip_id"));
        s.code = text(c, "code"); s.name = text(c, "name"); s.province = text(c, "province");
        s.delivery = text(c, "delivery"); s.missing = text(c, "missing"); s.status = text(c, "status");
        s.contact = text(c, "contact"); s.phone = text(c, "phone"); s.followUp = text(c, "follow_up");
        s.note = text(c, "note"); s.updated = text(c, "updated");
        return s;
    }

    private String text(Cursor c, String col) {
        String value = c.getString(c.getColumnIndexOrThrow(col));
        return value == null ? "" : value;
    }

    public void deleteTrip(long tripId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("sites", "trip_id=?", new String[]{String.valueOf(tripId)});
            db.delete("trips", "id=?", new String[]{String.valueOf(tripId)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public JSONObject backup() throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray tripArray = new JSONArray();
        for (Trip t : trips()) {
            JSONObject jt = new JSONObject();
            jt.put("name", t.name); jt.put("created", t.created);
            JSONArray siteArray = new JSONArray();
            for (Site s : sites(t.id)) {
                JSONObject js = new JSONObject();
                js.put("code", s.code); js.put("name", s.name); js.put("province", s.province);
                js.put("delivery", s.delivery); js.put("missing", s.missing); js.put("status", s.status);
                js.put("contact", s.contact); js.put("phone", s.phone); js.put("followUp", s.followUp);
                js.put("note", s.note); js.put("updated", s.updated);
                siteArray.put(js);
            }
            jt.put("sites", siteArray);
            tripArray.put(jt);
        }
        root.put("trips", tripArray);
        return root;
    }

    public void restore(JSONObject root) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("sites", null, null);
            db.delete("trips", null, null);
            JSONArray trips = root.getJSONArray("trips");
            for (int i = 0; i < trips.length(); i++) {
                JSONObject jt = trips.getJSONObject(i);
                ContentValues tv = new ContentValues();
                tv.put("name", jt.optString("name")); tv.put("created", jt.optString("created"));
                long tripId = db.insertOrThrow("trips", null, tv);
                JSONArray sites = jt.getJSONArray("sites");
                for (int j = 0; j < sites.length(); j++) {
                    JSONObject js = sites.getJSONObject(j);
                    Site s = new Site(); s.tripId = tripId;
                    s.code = js.optString("code"); s.name = js.optString("name"); s.province = js.optString("province");
                    s.delivery = js.optString("delivery"); s.missing = js.optString("missing"); s.status = js.optString("status", "Bekliyor");
                    s.contact = js.optString("contact"); s.phone = js.optString("phone"); s.followUp = js.optString("followUp");
                    s.note = js.optString("note"); s.updated = js.optString("updated");
                    db.insertOrThrow("sites", null, values(s));
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
