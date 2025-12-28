package com.applisto.appcloner;

import android.text.TextUtils;

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

public final class HeaderMonitor {

    private static final AtomicLong INDEX = new AtomicLong(0);
    private static final TreeMap<Long, Map<String, Object>> MAP = new TreeMap<>();

    private static int maxEntries = 2000;
    private static int valueTruncateLength = 512;

    // Optional filters (substring match)
    private static final Set<String> positiveNameFilter = new HashSet<>();
    private static final Set<String> negativeNameFilter = new HashSet<>();
    private static final Set<String> positiveValueFilter = new HashSet<>();
    private static final Set<String> negativeValueFilter = new HashSet<>();

    private HeaderMonitor() {}

    public static void install() {
        // Just hook installation, configuration happens separately if needed or defaults are used
        new HeaderHook().install(null);
    }

    public static void configure(
            int maxEntries,
            int valueTruncateLength,
            Collection<String> positiveName,
            Collection<String> negativeName,
            Collection<String> positiveValue,
            Collection<String> negativeValue
    ) {
        HeaderMonitor.maxEntries = Math.max(1, maxEntries);
        HeaderMonitor.valueTruncateLength = Math.max(0, valueTruncateLength);

        positiveNameFilter.clear(); negativeNameFilter.clear();
        positiveValueFilter.clear(); negativeValueFilter.clear();

        if (positiveName != null) positiveNameFilter.addAll(positiveName);
        if (negativeName != null) negativeNameFilter.addAll(negativeName);
        if (positiveValue != null) positiveValueFilter.addAll(positiveValue);
        if (negativeValue != null) negativeValueFilter.addAll(negativeValue);
    }

    public static void clear() {
        synchronized (MAP) {
            MAP.clear();
            INDEX.set(0);
        }
    }

    public static void addEntry(String name, String value) {
        if (!allow(name, positiveNameFilter, negativeNameFilter)) return;
        if (!allow(value, positiveValueFilter, negativeValueFilter)) return;

        String v = truncate(value, valueTruncateLength);
        long ts = System.currentTimeMillis();

        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", ts);
        entry.put("name", name);
        entry.put("value", v);

        synchronized (MAP) {
            long idx = INDEX.incrementAndGet();
            MAP.put(idx, entry);
            while (MAP.size() > maxEntries) MAP.pollFirstEntry();
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

        w.write("Index,Timestamp,Name,Value\n");
        for (Map.Entry<Long, Map<String, Object>> e : snapshot) {
            long idx = e.getKey();
            Map<String, Object> m = e.getValue();
            w.write(Long.toString(idx)); w.write(',');
            w.write(csvEscape(String.valueOf(m.get("timestamp")))); w.write(',');
            w.write(csvEscape(String.valueOf(m.get("name")))); w.write(',');
            w.write(csvEscape(String.valueOf(m.get("value")))); w.write('\n');
        }
        w.flush();
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
}
