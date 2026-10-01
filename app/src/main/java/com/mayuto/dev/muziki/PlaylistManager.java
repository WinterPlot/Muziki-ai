package com.mayuto.dev.muziki;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Plain-framework playlist storage. No AndroidX/AppCompat and no lambdas.
 * Playlists are stored in SharedPreferences so they survive app restarts.
 */
public class PlaylistManager {
    private static final String PREF = "muziki_playlists_v1";
    private static final String INDEX = "playlist_index";

    public static class Playlist {
        public String id, name, coverUri;
        public ArrayList<String> paths = new ArrayList<String>();
        public Playlist(String id, String name) { this.id=id; this.name=name; }
    }

    private static String enc(String s) {
        if (s == null) s = "";
        return Base64.encodeToString(s.getBytes(), Base64.NO_WRAP);
    }
    private static String dec(String s) {
        try { return new String(Base64.decode(s, Base64.DEFAULT)); }
        catch (Exception e) { return ""; }
    }

    private static SharedPreferences prefs(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c);
    }

    public static ArrayList<Playlist> getAll(Context c) {
        ArrayList<Playlist> out = new ArrayList<Playlist>();
        String index = prefs(c).getString(INDEX, "");
        if (index.length() == 0) return out;
        String[] ids = index.split("\\|");
        for (String id : ids) {
            if (id.length() == 0) continue;
            String raw = prefs(c).getString(PREF + "_" + id, "");
            if (raw.length() == 0) continue;
            String[] parts = raw.split("\\n", -1);
            String name = parts.length > 0 ? dec(parts[0]) : "Playlist";
            Playlist p = new Playlist(id, name);
            p.coverUri = parts.length > 1 ? dec(parts[1]) : "";
            if (parts.length > 2 && parts[2].length() > 0) {
                String[] ps = parts[2].split(",");
                for (String x : ps) {
                    String path = dec(x);
                    if (path.length() > 0) p.paths.add(path);
                }
            }
            out.add(p);
        }
        return out;
    }

    public static Playlist get(Context c, String id) {
        ArrayList<Playlist> all = getAll(c);
        for (Playlist p : all) if (p.id.equals(id)) return p;
        return null;
    }

    private static void writeIndex(Context c, ArrayList<Playlist> all) {
        StringBuilder b = new StringBuilder();
        for (Playlist p : all) {
            if (b.length() > 0) b.append("|");
            b.append(p.id);
        }
        prefs(c).edit().putString(INDEX, b.toString()).apply();
    }

    public static Playlist create(Context c, String name) {
        String id = String.valueOf(System.currentTimeMillis());
        Playlist p = new Playlist(id, name == null || name.trim().length()==0 ? "New Playlist" : name.trim());
        ArrayList<Playlist> all = getAll(c);
        all.add(p);
        save(c, p);
        writeIndex(c, all);
        return p;
    }

    public static void save(Context c, Playlist p) {
        StringBuilder paths = new StringBuilder();
        for (String path : p.paths) {
            if (paths.length() > 0) paths.append(",");
            paths.append(enc(path));
        }
        String raw = enc(p.name) + "\n" + enc(p.coverUri) + "\n" + paths.toString();
        prefs(c).edit().putString(PREF + "_" + p.id, raw).apply();
    }

    public static void rename(Context c, Playlist p, String name) {
        p.name = name;
        save(c, p);
    }

    public static void delete(Context c, Playlist p) {
        ArrayList<Playlist> all = getAll(c);
        for (int i=0;i<all.size();i++) if (all.get(i).id.equals(p.id)) { all.remove(i); break; }
        prefs(c).edit().remove(PREF + "_" + p.id).apply();
        writeIndex(c, all);
    }

    public static void setCover(Context c, Playlist p, String uri) {
        p.coverUri = uri == null ? "" : uri;
        save(c, p);
    }

    public static boolean contains(Playlist p, String path) { return p.paths.contains(path); }

    public static void add(Playlist p, String path) {
        if (path != null && path.length() > 0 && !p.paths.contains(path)) p.paths.add(path);
    }

    public static void remove(Playlist p, String path) { p.paths.remove(path); }

    public static void reorder(Playlist p, int from, int to) {
        if (from < 0 || from >= p.paths.size() || to < 0 || to >= p.paths.size() || from == to) return;
        String item = p.paths.remove(from);
        p.paths.add(to, item);
    }
}
