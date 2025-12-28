package com.applisto.appcloner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.InputStream;

/**
 * Proxy Activity to handle Intent.ACTION_OPEN_DOCUMENT without UI.
 * This activity launches the system picker and forwards the result to FakeCameraHook.
 */
public class FakeCameraActivity extends Activity {
    private static final String TAG = "FakeCameraActivity";
    private static final int REQUEST_CODE_SELECT_PICTURE_FROM_STORAGE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make window invisible/transparent to avoid flashing
        Window window = getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        String mode = getIntent().getStringExtra("mode");
        if ("pick".equals(mode)) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_CODE_SELECT_PICTURE_FROM_STORAGE);
        } else {
            // Fallback or error
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult: " + requestCode + ", " + resultCode);

        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            handleSelectedImage(data.getData());
        }

        // Always finish after handling result
        finish();
    }

    private void handleSelectedImage(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();

            if (bitmap != null) {
                FakeCameraHook.setFakeBitmap(bitmap);
                Log.i(TAG, "Selected image set successfully: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                Toast.makeText(this, "Image loaded. Use controls to adjust.", Toast.LENGTH_LONG).show();

                // Notify AppSupport that an image is selected
                FakeCameraAppSupport.notifyImageSelected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load image", e);
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }
}
