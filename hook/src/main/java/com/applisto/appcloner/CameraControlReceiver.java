package com.applisto.appcloner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class CameraControlReceiver extends BroadcastReceiver {
    private static final String TAG = "CameraControlReceiver";

    public static final String ACTION_ROTATE_CLOCKWISE = "com.applisto.appcloner.ACTION_ROTATE_CLOCKWISE";
    public static final String ACTION_ROTATE_COUNTERCLOCKWISE = "com.applisto.appcloner.ACTION_ROTATE_COUNTERCLOCKWISE";
    public static final String ACTION_FLIP_HORIZONTALLY = "com.applisto.appcloner.ACTION_FLIP_HORIZONTALLY";
    public static final String ACTION_TOGGLE_SCALE = "com.applisto.appcloner.ACTION_TOGGLE_SCALE";
    public static final String ACTION_SHOW_UI = "com.applisto.appcloner.ACTION_SHOW_UI";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Received action: " + action);

        if (ACTION_ROTATE_CLOCKWISE.equals(action)) {
            FakeCameraHook.performRotate(true);
        } else if (ACTION_ROTATE_COUNTERCLOCKWISE.equals(action)) {
            FakeCameraHook.performRotate(false);
        } else if (ACTION_FLIP_HORIZONTALLY.equals(action)) {
            FakeCameraHook.performFlip();
        } else if (ACTION_TOGGLE_SCALE.equals(action)) {
            FakeCameraHook.toggleScale();
        } else if (ACTION_SHOW_UI.equals(action)) {
            // Check if floating menu is enabled before showing overlay
            if (FakeCameraHook.isFloatingMenuEnabled()) {
                FakeCameraHook.showOverlay();
            } else {
                Log.i(TAG, "Floating menu is disabled, opening file picker directly");
                try {
                    Intent pickIntent = new Intent(context, FakeCameraActivity.class);
                    pickIntent.putExtra("mode", "pick");
                    pickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(pickIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start FakeCameraActivity", e);
                }
            }
        }
    }
}
