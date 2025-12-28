package com.applisto.appcloner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * WebView URL/data filter with Pine hooks.
 * - URL rules: regex match → block or replace (optional URL-encode replacement).
 * - Data rules: regex match → block or replace; optional ignore case; replace first/all.
 * - Response rewriting: for GET text/*, html, json, js, css (configurable).
 *
 * Config (assets/cloner.json):
 * {
 *   "webview_filter": {
 *     "enabled": true,
 *     "debug": false,
 *     "rewrite_responses": true,
 *     "max_rewrite_size_kb": 512,
 *     "rewrite_content_types": ["text/html", "application/json", "application/javascript", "text/javascript", "text/css", "text/plain"],
 *     "rules": [
 *       { "url_regex": "https?://(www\\.)?tracker\\.example\\.com/.*", "url_block_if_matching": true },
 *       { "url_regex": "https?://api\\.example\\.com/v1/(.+)", "url_replacement": "https://api.example.com/v2/$1", "url_encode_replacement": false },
 *       { "data_regex": "(?i)<script[^>]*>.*?bad\$ \ $ .*?</script>", "data_block_if_matching": true },
 *       { "data_regex": "\"featureFlag\"\\s*:\\s*false", "data_replacement": "\"featureFlag\": true", "data_replace_all": true }
 *     ]
 *   }
 * }
 */
public final class WebViewUrlDataFilterHook {
    private static final String TAG = "WVUrlDataFilterHook";

    // Config
    private static volatile boolean ENABLED = false;
    private static volatile boolean DEBUG = false;
    private static volatile boolean REWRITE_RESPONSES = false;
    private static volatile int MAX_REWRITE_SIZE = 512 * 1024; // bytes
    private static volatile Set<String> REWRITE_CT = new HashSet<>(Arrays.asList(
            "text/html", "application/json", "application/javascript",
            "text/javascript", "text/css", "text/plain"
    ));
    private static volatile List<Rule> RULES = Collections.emptyList();

    private static volatile boolean sInstalled = false;

    public void init(Context ctx) {
        if (sInstalled) return;
        synchronized (WebViewUrlDataFilterHook.class) {
            if (sInstalled) return;
            try {
                loadConfig(ctx.getApplicationContext());
            } catch (Throwable t) {
                Log.e(TAG, "Failed to load config; continuing with defaults", t);
            }
            if (!ENABLED) {
                if (DEBUG) Log.i(TAG, "Filter disabled by config; not installing hooks");
                sInstalled = true; // mark installed to avoid retry churn
                return;
            }
            installHooks();
            installServiceWorkerClientSafely();
            sInstalled = true;
            if (DEBUG) Log.i(TAG, "Installed hooks. rules=" + RULES.size());
        }
    }

    // -------------------- Hook installation --------------------

    private static void installHooks() {
        safeHook(getMethod(WebView.class, "loadUrl", String.class),
                new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        String url = (String) frame.args[0];
                        Action a = applyUrlRules(url);
                        if (a.block) {
                            if (DEBUG) Log.d(TAG, "Blocked loadUrl: " + url);
                            frame.args[0] = "about:blank";
                            return;
                        }
                        if (a.rewritten != null) {
                            if (DEBUG) Log.d(TAG, "Rewrite loadUrl: " + url + " -> " + a.rewritten);
                            frame.args[0] = a.rewritten;
                        }
                    }
                });

        safeHook(getMethod(WebView.class, "loadUrl", String.class, Map.class),
                new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        String url = (String) frame.args[0];
                        Action a = applyUrlRules(url);
                        if (a.block) {
                            if (DEBUG) Log.d(TAG, "Blocked loadUrl(headers): " + url);
                            frame.args[0] = "about:blank";
                            return;
                        }
                        if (a.rewritten != null) {
                            if (DEBUG) Log.d(TAG, "Rewrite loadUrl(headers): " + url + " -> " + a.rewritten);
                            frame.args[0] = a.rewritten;
                        }
                    }
                });

        safeHook(getMethod(WebView.class, "loadData", String.class, String.class, String.class),
                new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        String data = (String) frame.args[0];
                        Action a = applyDataRules(data);
                        if (a.block) {
                            if (DEBUG) Log.d(TAG, "Blocked loadData");
                            frame.args[0] = "";
                            return;
                        }
                        if (a.rewritten != null) frame.args[0] = a.rewritten;
                    }
                });

        safeHook(getMethod(WebView.class, "loadDataWithBaseURL",
                String.class, String.class, String.class, String.class, String.class),
                new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        String baseUrl = (String) frame.args[0];
                        String data = (String) frame.args[1];

                        // Base URL rules
                        Action au = applyUrlRules(baseUrl);
                        if (au.block) {
                            if (DEBUG) Log.d(TAG, "Blocked loadDataWithBaseURL baseUrl=" + baseUrl);
                            frame.args[0] = "about:blank";
                        } else if (au.rewritten != null) {
                            if (DEBUG) Log.d(TAG, "Rewrite baseUrl: " + baseUrl + " -> " + au.rewritten);
                            frame.args[0] = au.rewritten;
                        }

                        // Data rules
                        Action ad = applyDataRules(data);
                        if (ad.block) {
                            if (DEBUG) Log.d(TAG, "Blocked loadDataWithBaseURL data");
                            frame.args[1] = "";
                            return;
                        }
                        if (ad.rewritten != null) frame.args[1] = ad.rewritten;
                    }
                });

        safeHook(getMethod(WebView.class, "evaluateJavascript", String.class, ValueCallback.class),
                new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        String js = (String) frame.args[0];
                        Action a = applyDataRules(js);
                        if (a.block) {
                            if (DEBUG) Log.d(TAG, "Blocked evaluateJavascript");
                            frame.args[0] = "";
                            return;
                        }
                        if (a.rewritten != null) frame.args[0] = a.rewritten;
                    }
                });

        safeHook(getMethod(WebView.class, "postUrl", String.class, byte[].class),
                new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        String url = (String) frame.args[0];
                        byte[] post = (byte[]) frame.args[1];

                        Action au = applyUrlRules(url);
                        if (au.block) {
                            if (DEBUG) Log.d(TAG, "Blocked postUrl: " + url);
                            frame.args[0] = "about:blank";
                            frame.args[1] = new byte[0];
                            return;
                        }
                        if (au.rewritten != null) frame.args[0] = au.rewritten;

                        // Try UTF-8 decode; if not valid UTF-8, we leave bytes unchanged.
                        if (post != null && post.length > 0) {
                            try {
                                String body = new String(post, Charset.forName("UTF-8"));
                                Action ad = applyDataRules(body);
                                if (ad.block) {
                                    if (DEBUG) Log.d(TAG, "Blocked postUrl body");
                                    frame.args[1] = new byte[0];
                                    return;
                                }
                                if (ad.rewritten != null) {
                                    frame.args[1] = ad.rewritten.getBytes(Charset.forName("UTF-8"));
                                }
                            } catch (Throwable ignore) {
                                // binary body; skip rewriting
                            }
                        }
                    }
                });

        // Wrap any WebViewClient set by the app
        safeHook(getMethod(WebView.class, "setWebViewClient", WebViewClient.class),
                new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        WebView wv = (WebView) frame.thisObject;
                        WebViewClient orig = (WebViewClient) frame.args[0];
                        frame.args[0] = wrapClient(wv.getContext(), orig);
                    }
                });
    }

    private static void installServiceWorkerClientSafely() {
        if (Build.VERSION.SDK_INT < 24) return;
        try {
            ServiceWorkerController controller = ServiceWorkerController.getInstance();
            ServiceWorkerClient wrap = new ServiceWorkerClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    try {
                        if (!REWRITE_RESPONSES) return null;
                        if (request == null || request.getMethod() == null) return null;
                        if (!"GET".equalsIgnoreCase(request.getMethod())) return null;
                        String url = String.valueOf(request.getUrl());
                        // URL rules can block here too
                        Action au = applyUrlRules(url);
                        if (au.block) {
                            if (DEBUG) Log.d(TAG, "[SW] Blocked: " + url);
                            return blockedResponse();
                        }
                        String effectiveUrl = au.rewritten != null ? au.rewritten : url;
                        return maybeRewriteResponse(effectiveUrl, request.getRequestHeaders());
                    } catch (Throwable t) {
                        if (DEBUG) Log.w(TAG, "[SW] intercept error", t);
                        return null;
                    }
                }
            };
            controller.setServiceWorkerClient(wrap);

            // Hook setServiceWorkerClient as well, to wrap app-provided clients.
            Method m = getMethod(ServiceWorkerController.class, "setServiceWorkerClient", ServiceWorkerClient.class);
            if (m != null) {
                Hooking.pineHook(m, new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        ServiceWorkerClient orig = (ServiceWorkerClient) frame.args[0];
                        frame.args[0] = new ServiceWorkerClient() {
                            @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                                try {
                                    // Delegate to ours first
                                    WebResourceResponse res = wrap.shouldInterceptRequest(request);
                                    if (res != null) return res;
                                    // then original
                                    return orig != null ? orig.shouldInterceptRequest(request) : null;
                                } catch (Throwable t) {
                                    if (DEBUG) Log.w(TAG, "SW wrapped client error", t);
                                    return orig != null ? orig.shouldInterceptRequest(request) : null;
                                }
                            }
                        };
                    }
                });
            }
        } catch (Throwable t) {
            if (DEBUG) Log.w(TAG, "ServiceWorkerClient install failed", t);
        }
    }

    // -------------------- Client wrapper --------------------

    private static WebViewClient wrapClient(Context ctx, WebViewClient orig) {
        return new FilteringClient(orig);
    }

    private static final class FilteringClient extends WebViewClient {
        private final WebViewClient orig;

        FilteringClient(WebViewClient orig) {
            this.orig = orig;
        }

        @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                Action a = applyUrlRules(url);
                if (a.block) {
                    if (DEBUG) Log.d(TAG, "Blocked nav: " + url);
                    return true; // block
                }
                if (a.rewritten != null) {
                    if (DEBUG) Log.d(TAG, "Rewrite nav: " + url + " -> " + a.rewritten);
                    view.loadUrl(a.rewritten);
                    return true;
                }
            } catch (Throwable t) {
                if (DEBUG) Log.w(TAG, "shouldOverrideUrlLoading(String) error", t);
            }
            return orig != null && orig.shouldOverrideUrlLoading(view, url);
        }

        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (request == null) return orig != null && orig.shouldOverrideUrlLoading(view, request);
            String url = String.valueOf(request.getUrl());
            try {
                Action a = applyUrlRules(url);
                if (a.block) {
                    if (DEBUG) Log.d(TAG, "Blocked nav(req): " + url);
                    return true;
                }
                if (a.rewritten != null) {
                    if (DEBUG) Log.d(TAG, "Rewrite nav(req): " + url + " -> " + a.rewritten);
                    view.loadUrl(a.rewritten);
                    return true;
                }
            } catch (Throwable t) {
                if (DEBUG) Log.w(TAG, "shouldOverrideUrlLoading(req) error", t);
            }
            return orig != null && orig.shouldOverrideUrlLoading(view, request);
        }

        @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            try {
                if (!REWRITE_RESPONSES) return orig != null ? orig.shouldInterceptRequest(view, url) : null;
                Action au = applyUrlRules(url);
                if (au.block) {
                    if (DEBUG) Log.d(TAG, "Blocked resource: " + url);
                    return blockedResponse();
                }
                String effectiveUrl = au.rewritten != null ? au.rewritten : url;
                WebResourceResponse ours = maybeRewriteResponse(effectiveUrl, Collections.emptyMap());
                if (ours != null) return ours;
            } catch (Throwable t) {
                if (DEBUG) Log.w(TAG, "intercept(String) error", t);
            }
            return orig != null ? orig.shouldInterceptRequest(view, url) : null;
        }

        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            try {
                if (!REWRITE_RESPONSES) return orig != null ? orig.shouldInterceptRequest(view, request) : null;
                if (request == null || request.getMethod() == null) return orig != null ? orig.shouldInterceptRequest(view, request) : null;
                if (!"GET".equalsIgnoreCase(request.getMethod())) return orig != null ? orig.shouldInterceptRequest(view, request) : null;

                String url = String.valueOf(request.getUrl());
                Action au = applyUrlRules(url);
                if (au.block) {
                    if (DEBUG) Log.d(TAG, "Blocked resource(req): " + url);
                    return blockedResponse();
                }
                String effectiveUrl = au.rewritten != null ? au.rewritten : url;
                WebResourceResponse ours = maybeRewriteResponse(effectiveUrl, request.getRequestHeaders());
                if (ours != null) return ours;
            } catch (Throwable t) {
                if (DEBUG) Log.w(TAG, "intercept(req) error", t);
            }
            return orig != null ? orig.shouldInterceptRequest(view, request) : null;
        }
    }

    // -------------------- Response rewriting --------------------

    private static WebResourceResponse maybeRewriteResponse(String url, Map<String, String> headers) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null) continue;
                    // Avoid overriding Host/Connection
                    String k = e.getKey();
                    if ("host".equalsIgnoreCase(k) || "connection".equalsIgnoreCase(k)) continue;
                    c.setRequestProperty(k, e.getValue());
                }
            }
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                // Let WebView follow redirects itself
                closeQuietly(c.getInputStream());
                c.disconnect();
                return null;
            }
            InputStream in = new BufferedInputStream(c.getInputStream());
            String ct = c.getContentType(); // may be null
            int contentLen = c.getContentLength();
            if (contentLen > 0 && contentLen > MAX_REWRITE_SIZE) {
                closeQuietly(in); c.disconnect(); return null;
            }
            String mime = parseMime(ct);
            String charset = parseCharset(ct);
            if (!shouldRewriteMime(mime)) {
                closeQuietly(in); c.disconnect(); return null;
            }
            byte[] body = readUpTo(in, MAX_REWRITE_SIZE + 1); // +1 to detect overflow
            closeQuietly(in);
            c.disconnect();
            if (body.length > MAX_REWRITE_SIZE) return null;

            String text = new String(body, charsetSafe(charset));
            Action a = applyDataRules(text);
            if (a.block) return blockedResponse();
            if (a.rewritten == null) return null;

            // Build new response with same MIME/charset
            byte[] out = a.rewritten.getBytes(charsetSafe(charset));
            WebResourceResponse resp = new WebResourceResponse(mime != null ? mime : "text/plain",
                    charset != null ? charset : "UTF-8",
                    new ByteArrayInputStream(out));
            return resp;
        } catch (Throwable t) {
            if (DEBUG) Log.w(TAG, "maybeRewriteResponse error for " + url, t);
            return null;
        }
    }

    private static WebResourceResponse blockedResponse() {
        String msg = "Blocked by WebView filter";
        return new WebResourceResponse("text/plain", "UTF-8",
                new ByteArrayInputStream(msg.getBytes(Charset.forName("UTF-8"))));
    }

    private static boolean shouldRewriteMime(String mime) {
        if (mime == null) return false;
        String norm = mime.toLowerCase(Locale.ROOT).trim();
        int sc = norm.indexOf(';');
        if (sc > 0) norm = norm.substring(0, sc).trim();
        return REWRITE_CT.contains(norm);
    }

    private static String parseMime(String ct) {
        if (ct == null) return null;
        int sc = ct.indexOf(';');
        return (sc > 0 ? ct.substring(0, sc) : ct).trim();
    }

    private static String parseCharset(String ct) {
        if (ct == null) return null;
        String[] parts = ct.split(";");
        for (String p : parts) {
            String s = p.trim();
            int eq = s.indexOf('=');
            if (eq > 0) {
                String k = s.substring(0, eq).trim();
                String v = s.substring(eq + 1).trim();
                if ("charset".equalsIgnoreCase(k)) return v.replace("\"", "");
            }
        }
        return null;
    }

    private static Charset charsetSafe(String cs) {
        try { return cs != null ? Charset.forName(cs) : Charset.forName("UTF-8"); }
        catch (Throwable ignore) { return Charset.forName("UTF-8"); }
    }

    private static byte[] readUpTo(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] buf = new byte[16 * 1024];
        int read;
        while ((read = in.read(buf)) != -1) {
            if (bos.size() + read > maxBytes) {
                int remain = maxBytes - bos.size();
                if (remain > 0) bos.write(buf, 0, remain);
                break;
            }
            bos.write(buf, 0, read);
        }
        return bos.toByteArray();
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (Throwable ignore) {}
    }

    // -------------------- Rules engine --------------------

    private static final class Rule {
        final Pattern urlPat;
        final boolean urlBlock;
        final String urlReplacement;
        final boolean urlEncodeReplacement;

        final Pattern dataPat;
        final boolean dataBlock;
        final String dataReplacement;
        final boolean dataReplaceAll;

        Rule(Pattern urlPat, boolean urlBlock, String urlReplacement, boolean urlEncodeReplacement,
             Pattern dataPat, boolean dataBlock, String dataReplacement, boolean dataReplaceAll) {
            this.urlPat = urlPat;
            this.urlBlock = urlBlock;
            this.urlReplacement = urlReplacement;
            this.urlEncodeReplacement = urlEncodeReplacement;
            this.dataPat = dataPat;
            this.dataBlock = dataBlock;
            this.dataReplacement = dataReplacement;
            this.dataReplaceAll = dataReplaceAll;
        }
    }

    private static final class Action {
        final boolean block;
        final String rewritten;
        Action(boolean b, String r) { this.block = b; this.rewritten = r; }
        static Action none() { return new Action(false, null); }
    }

    private static Action applyUrlRules(String url) {
        if (url == null) return Action.none();
        List<Rule> rules = RULES;
        String cur = url;
        boolean changed = false;
        for (Rule r : rules) {
            if (r.urlPat == null) continue;
            Matcher m = r.urlPat.matcher(cur);
            if (!m.find()) continue;
            if (r.urlBlock) return new Action(true, null);
            String repl = r.urlReplacement;
            if (r.urlEncodeReplacement && repl != null) repl = Uri.encode(repl);
            cur = m.replaceAll(repl == null ? "" : repl);
            changed = true;
        }
        return changed ? new Action(false, cur) : Action.none();
    }

    private static Action applyDataRules(String data) {
        if (data == null) return Action.none();
        List<Rule> rules = RULES;
        String cur = data;
        boolean changed = false;
        for (Rule r : rules) {
            if (r.dataPat == null) continue;
            Matcher m = r.dataPat.matcher(cur);
            if (!m.find()) continue;

            if (r.dataBlock) {
                return new Action(true, null);
            }

            String replacement = (r.dataReplacement != null) ? r.dataReplacement : "";
            // If replaceAll flag is true, replace every match; else only the first match.
            cur = r.dataReplaceAll ? m.replaceAll(replacement) : m.replaceFirst(replacement);
            changed = true;
        }
        return changed ? new Action(false, cur) : Action.none();
    }

    // -------------------- Config loading --------------------

    private static void loadConfig(Context ctx) throws Exception {
        String json = readAssetIfExists(ctx, "cloner.json");
        if (json == null) {
            // Fallback names if you prefer a different asset file.
            json = readAssetIfExists(ctx, "appcloner.json");
        }
        if (json == null) {
            ENABLED = false;
            DEBUG = false;
            REWRITE_RESPONSES = false;
            RULES = Collections.emptyList();
            if (DEBUG) Log.i(TAG, "No config asset found; filter disabled.");
            return;
        }

        JSONObject root = new JSONObject(json);
        JSONObject cfg = root.optJSONObject("webview_filter");
        if (cfg == null) {
            ENABLED = false;
            DEBUG = false;
            REWRITE_RESPONSES = false;
            RULES = Collections.emptyList();
            if (DEBUG) Log.i(TAG, "webview_filter section missing; filter disabled.");
            return;
        }

        ENABLED = cfg.optBoolean("enabled", false);
        DEBUG = cfg.optBoolean("debug", false);
        REWRITE_RESPONSES = cfg.optBoolean("rewrite_responses", false);
        int kb = cfg.optInt("max_rewrite_size_kb", 512);
        if (kb < 1) kb = 1;
        MAX_REWRITE_SIZE = kb * 1024;

        // Content types list
        Set<String> types = new HashSet<>();
        JSONArray ctArr = cfg.optJSONArray("rewrite_content_types");
        if (ctArr != null) {
            for (int i = 0; i < ctArr.length(); i++) {
                String t = ctArr.optString(i, null);
                if (t != null && !t.trim().isEmpty()) {
                    types.add(t.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (!types.isEmpty()) REWRITE_CT = types;

        // Rules
        JSONArray arr = cfg.optJSONArray("rules");
        if (arr == null || arr.length() == 0) {
            RULES = Collections.emptyList();
        } else {
            newRules:
            {
                try {
                    java.util.ArrayList<Rule> list = new java.util.ArrayList<>(arr.length());
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;

                        // URL part
                        Pattern urlPat = null;
                        String urlRegex = optStringNonEmpty(o, "url_regex");
                        if (urlRegex != null) {
                            try {
                                urlPat = Pattern.compile(urlRegex);
                            } catch (Throwable t) {
                                if (DEBUG) Log.w(TAG, "Bad url_regex at index " + i + ": " + urlRegex, t);
                            }
                        }
                        boolean urlBlock = o.optBoolean("url_block_if_matching", false);
                        String urlRepl = optStringAllowEmpty(o, "url_replacement"); // allow empty
                        boolean urlEncodeRepl = o.optBoolean("url_encode_replacement", false);

                        // Data part
                        Pattern dataPat = null;
                        String dataRegex = optStringNonEmpty(o, "data_regex");
                        boolean dataIgnoreCase = o.optBoolean("data_ignore_case", false)
                                || o.optBoolean("ignore_case", false); // alias
                        if (dataRegex != null) {
                            try {
                                int flags = Pattern.DOTALL | (dataIgnoreCase ? Pattern.CASE_INSENSITIVE : 0);
                                dataPat = Pattern.compile(dataRegex, flags);
                            } catch (Throwable t) {
                                if (DEBUG) Log.w(TAG, "Bad data_regex at index " + i + ": " + dataRegex, t);
                            }
                        }
                        boolean dataBlock = o.optBoolean("data_block_if_matching", false);
                        String dataRepl = optStringAllowEmpty(o, "data_replacement"); // allow empty
                        boolean dataReplaceAll = o.optBoolean("data_replace_all", false);

                        list.add(new Rule(
                                urlPat, urlBlock, urlRepl, urlEncodeRepl,
                                dataPat, dataBlock, dataRepl, dataReplaceAll
                        ));
                    }
                    RULES = Collections.unmodifiableList(list);
                } catch (Throwable t) {
                    RULES = Collections.emptyList();
                    if (DEBUG) Log.w(TAG, "Failed to parse rules", t);
                }
            }
        }

        if (DEBUG) {
            Log.i(TAG, "Config loaded: enabled=" + ENABLED +
                    ", rewriteResponses=" + REWRITE_RESPONSES +
                    ", rules=" + RULES.size() +
                    ", maxRewrite=" + (MAX_REWRITE_SIZE / 1024) + "KB" +
                    ", types=" + REWRITE_CT);
        }
    }

    // -------------------- Reflection and hooking helpers --------------------

    private static Method getMethod(Class<?> cls, String name, Class<?>... params) {
        try {
            Method m = cls.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            try {
                Method m = cls.getMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignore) {
            }
        } catch (Throwable ignore) {
        }
        if (DEBUG) Log.w(TAG, "Method not found: " + cls.getName() + "#" + name + Arrays.toString(params));
        return null;
    }

    private static void safeHook(Method m, MethodHook callback) {
        if (m == null) return;
        try {
            Hooking.pineHook(m, callback);
        } catch (Throwable t) {
            if (DEBUG) Log.w(TAG, "Hook failed for " + m, t);
        }
    }

    // -------------------- IO helpers --------------------

    private static String readAssetIfExists(Context ctx, String name) {
        InputStream in = null;
        try {
            in = ctx.getAssets().open(name);
            return readToString(in);
        } catch (Throwable ignore) {
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    private static String readToString(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder(8 * 1024);
        char[] buf = new char[4096];
        try (Reader r = new InputStreamReader(in, Charset.forName("UTF-8"))) {
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static String optStringNonEmpty(JSONObject o, String key) {
        String v = o.optString(key, null);
        if (v == null) return null;
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    private static String optStringAllowEmpty(JSONObject o, String key) {
        if (!o.has(key)) return null; // not provided
        String v = o.optString(key, "");
        // keep empty as valid replacement (deletes match)
        return v;
    }

    // -------------------- Public API --------------------

    // Optional: call this if you change cloner.json at runtime and want to re-read rules.
    public void reload(Context ctx) {
        try {
            loadConfig(ctx.getApplicationContext());
            Log.i(TAG, "Config reloaded. Rules=" + RULES.size());
        } catch (Throwable t) {
            Log.e(TAG, "reload failed", t);
        }
    }
}
