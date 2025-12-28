package com.applisto.appcloner;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileOutputStream;

public class FakeCameraAppSupport {
    private static final String TAG = "FakeCameraAppSupport";
    private static final int NOTIFICATION_ID = 12025;

    private static Activity sActivity;
    private static int sRequestCode;
    private static Uri sUri;

    public static void install(Context context) {
        Log.i(TAG, "install; ");
        new ExecStartActivityHook() {
            @Override
            protected boolean onExecStartActivity(ExecStartActivityArgs args) {
                return FakeCameraAppSupport.onExecStartActivity(args);
            }
        }.install(context);
    }

    private static boolean onExecStartActivity(ExecStartActivityHook.ExecStartActivityArgs args) {
        try {
            Intent intent = args.intent;
            if (isCameraIntent(intent) && args.who instanceof Activity) {
                Activity activity = (Activity) args.who;

                sActivity = activity;
                sRequestCode = args.requestCode;

                Log.i(TAG, "onExecStartActivity; sTargetActivity: " + sActivity + ", sRequestCode: " + sRequestCode);

                // Always start FakeCameraActivity to select an image.
                // We no longer check FakeCameraHook.isFakeCameraActive() because the user
                // explicitly wants to choose an image every time the camera is requested.
                try {
                    Class<?> clazz = Class.forName("com.applisto.appcloner.FakeCameraActivity");
                    Intent fakeCamIntent = new Intent(activity, clazz);
                    fakeCamIntent.putExtra("fake_camera_app", true);
                    fakeCamIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(fakeCamIntent);

                    // Show notification
                    showNotification(activity);
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, "FakeCameraActivity class not found", e);
                }

                return false; // Suppress original camera launch
            }
        } catch (Exception e) {
            Log.w(TAG, "Error in onExecStartActivity", e);
        }
        return true;
    }

    private static boolean isCameraIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        if ("android.media.action.IMAGE_CAPTURE".equals(action) ||
            "android.media.action.IMAGE_CAPTURE_SECURE".equals(action)) {

            sUri = intent.getParcelableExtra("output");
            Log.i(TAG, "isCameraIntent; sUri: " + sUri);
            return true;
        }

        if ("android.intent.action.CHOOSER".equals(action)) {
            Intent target = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            return isCameraIntent(target);
        }

        return false;
    }

    public static void notifyImageSelected() {
        Log.i(TAG, "notifyImageSelected called");
        if (sActivity != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Bitmap bitmap = FakeCameraHook.getFakeBitmap();
                    byte[] jpegBytes = FakeCameraHook.getFakeJpegData();
                    setImage(bitmap, jpegBytes);
                } catch (Exception e) {
                    Log.w(TAG, "Error handling notifyImageSelected", e);
                }
            });
        }
    }

    public static void setImage(Bitmap bitmap, byte[] jpegBytes) {
        Log.i(TAG, "setImage; sActivity: " + sActivity + ", sUri: " + sUri);

        if (sActivity == null) return;

        try {
            Intent resultData = new Intent();
            resultData.putExtra("data", bitmap); // For thumbnail

            // Process EXIF before writing
            byte[] finalBytes = jpegBytes;
            if (bitmap != null && finalBytes != null) {
                finalBytes = FakeCameraHook.processImageWithExif(sActivity, finalBytes, bitmap.getWidth(), bitmap.getHeight());
            }

            if (sUri != null) {
                resultData.setData(sUri);

                try (ParcelFileDescriptor pfd = sActivity.getContentResolver().openFileDescriptor(sUri, "w")) {
                    if (pfd != null) {
                        try (FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor())) {
                            if (finalBytes != null) {
                                fos.write(finalBytes);
                            } else if (bitmap != null) {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                            }
                        }
                    }
                }
            }

            try {
                java.lang.reflect.Method method = Activity.class.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class);
                method.setAccessible(true);
                method.invoke(sActivity, sRequestCode, Activity.RESULT_OK, resultData);
                Log.i(TAG, "setImage; onActivityResult called");
            } catch (Exception e) {
                Log.w(TAG, "Failed to invoke onActivityResult", e);
            }

        } catch (Exception e) {
            Log.w(TAG, "Error in setImage", e);
        } finally {
            sActivity = null;
            sRequestCode = 0;
            sUri = null;
        }
    }

    private static void showNotification(Context context) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            String title = "Fake Camera";
            String text = "Tap to select a picture for the fake camera app";

            Notification.Builder builder = new Notification.Builder(context);
            builder.setContentTitle(title)
                   .setContentText(text)
                   .setSmallIcon(android.R.drawable.ic_menu_camera)
                   .setPriority(Notification.PRIORITY_HIGH);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                 String channelId = "fake_camera_channel";
                 android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, "Fake Camera", NotificationManager.IMPORTANCE_HIGH);
                 nm.createNotificationChannel(channel);
                 builder.setChannelId(channelId);
            }

            nm.notify(NOTIFICATION_ID, builder.build());

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                nm.cancel(NOTIFICATION_ID);
            }, 3000);

        } catch (Exception e) {
            Log.w(TAG, "Failed to show notification", e);
        }
    }
}
