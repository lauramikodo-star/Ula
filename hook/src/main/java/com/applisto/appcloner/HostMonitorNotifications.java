package com.applisto.appcloner;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public final class HostMonitorNotifications {

    private HostMonitorNotifications() {}

    public static void install(Context context, int notificationId, String routePath, String title) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof Application) {
            Application app = (Application) appContext;

            app.registerActivityLifecycleCallbacks(new SimpleActivityCallbacks() {
                private boolean shown;

                @Override public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {
                    if (shown) return;
                    shown = true;

                    // Pointing to LocalWebConsole port (18080)
                    String baseUrl = LocalWebConsole.getConsoleUrl();
                    if (LocalWebConsole.getPort() <= 0) return; // invalid port

                    String url = baseUrl + "/" + routePath;

                    showNotification(activity, notificationId, title, url);
                }

                @Override public void onActivityDestroyed(Activity activity) {
                    // On last activity destroy, you can hide; simple approach: hide always
                    hideNotification(activity, notificationId);
                }
            });
        }
    }

    private static void showNotification(Context ctx, int id, String title, String url) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "host_monitor";

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(channelId, "Host Monitor", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }

        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        PendingIntent pi = PendingIntent.getActivity(
                ctx, 0, i,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(ctx, channelId)
                : new Notification.Builder(ctx);

        Notification n = b.setContentTitle(title)
                .setContentText(url)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        nm.notify(id, n);
    }

    private static void hideNotification(Context ctx, int id) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(id);
    }

    /** Minimal lifecycle adapter */
    static class SimpleActivityCallbacks implements Application.ActivityLifecycleCallbacks {
        @Override public void onActivityCreated(Activity a, android.os.Bundle b) {}
        @Override public void onActivityStarted(Activity a) {}
        @Override public void onActivityResumed(Activity a) {}
        @Override public void onActivityPaused(Activity a) {}
        @Override public void onActivityStopped(Activity a) {}
        @Override public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) {}
        @Override public void onActivityDestroyed(Activity a) {}
    }
}
