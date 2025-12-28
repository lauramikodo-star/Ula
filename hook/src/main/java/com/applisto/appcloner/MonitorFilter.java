package com.applisto.appcloner;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MonitorFilter {
    private MonitorFilter() {}

    /** Parses list entries like: "+google", "-ads", "example.com" (defaults to positive). */
    public static void parseFilter(List<String> items, Set<String> positive, Set<String> negative) {
        positive.clear();
        negative.clear();
        if (items == null) return;

        for (String raw : items) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;

            boolean neg = false;
            if (s.startsWith("!") || s.startsWith("-")) {
                neg = true;
                s = s.substring(1).trim();
            } else if (s.startsWith("+")) {
                s = s.substring(1).trim();
            }

            if (s.isEmpty()) continue;
            s = s.toLowerCase(Locale.US);

            if (neg) negative.add(s);
            else positive.add(s);
        }
    }

    /**
     * If positive set is empty => allow unless it matches negative.
     * If positive set not empty => allow only if it matches a positive AND doesn't match negative.
     * Match rule = case-insensitive "contains".
     */
    public static boolean allowAdding(String value, Set<String> positive, Set<String> negative) {
        if (value == null) return false;
        String v = value.toLowerCase(Locale.US);

        if (negative != null) {
            for (String n : negative) {
                if (n != null && !n.isEmpty() && v.contains(n)) return false;
            }
        }

        if (positive == null || positive.isEmpty()) return true;

        for (String p : positive) {
            if (p != null && !p.isEmpty() && v.contains(p)) return true;
        }
        return false;
    }
}
