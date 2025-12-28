package com.applisto.appcloner;

import android.app.Dialog;
import android.content.Context;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Properties;

public class SkipDialogs {
    private static final String TAG = "SkipDialogs";

    public static void install(Context context,
                               final List<String> strings,
                               final List<String> stacktraceStrings,
                               final boolean monitorStacktraces,
                               final Properties properties) {

        ShowDialogHook.install(context);
        ShowDialogHook.addHook(new ShowDialogHook() {
            @Override
            public Boolean handleShowDialog(Dialog dialog) {
                // A) Extract text
                String text = getAlertDialogText(dialog);
                // Log.d(TAG, "Dialog text: " + text);

                // B) "Do NOT skip" exceptions
                if (shouldNotSkip(text, properties)) {
                    return null; // continue showing
                }

                // C) Skip by stacktrace match
                if (stacktraceStrings != null && !stacktraceStrings.isEmpty()) {
                    String stackTrace = getCurrentStackTrace();
                    for (String s : stacktraceStrings) {
                        if (s != null && stackTrace.contains(s.toLowerCase())) {
                            Log.i(TAG, "Skipping dialog (stacktrace match): " + s);
                            return Boolean.FALSE; // skip
                        }
                    }
                }

                // D) Skip by text match
                if (strings != null && !strings.isEmpty()) {
                    for (String s : strings) {
                        String match = substitutePlaceholders(context, s, "skip_dialogs_title");
                        if (match != null && text.contains(match.toLowerCase())) {
                            Log.i(TAG, "Skipping dialog (text match): " + match);
                            return Boolean.FALSE; // skip
                        }
                    }
                }

                // E) Optional stacktrace monitor
                if (monitorStacktraces) {
                    showStacktraceNotification(context, properties);
                    // Returns null so behavior doesn't change
                }

                return null;
            }
        });
    }

    private static boolean shouldNotSkip(String text, Properties properties) {
        // Properties check
        if (properties != null) {
            String[] keys = {
                    "device_lock_title",
                    "force_device_lock_title",
                    "new_device_lock_message1",
                    "new_device_lock_message2",
                    "new_device_lock_message3",
                    "new_device_lock_message4"
            };
            for (String key : keys) {
                String val = properties.getProperty(key);
                if (val != null && text.contains(val.toLowerCase())) return true;
            }
        }

        // Hardcoded messages
        if (text.contains("this clone is tied to a different app cloner account") ||
            text.contains("this clone was likely created using an unofficial copy of app cloner") ||
            text.contains("failed to register clone")) {
            return true;
        }

        return false;
    }

    private static String getCurrentStackTrace() {
        StringWriter sw = new StringWriter();
        new Exception().printStackTrace(new PrintWriter(sw));
        return sw.toString().toLowerCase();
    }

    private static String substitutePlaceholders(Context context, String entry, String titleKey) {
        // Basic stub - in real app this replaces %APP_NAME%, etc.
        // For now return entry as-is or lowercased
        return entry.toLowerCase();
    }

    private static void showStacktraceNotification(Context context, Properties properties) {
        String stackTrace = getCurrentStackTrace();
        StringBuilder filtered = new StringBuilder();
        for (String line : stackTrace.split("\n")) {
            if (!line.contains("at com.applisto.appcloner") && !line.contains("top.canyie.pine")) {
                filtered.append(line.replace("\tat", "")).append("\n");
            }
        }

        String label = (properties != null) ? properties.getProperty("tap_to_copy_text_label", "Tap to copy") : "Tap to copy";
        // Simple log for now, replacing Utils.showNotification
        Log.i(TAG, "Dialog Stacktrace:\n" + filtered.toString());
        // HostMonitorNotifications.install could be used or a dedicated notification helper
    }
}
