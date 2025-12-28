package com.applisto.appcloner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.util.Log;
import java.io.File;

public class DataExportReceiver extends BroadcastReceiver {
    private static final String TAG = "DataExportReceiver";
    public static final String ACTION_EXPORT_DATA = "com.applisto.appcloner.ACTION_EXPORT_DATA";
    private static final String IPC_PERMISSION = "com.appcloner.replica.permission.REPLICA_IPC";

    // Static lock to prevent concurrent exports
    private static final Object LOCK = new Object();
    private static boolean sIsExporting = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_EXPORT_DATA.equals(intent.getAction())) {
            Log.i(TAG, "Received export data request.");

            // Notify AccessibleDataDirHook that export is starting
            AccessibleDataDirHook.onExportStarting();

            synchronized (LOCK) {
                if (sIsExporting) {
                    Log.w(TAG, "Export already in progress, skipping duplicate request.");
                    AccessibleDataDirHook.onExportCompleted();
                    return;
                }
                sIsExporting = true;
            }

            final String senderPackage = intent.getStringExtra("sender_package");
            final PendingResult pendingResult = goAsync();

            new Thread(() -> {
                try {
                    performExport(context, senderPackage);
                } catch (Throwable t) {
                    Log.e(TAG, "Fatal error in export thread", t);
                } finally {
                    synchronized (LOCK) {
                        sIsExporting = false;
                    }
                    // Notify AccessibleDataDirHook that export is completed
                    AccessibleDataDirHook.onExportCompleted();
                    pendingResult.finish();
                }
            }).start();
        }
    }

    private void performExport(Context context, String senderPackage) {
        String packageName = context.getPackageName();
        Log.i(TAG, "Starting export for package: " + packageName);

        Intent resultIntent = new Intent("com.appcloner.replica.EXPORT_COMPLETED");
        if (senderPackage != null && !senderPackage.isEmpty()) {
            resultIntent.setPackage(senderPackage);
        } else {
            resultIntent.setPackage("com.appcloner.replica");
        }
        resultIntent.putExtra("exported_package", packageName);

        try {
            AppDataManager dataManager = new AppDataManager(context, packageName, false);
            File exportedFile = dataManager.exportAppData();

            resultIntent.putExtra("export_success", true);
            if (exportedFile != null) {
                resultIntent.putExtra("export_path", exportedFile.getAbsolutePath());
                Log.d(TAG, "Export successful: " + exportedFile.getAbsolutePath());
            } else {
                // MediaStore case (Android 10+)
                resultIntent.putExtra("export_path", "Downloads directory");
                Log.d(TAG, "Export successful to Downloads");
            }

        } catch (Throwable t) {
            Log.e(TAG, "Export failed", t);
            resultIntent.putExtra("export_success", false);
            resultIntent.putExtra("error_message", t.getMessage());
        }

        try {
            // Send broadcast with IPC permission requirement for receiver
            context.sendBroadcast(resultIntent, IPC_PERMISSION);
            Log.i(TAG, "Result broadcast sent.");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to send result broadcast", t);
        }
    }
}
