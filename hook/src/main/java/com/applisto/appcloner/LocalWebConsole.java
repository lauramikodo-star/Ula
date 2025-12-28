package com.applisto.appcloner;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.applisto.appcloner.MonitorHttpRoutes;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public final class LocalWebConsole {

    private static final String TAG = "LocalWebConsole";
    private static final int NOTIFICATION_ID = 445566;
    private static final String CHANNEL_ID = "web_console";

    private static volatile File homeDir;
    private static volatile File currentDir;
    private static SimpleHttpServer server;
    private static int sPort = -1;
    private static final AtomicInteger activityCounter = new AtomicInteger(0);
    private static boolean isStarted = false;

    public static void install(Context ctx, int port) {
        if (ctx == null) return;
        sPort = port;
        Context appContext = ctx.getApplicationContext();

        homeDir = appContext.getFilesDir().getParentFile();
        currentDir = homeDir;

        server = new SimpleHttpServer(port) {
            @Override protected Response handleRequest(Request req) {
                // Monitor Routes (Host, Header, Preferences)
                try {
                     SimpleHttpServer.Response mon = MonitorHttpRoutes.tryHandle(new SimpleHttpServer.Request(req.method, req.path));
                     if (mon != null) return new Response(mon.statusCode, mon.contentType, mon.body);
                } catch (Throwable t) {
                    // MonitorHttpRoutes might not be compatible or accessible
                }

                // GET /
                if ("GET".equals(req.method)) {
                    if (req.path.equals("/") || req.path.equals("/index.html")) {
                        return new Response(200, "text/html", html());
                    }
                }

                // POST /execute/<cmd>
                if ("POST".equals(req.method) && req.path.startsWith("/execute/")) {
                    String enc = req.path.substring("/execute/".length());
                    String cmd = "";
                    try {
                        cmd = URLDecoder.decode(enc, "UTF-8");
                    } catch (Exception e) {
                         // Should use StandardCharsets.UTF_8 but to be safe with older APIs if needed
                    }
                    return new Response(200, "text/plain", runWhitelisted(cmd));
                }

                return new Response(404, "text/plain", "not found");
            }
        };

        if (appContext instanceof Application) {
            ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    // Start on first activity created, mimicking original behavior
                    if (activityCounter.incrementAndGet() == 1) {
                         // We start here but also check in onActivityStarted for robustness
                         start(appContext);
                    }
                }

                @Override
                public void onActivityStarted(Activity activity) {
                     // Ensure it is started if we missed creation or it was stopped
                     if (!isStarted) {
                         start(appContext);
                     }
                }

                @Override
                public void onActivityResumed(Activity activity) {}
                @Override
                public void onActivityPaused(Activity activity) {}
                @Override
                public void onActivityStopped(Activity activity) {}
                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

                @Override
                public void onActivityDestroyed(Activity activity) {
                    if (activityCounter.decrementAndGet() <= 0) {
                        stop(appContext);
                    }
                }
            });
        }
    }

    public static synchronized void start(Context context) {
        if (server != null && !isStarted) {
            server.start();
            isStarted = true;
            showNotification(context);
        }
    }

    public static synchronized void stop(Context context) {
        if (server != null && isStarted) {
            server.stop();
            isStarted = false;
            hideNotification(context);
        }
    }

    public static int getPort() {
        if (server != null) return server.getPort();
        return sPort;
    }

    public static String getConsoleUrl() {
        String ip = getIpAddress();
        return "http://" + ip + ":" + getPort();
    }

    private static String getIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                java.net.NetworkInterface intf = en.nextElement();
                java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses();
                while (enumIpAddr.hasMoreElements()) {
                    java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Get IP failed", ex);
        }
        return "localhost";
    }

    private static void showNotification(Context context) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            String title = "Web Console Running";
            String text = getConsoleUrl();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Web Console", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Shows when the local web console is active");
                nm.createNotificationChannel(channel);
            }

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context);
            }

            builder.setContentTitle(title)
                   .setContentText(text)
                   .setSmallIcon(android.R.drawable.sym_def_app_icon)
                   .setOngoing(true);

            // Add pending intent to open browser? or just open app?
            // For now just basic notification

            nm.notify(NOTIFICATION_ID, builder.build());

        } catch (Throwable t) {
            Log.e(TAG, "Failed to show notification", t);
        }
    }

    private static void hideNotification(Context context) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(NOTIFICATION_ID);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hide notification", t);
        }
    }

    private static String runWhitelisted(String input) {
        String cmd = input == null ? "" : input.trim();
        if (cmd.isEmpty()) return "\r\n";

        if (cmd.equals("help")) {
            return "Allowed commands:\r\n" +
                    "  help\r\n  pwd\r\n  ls\r\n  cd <dir>\r\n  cat <file>\r\n";
        }

        if (cmd.equals("pwd")) {
            return currentDir.getAbsolutePath() + "\r\n";
        }

        if (cmd.equals("ls")) {
            File[] files = currentDir.listFiles();
            if (files == null) return "(empty)\r\n";
            StringBuilder sb = new StringBuilder();
            for (File f : files) {
                sb.append(f.isDirectory() ? "[D] " : "    ");
                sb.append(f.getName()).append("\r\n");
            }
            return sb.toString();
        }

        if (cmd.startsWith("cd ")) {
            String arg = cmd.substring(3).trim();
            return cd(arg);
        }

        if (cmd.startsWith("cat ")) {
            String name = cmd.substring(4).trim();
            return cat(name);
        }

        return "Blocked. Type 'help'.\r\n";
    }

    private static String cd(String arg) {
        File target;
        if (arg.equals("~")) target = homeDir;
        else if (arg.equals("..")) target = currentDir.getParentFile();
        else target = new File(currentDir, arg);

        try {
            if (target != null && target.isDirectory()) {
                File canon = target.getCanonicalFile();

                // Restrict navigation inside app sandbox (homeDir)
                if (!canon.getAbsolutePath().startsWith(homeDir.getCanonicalPath())) {
                    return "cd: blocked (outside sandbox)\r\n";
                }

                currentDir = canon;
                return "Directory changed to " + currentDir.getAbsolutePath() + "\r\n";
            }
        } catch (Throwable ignored) {}

        return "cd: " + arg + ": No such directory\r\n";
    }

    private static String cat(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "cat: missing file\r\n";
        File f = new File(currentDir, fileName);
        try {
            File canon = f.getCanonicalFile();
            if (!canon.getAbsolutePath().startsWith(homeDir.getCanonicalPath())) {
                return "cat: blocked (outside sandbox)\r\n";
            }
            if (!canon.isFile()) return "cat: not a file\r\n";

            // Limit read size
            byte[] buffer = new byte[32 * 1024];
            int read;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(canon)) {
                read = fis.read(buffer);
            }
            if (read <= 0) return "(empty)\r\n";
            return new String(buffer, 0, read, StandardCharsets.UTF_8) + "\r\n";
        } catch (Throwable t) {
            return "cat failed: " + t.getMessage() + "\r\n";
        }
    }

    private static String html() {
        // Minimal terminal UI (no external CDN needed)
        return "<!doctype html><meta charset=utf-8>" +
                "<title>Local Console</title>" +
                "<style>body{font-family:monospace;margin:0;padding:12px}#out{white-space:pre-wrap}</style>" +
                "<div id=out>Welcome. Type: help\n\n</div>" +
                "<input id=in style='width:100%;font-family:monospace;font-size:16px' autofocus />" +
                "<script>" +
                "const out=document.getElementById('out'); const inp=document.getElementById('in');" +
                "function write(s){out.textContent+=s; window.scrollTo(0,document.body.scrollHeight);} " +
                "inp.addEventListener('keydown',e=>{if(e.key==='Enter'){const c=inp.value; inp.value='';" +
                "write('$ '+c+'\\n'); fetch('/execute/'+encodeURIComponent(c),{method:'POST'})" +
                ".then(r=>r.text()).then(t=>write(t)).catch(()=>write('error\\n'));}});" +
                "</script>";
    }
}
