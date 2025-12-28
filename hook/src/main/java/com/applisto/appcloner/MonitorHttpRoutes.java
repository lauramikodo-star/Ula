package com.applisto.appcloner;

import android.text.TextUtils;

import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public final class MonitorHttpRoutes {
    private MonitorHttpRoutes() {}

    /** Call this from your server's handleRequest(...) */
    public static SimpleHttpServer.Response tryHandle(SimpleHttpServer.Request req) {
        String raw = req.path; // includes query
        String path = stripQuery(raw);

        // Host Monitor
        if ("/host-monitor".equals(path) || "host-monitor".equals(path)) {
            if ("GET".equalsIgnoreCase(req.method)) return renderHostMonitorPage(req.path);
            return new SimpleHttpServer.Response(405, "text/plain", "method not allowed");
        }
        if ("/host-monitor.csv".equals(path) || "host-monitor.csv".equals(path)) {
            if ("GET".equalsIgnoreCase(req.method)) return exportHostCsv(req.path);
            return new SimpleHttpServer.Response(405, "text/plain", "method not allowed");
        }
        if ("/host-monitor/clear".equals(path) || "host-monitor/clear".equals(path)) {
            if ("POST".equalsIgnoreCase(req.method)) {
                HostMonitor.deleteEntries();
                return new SimpleHttpServer.Response(200, "application/json", "{\"ok\":true}");
            }
            return new SimpleHttpServer.Response(405, "text/plain", "method not allowed");
        }

        // Header Monitor
        if ("/header-monitor".equals(path) || "header-monitor".equals(path)) {
            if ("GET".equalsIgnoreCase(req.method)) return renderHeaderMonitorPage(req.path);
            return new SimpleHttpServer.Response(405, "text/plain", "method not allowed");
        }
        if ("/header-monitor.csv".equals(path) || "header-monitor.csv".equals(path)) {
            if ("GET".equalsIgnoreCase(req.method)) return exportHeaderCsv(req.path);
            return new SimpleHttpServer.Response(405, "text/plain", "method not allowed");
        }
        if ("/header-monitor/clear".equals(path) || "header-monitor/clear".equals(path)) {
            if ("POST".equalsIgnoreCase(req.method)) {
                HeaderMonitor.clear();
                return new SimpleHttpServer.Response(200, "application/json", "{\"ok\":true}");
            }
            return new SimpleHttpServer.Response(405, "text/plain", "method not allowed");
        }

        // Preferences Monitor
        if ("/preferences-monitor".equals(path) || "preferences-monitor".equals(path)) {
            if ("GET".equalsIgnoreCase(req.method)) return renderPreferencesMonitorPage(req.path);
            return new SimpleHttpServer.Response(405, "text/plain", "method not allowed");
        }
        if ("/preferences-monitor.csv".equals(path) || "preferences-monitor.csv".equals(path)) {
            if ("GET".equalsIgnoreCase(req.method)) return exportPreferencesCsv(req.path);
            return new SimpleHttpServer.Response(405, "text/plain", "method not allowed");
        }
        if ("/preferences-monitor/clear".equals(path) || "preferences-monitor/clear".equals(path)) {
            if ("POST".equalsIgnoreCase(req.method)) {
                PreferencesMonitor.clear();
                return new SimpleHttpServer.Response(200, "application/json", "{\"ok\":true}");
            }
            return new SimpleHttpServer.Response(405, "text/plain", "method not allowed");
        }

        return null; // not handled
    }

    // ------------------ Host Monitor ------------------

    private static SimpleHttpServer.Response renderHostMonitorPage(String fullPathWithQuery) {
        Map<String, String> q = parseQuery(fullPathWithQuery);
        String pkg = q.get("packageName");
        if (pkg == null) pkg = "";

        TreeMap<Long, Map<String, Object>> snap = HostMonitor.snapshot();
        long lastIndex = snap.isEmpty() ? 0L : snap.lastKey();

        StringBuilder rows = new StringBuilder();
        for (Map.Entry<Long, Map<String, Object>> e : snap.descendingMap().entrySet()) {
            long idx = e.getKey();
            Map<String, Object> v = e.getValue();
            rows.append("<tr>")
                .append("<td>").append(idx).append("</td>")
                .append("<td>").append(escapeHtml(String.valueOf(v.get("timestamp")))).append("</td>")
                .append("<td>").append(escapeHtml(String.valueOf(v.get("host")))).append("</td>")
                .append("<td>").append(v.get("port") == null ? "" : escapeHtml(String.valueOf(v.get("port")))).append("</td>")
                .append("</tr>\n");
        }

        String html = renderPage("Host Monitor", pkg, snap.size(), lastIndex,
                "<th>Index</th><th>Timestamp</th><th>Host</th><th>Port</th>", rows.toString(),
                "host-monitor");
        return new SimpleHttpServer.Response(200, "text/html", html);
    }

    private static SimpleHttpServer.Response exportHostCsv(String fullPathWithQuery) {
        Map<String, String> q = parseQuery(fullPathWithQuery);
        long after = parseLong(q.get("after"), 0L);
        try {
            StringWriter sw = new StringWriter();
            HostMonitor.writeMonitorCsv(sw, after);
            return new SimpleHttpServer.Response(200, "text/csv; charset=utf-8", sw.toString());
        } catch (Throwable t) {
            return new SimpleHttpServer.Response(500, "text/plain", "csv error: " + t);
        }
    }

    // ------------------ Header Monitor ------------------

    private static SimpleHttpServer.Response renderHeaderMonitorPage(String fullPathWithQuery) {
        Map<String, String> q = parseQuery(fullPathWithQuery);
        String pkg = q.get("packageName");
        if (pkg == null) pkg = "";

        TreeMap<Long, Map<String, Object>> snap = HeaderMonitor.snapshot();
        long lastIndex = snap.isEmpty() ? 0L : snap.lastKey();

        StringBuilder rows = new StringBuilder();
        for (Map.Entry<Long, Map<String, Object>> e : snap.descendingMap().entrySet()) {
            long idx = e.getKey();
            Map<String, Object> v = e.getValue();
            rows.append("<tr>")
                    .append("<td>").append(idx).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(v.get("timestamp")))).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(v.get("name")))).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(v.get("value")))).append("</td>")
                    .append("</tr>\n");
        }

        String html = renderPage("Header Monitor", pkg, snap.size(), lastIndex,
                "<th>Index</th><th>Timestamp</th><th>Name</th><th>Value</th>", rows.toString(),
                "header-monitor");
        return new SimpleHttpServer.Response(200, "text/html", html);
    }

    private static SimpleHttpServer.Response exportHeaderCsv(String fullPathWithQuery) {
        Map<String, String> q = parseQuery(fullPathWithQuery);
        long after = parseLong(q.get("after"), 0L);
        try {
            StringWriter sw = new StringWriter();
            HeaderMonitor.writeMonitorCsv(sw, after);
            return new SimpleHttpServer.Response(200, "text/csv; charset=utf-8", sw.toString());
        } catch (Throwable t) {
            return new SimpleHttpServer.Response(500, "text/plain", "csv error: " + t);
        }
    }

    // ------------------ Preferences Monitor ------------------

    private static SimpleHttpServer.Response renderPreferencesMonitorPage(String fullPathWithQuery) {
        Map<String, String> q = parseQuery(fullPathWithQuery);
        String pkg = q.get("packageName");
        if (pkg == null) pkg = "";

        TreeMap<Long, Map<String, Object>> snap = PreferencesMonitor.snapshot();
        long lastIndex = snap.isEmpty() ? 0L : snap.lastKey();

        StringBuilder rows = new StringBuilder();
        for (Map.Entry<Long, Map<String, Object>> e : snap.descendingMap().entrySet()) {
            long idx = e.getKey();
            Map<String, Object> v = e.getValue();
            rows.append("<tr>")
                    .append("<td>").append(idx).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(v.get("timestamp")))).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(v.get("method")))).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(v.get("fileName")))).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(v.get("key")))).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(v.get("value")))).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(v.get("defaultValue")))).append("</td>")
                    .append("</tr>\n");
        }

        String html = renderPage("Preferences Monitor", pkg, snap.size(), lastIndex,
                "<th>Index</th><th>Timestamp</th><th>Method</th><th>FileName</th><th>Key</th><th>Value</th><th>DefaultValue</th>",
                rows.toString(),
                "preferences-monitor");
        return new SimpleHttpServer.Response(200, "text/html", html);
    }

    private static SimpleHttpServer.Response exportPreferencesCsv(String fullPathWithQuery) {
        Map<String, String> q = parseQuery(fullPathWithQuery);
        long after = parseLong(q.get("after"), 0L);
        try {
            StringWriter sw = new StringWriter();
            PreferencesMonitor.writeMonitorCsv(sw, after);
            return new SimpleHttpServer.Response(200, "text/csv; charset=utf-8", sw.toString());
        } catch (Throwable t) {
            return new SimpleHttpServer.Response(500, "text/plain", "csv error: " + t);
        }
    }

    // ------------------ Common Helpers ------------------

    private static String renderPage(String title, String pkg, int entries, long lastIndex, String thead, String rows, String basePath) {
        return "<!doctype html><meta charset=utf-8>" +
                "<title>" + title + "</title>" +
                "<style>" +
                "body{font-family:system-ui,monospace;margin:0;padding:12px;background:#0b0f14;color:#e6edf3}" +
                "a,button{color:#e6edf3} .bar{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:10px}" +
                "button{background:#1f2a37;border:1px solid #334155;border-radius:10px;padding:8px 12px;cursor:pointer}" +
                "button:hover{background:#253244}" +
                "table{width:100%;border-collapse:collapse;border:1px solid #334155;border-radius:12px;overflow:hidden}" +
                "th,td{border-bottom:1px solid #334155;padding:8px;font-size:13px}" +
                "th{background:#111827;text-align:left}" +
                ".muted{opacity:.75}" +
                "</style>" +

                "<div class=bar>" +
                "<div><b>" + title + "</b> <span class=muted>pkg:</span> " + escapeHtml(pkg) + "</div>" +
                "<div class=muted>entries: " + entries + " | lastIndex: " + lastIndex + "</div>" +
                "<button onclick='location.reload()'>Refresh</button>" +
                "<button onclick='downloadCsv()'>Download CSV</button>" +
                "<button onclick='clearEntries()'>Clear</button>" +
                "</div>" +

                "<table><thead><tr>" + thead + "</tr></thead><tbody>" +
                rows +
                "</tbody></table>" +

                "<script>" +
                "function downloadCsv(){ " +
                "  const u=new URL(location.href); " +
                "  const a=u.searchParams.get('after')||'0'; " +
                "  const csv='/" + basePath + ".csv?after='+encodeURIComponent(a);" +
                "  location.href=csv;" +
                "}" +
                "async function clearEntries(){ " +
                "  if(!confirm('Clear all entries?')) return;" +
                "  const r=await fetch('/" + basePath + "/clear',{method:'POST'});" +
                "  if(r.ok) location.reload(); else alert('Failed');" +
                "}" +
                "</script>";
    }

    private static String stripQuery(String p) {
        if (p == null) return "";
        int i = p.indexOf('?');
        return i >= 0 ? p.substring(0, i) : p;
    }

    private static Map<String, String> parseQuery(String fullPath) {
        java.util.HashMap<String, String> out = new java.util.HashMap<>();
        if (fullPath == null) return out;

        int q = fullPath.indexOf('?');
        if (q < 0 || q == fullPath.length() - 1) return out;

        String qs = fullPath.substring(q + 1);
        for (String part : qs.split("&")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String k = eq >= 0 ? part.substring(0, eq) : part;
            String v = eq >= 0 ? part.substring(eq + 1) : "";
            k = urlDecode(k);
            v = urlDecode(v);
            if (!TextUtils.isEmpty(k)) out.put(k, v);
        }
        return out;
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (Throwable t) {
            return s;
        }
    }

    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s); } catch (Throwable t) { return def; }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;")
                .replace("<","&lt;")
                .replace(">","&gt;")
                .replace("\"","&quot;")
                .replace("'","&#39;");
    }
}
