package com.applisto.appcloner;

import android.content.Context;
import android.text.TextUtils;

import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class HostMonitor {
    private static final int NOTIFICATION_ID = 986711463;

    private static final AtomicLong sIndex = new AtomicLong(0);
    private static final Set<String> sPositiveHostFilter = new HashSet<>();
    private static final Set<String> sNegativeHostFilter = new HashSet<>();

    private static volatile TreeMap<Long, Map<String, Object>> sMap;
    private static volatile int sMaxEntries = 2000;
    private static volatile int sCountDown = -1; // jar: -1 default, set to 10 if not monitoringSuite
    private static volatile String sLastHost;

    // Basic reserved hostnames (jar also skips RESERVED_HOST_NAMES and pool.ntp.org)
    private static final Set<String> RESERVED = new HashSet<>();
    static {
        RESERVED.add("localhost");
        RESERVED.add("127.0.0.1");
        RESERVED.add("::1");
        RESERVED.add("0.0.0.0");
    }

    private HostMonitor() {}

    public static void install(Context context, List<String> hostFilter, int maxEntries, boolean monitoringSuite) {
        sMaxEntries = maxEntries;
        if (monitoringSuite) {
            MonitorFilter.parseFilter(hostFilter, sPositiveHostFilter, sNegativeHostFilter);
        } else {
            sCountDown = 10; // jar behavior
        }

        sMap = new TreeMap<>();

        // Hook DNS resolution
        new InetAddressGetByNameHook() {
            @Override protected InetAddress onGetByName(AtomicReference<String> host) {
                String h = host.get();
                if (!TextUtils.isEmpty(h)) addEntry(System.currentTimeMillis(), h, null);
                return null; // monitor-only (jar returns null)
            }

            @Override protected InetAddress[] onGetAllByName(AtomicReference<String> host) {
                String h = host.get();
                if (!TextUtils.isEmpty(h)) addEntry(System.currentTimeMillis(), h, null);
                return null; // monitor-only
            }
        }.install(context);

        // Hook real socket connects
        new SocketConnectHook() {
            @Override protected void onSocketConnect(
                    AtomicReference<InetAddress> address,
                    AtomicReference<Integer> port,
                    AtomicReference<Integer> timeout
            ) {
                InetAddress a = address.get();
                if (a == null) return;
                String host = a.getHostName();
                Integer p = port.get();
                addEntry(System.currentTimeMillis(), host, p);
            }
        }.install(context);

        // Optional: show UI notification (matches jar route style)
        HostMonitorNotifications.install(context, NOTIFICATION_ID, "host-monitor?packageName=" + context.getPackageName(), "Host Monitor");
    }

    public static java.util.TreeMap<Long, java.util.Map<String, Object>> snapshot() {
        java.util.TreeMap<Long, java.util.Map<String, Object>> map = sMap;
        if (map == null) return new java.util.TreeMap<>();
        synchronized (map) {
            return new java.util.TreeMap<>(map);
        }
    }

    public static void deleteEntries() {
        TreeMap<Long, Map<String, Object>> map = sMap;
        if (map == null) return;
        synchronized (map) {
            map.clear();
            sIndex.set(0);
        }
    }

    private static void addEntry(long timestamp, String host, Integer port) {
        if (host == null) return;

        // Skip reserved and NTP pool like jar
        String h = host.trim();
        if (h.isEmpty()) return;
        if (RESERVED.contains(h)) return;
        if (h.endsWith("pool.ntp.org")) return;

        // countdown behavior like jar
        int cd = sCountDown;
        if (cd != -1) {
            if (cd == 0) return;
            sCountDown = cd - 1;
        }

        // filters like jar
        if (!MonitorFilter.allowAdding(h, sPositiveHostFilter, sNegativeHostFilter)) return;

        // dedupe consecutive
        if (h.equals(sLastHost)) return;

        TreeMap<Long, Map<String, Object>> map = sMap;
        if (map == null) return;

        long idx = sIndex.incrementAndGet();
        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", timestamp);
        entry.put("host", h);
        if (port != null) entry.put("port", port);

        synchronized (map) {
            map.put(idx, entry);
            while (map.size() > sMaxEntries) {
                map.pollFirstEntry();
            }
        }

        sLastHost = h;
    }

    public static void writeMonitorCsv(Writer w, long afterIndex) throws IOException {
        TreeMap<Long, Map<String, Object>> map = sMap;
        if (map == null) return;

        final java.util.List<Map.Entry<Long, Map<String, Object>>> rows;
        synchronized (map) {
            rows = new java.util.ArrayList<>(map.tailMap(afterIndex, false).entrySet());
        }

        w.write("Index,Timestamp,Host,Port\n");
        for (Map.Entry<Long, Map<String, Object>> e : rows) {
            long idx = e.getKey();
            Map<String, Object> v = e.getValue();

            String ts = String.valueOf(v.get("timestamp"));
            String host = String.valueOf(v.get("host"));
            String port = Objects.toString(v.get("port"), "");

            w.write(Long.toString(idx)); w.write(',');
            w.write(csvEscape(ts)); w.write(',');
            w.write(csvEscape(host)); w.write(',');
            w.write(csvEscape(port)); w.write('\n');
        }
        w.flush();
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean need = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!need) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
