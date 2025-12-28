package com.applisto.appcloner;

import android.content.SharedPreferences;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PreferencesMonitor {

    private static final AtomicLong INDEX = new AtomicLong(0);
    private static final TreeMap<Long, Map<String, Object>> MAP = new TreeMap<>();

    private static int maxEntries = 2000;
    private static int valueTruncateLength = 512;

    private static final Set<String> positiveKeyFilter = new HashSet<>();
    private static final Set<String> negativeKeyFilter = new HashSet<>();
    private static final Set<String> positiveValueFilter = new HashSet<>();
    private static final Set<String> negativeValueFilter = new HashSet<>();

    private PreferencesMonitor() {}

    public static void install() {
        new SharedPreferencesHook().install(null);
    }

    public static void configure(
            int maxEntries,
            int valueTruncateLength,
            Collection<String> positiveKey,
            Collection<String> negativeKey,
            Collection<String> positiveValue,
            Collection<String> negativeValue
    ) {
        PreferencesMonitor.maxEntries = Math.max(1, maxEntries);
        PreferencesMonitor.valueTruncateLength = Math.max(0, valueTruncateLength);

        positiveKeyFilter.clear(); negativeKeyFilter.clear();
        positiveValueFilter.clear(); negativeValueFilter.clear();

        if (positiveKey != null) positiveKeyFilter.addAll(positiveKey);
        if (negativeKey != null) negativeKeyFilter.addAll(negativeKey);
        if (positiveValue != null) positiveValueFilter.addAll(positiveValue);
        if (negativeValue != null) negativeValueFilter.addAll(negativeValue);
    }

    public static void clear() {
        synchronized (MAP) {
            MAP.clear();
            INDEX.set(0);
        }
    }

    public static TreeMap<Long, Map<String, Object>> snapshot() {
        synchronized (MAP) {
            return new TreeMap<>(MAP);
        }
    }

    public static void writeMonitorCsv(Writer w, long afterIndexExclusive) throws IOException {
        List<Map.Entry<Long, Map<String, Object>>> snapshot;
        synchronized (MAP) {
            snapshot = new ArrayList<>(MAP.tailMap(afterIndexExclusive, false).entrySet());
        }

        w.write("Index,Timestamp,Method,FileName,Key,Value,DefaultValue\n");
        for (Map.Entry<Long, Map<String, Object>> e : snapshot) {
            long idx = e.getKey();
            Map<String, Object> m = e.getValue();
            w.write(Long.toString(idx)); w.write(',');
            w.write(csvEscape(String.valueOf(m.get("timestamp")))); w.write(',');
            w.write(csvEscape(String.valueOf(m.get("method")))); w.write(',');
            w.write(csvEscape(String.valueOf(m.get("fileName")))); w.write(',');
            w.write(csvEscape(String.valueOf(m.get("key")))); w.write(',');
            w.write(csvEscape(String.valueOf(m.get("value")))); w.write(',');
            w.write(csvEscape(String.valueOf(m.get("defaultValue")))); w.write('\n');
        }
        w.flush();
    }

    public static void addEntry(String method, String fileName, String key, String value, String defaultValue) {
        if (!allow(key, positiveKeyFilter, negativeKeyFilter)) return;
        if (!allow(value, positiveValueFilter, negativeValueFilter)) return;

        String v = truncate(value, valueTruncateLength);

        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", System.currentTimeMillis());
        entry.put("method", method);
        entry.put("fileName", fileName);
        entry.put("key", key);
        entry.put("value", v);
        entry.put("defaultValue", defaultValue);

        synchronized (MAP) {
            long idx = INDEX.incrementAndGet();
            MAP.put(idx, entry);
            while (MAP.size() > maxEntries) MAP.pollFirstEntry();
        }
    }

    private static boolean allow(String s, Set<String> positive, Set<String> negative) {
        if (s == null) s = "";
        for (String n : negative) {
            if (n != null && !n.isEmpty() && s.contains(n)) return false;
        }
        if (positive.isEmpty()) return true;
        for (String p : positive) {
            if (p != null && !p.isEmpty() && s.contains(p)) return true;
        }
        return false;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (max <= 0) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String csvEscape(String s) {
        if (s == null) s = "";
        boolean needQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!needQuotes) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public static SharedPreferences wrap(String fileName, SharedPreferences delegate) {
        return new LoggedSharedPreferences(fileName, delegate);
    }

    private static final class LoggedSharedPreferences implements SharedPreferences {
        private final String fileName;
        private final SharedPreferences d;

        LoggedSharedPreferences(String fileName, SharedPreferences delegate) {
            this.fileName = fileName == null ? "prefs" : fileName;
            this.d = delegate;
        }

        @Override public Map<String, ?> getAll() { return d.getAll(); }

        @Override public String getString(String key, String defValue) {
            String v = d.getString(key, defValue);
            addEntry("getString", fileName, key, String.valueOf(v), String.valueOf(defValue));
            return v;
        }

        @Override public Set<String> getStringSet(String key, Set<String> defValues) {
            Set<String> v = d.getStringSet(key, defValues);
            addEntry("getStringSet", fileName, key, String.valueOf(v), String.valueOf(defValues));
            return v;
        }

        @Override public int getInt(String key, int defValue) {
            int v = d.getInt(key, defValue);
            addEntry("getInt", fileName, key, String.valueOf(v), String.valueOf(defValue));
            return v;
        }

        @Override public long getLong(String key, long defValue) {
            long v = d.getLong(key, defValue);
            addEntry("getLong", fileName, key, String.valueOf(v), String.valueOf(defValue));
            return v;
        }

        @Override public float getFloat(String key, float defValue) {
            float v = d.getFloat(key, defValue);
            addEntry("getFloat", fileName, key, String.valueOf(v), String.valueOf(defValue));
            return v;
        }

        @Override public boolean getBoolean(String key, boolean defValue) {
            boolean v = d.getBoolean(key, defValue);
            addEntry("getBoolean", fileName, key, String.valueOf(v), String.valueOf(defValue));
            return v;
        }

        @Override public boolean contains(String key) {
            boolean v = d.contains(key);
            addEntry("contains", fileName, key, String.valueOf(v), "-");
            return v;
        }

        @Override public Editor edit() { return new LoggedEditor(fileName, d.edit()); }

        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            d.registerOnSharedPreferenceChangeListener(listener);
        }

        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            d.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    private static final class LoggedEditor implements SharedPreferences.Editor {
        private final String fileName;
        private final SharedPreferences.Editor e;

        LoggedEditor(String fileName, SharedPreferences.Editor editor) {
            this.fileName = fileName;
            this.e = editor;
        }

        @Override public SharedPreferences.Editor putString(String key, String value) {
            addEntry("putString", fileName, key, String.valueOf(value), "-");
            return e.putString(key, value);
        }

        @Override public SharedPreferences.Editor putStringSet(String key, Set<String> values) {
            addEntry("putStringSet", fileName, key, String.valueOf(values), "-");
            return e.putStringSet(key, values);
        }

        @Override public SharedPreferences.Editor putInt(String key, int value) {
            addEntry("putInt", fileName, key, String.valueOf(value), "-");
            return e.putInt(key, value);
        }

        @Override public SharedPreferences.Editor putLong(String key, long value) {
            addEntry("putLong", fileName, key, String.valueOf(value), "-");
            return e.putLong(key, value);
        }

        @Override public SharedPreferences.Editor putFloat(String key, float value) {
            addEntry("putFloat", fileName, key, String.valueOf(value), "-");
            return e.putFloat(key, value);
        }

        @Override public SharedPreferences.Editor putBoolean(String key, boolean value) {
            addEntry("putBoolean", fileName, key, String.valueOf(value), "-");
            return e.putBoolean(key, value);
        }

        @Override public SharedPreferences.Editor remove(String key) {
            addEntry("remove", fileName, key, "-", "-");
            return e.remove(key);
        }

        @Override public SharedPreferences.Editor clear() {
            addEntry("clear", fileName, "*", "-", "-");
            return e.clear();
        }

        @Override public boolean commit() { return e.commit(); }

        @Override public void apply() { e.apply(); }
    }
}
