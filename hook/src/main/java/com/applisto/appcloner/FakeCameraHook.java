package com.applisto.appcloner;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Executor;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public final class FakeCameraHook {
    private static final String TAG = "FakeCameraHook";
    private static volatile boolean sHooked = false;

    /* ---------- Settings loaded from cloner.json ---------- */
    private static boolean ENABLED;
    private static String FAKE_IMAGE_PATH;
    private static boolean ROTATE_IMAGE;
    private static int ROTATION_ANGLE;
    private static boolean FLIP_HORIZONTALLY;
    private static boolean RESIZE_IMAGE;
    private static boolean ADD_EXIF_ATTRIBUTES;
    private static boolean RANDOMIZE_IMAGE;
    private static int RANDOMIZE_STRENGTH;
    private static boolean ALTERNATIVE_MODE;
    private static boolean OPEN_STREAM_WORKAROUND;
    private static boolean CLOSE_STREAM_WORKAROUND;
    private static boolean FLOATING_MENU_ENABLED;
    private static boolean USE_RANDOM_IMAGE;
    private static boolean PRESERVE_ASPECT_RATIO;
    private static boolean CENTER_IMAGE;
    private static boolean FILL_IMAGE;
    private static String[] FAKE_IMAGE_PATHS;
    private static boolean HOOK_LOW_LEVEL_APIS;
    private static boolean SYSTEM_CAMERA_WORKAROUND;
    private static boolean ADD_SPOOFED_LOCATION;
    /* ==================================================== */

    // Cached fake images
    private static Bitmap sFakeBitmap;
    private static byte[] sFakeJpegData;
    private static List<Bitmap> sFakeBitmaps = new ArrayList<>();
    private static int sCurrentImageIndex = 0;

    // Cache for processed frame data
    private static volatile byte[] sCachedYuvData;
    private static int sCachedWidth;
    private static int sCachedHeight;
    private static int sCachedFormat;
    private static long sCachedBitmapId; // To invalidate cache if bitmap changes

    // Reusable bitmap to prevent OOM
    private static Bitmap sIntermediateBitmap;
    // Reusable buffers to prevent OOM/churn
    private static int[] sCachedPixelBuffer;
    private static byte[] sCachedNV21Buffer;

    private static final Object sProcessingLock = new Object();

    // Manual Adjustments
    private static volatile float sZoomLevel = 1.0f;
    private static volatile int sTranslateX = 0;
    private static volatile int sTranslateY = 0;

    // Random for image randomization
    private static Random sRandom = new Random();

    // Handler for UI operations
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    // Notification ID
    private static final int NOTIFICATION_ID = 556712456;

    // Strings properties for notifications
    private static Properties sStringsProperties;

    // Time tracking
    private static long sPictureTakenMillis;
    private static long sPictureTakenNanos;

    // For "Fake Camera Active" logic
    private static long sImageSetMillis;

    // System camera workaround flag
    private static boolean sSystemCameraWorkaroundActive = false;

    private static Context sContext;

    // Static instance for helper access
    private static FakeCameraHook sInstance;

    // Current Activity Tracker
    private static WeakReference<Activity> sCurrentActivity;
    private static WeakReference<View> sOverlayView;

    public void init(Context ctx) {
        if (sHooked) return;
        sHooked = true;
        sContext = ctx;
        sInstance = this;

        loadSettings(ctx);
        if (!ENABLED) {
            Log.i(TAG, "FakeCameraHook disabled in cloner.json");
            return;
        }

        try {
            loadStringsProperties(ctx);
            loadFakeImages(ctx);
            hookCameraAPIs();

            // Additional hooks for enhanced compatibility
            if (HOOK_LOW_LEVEL_APIS) {
                hookLowLevelCameraAPIs();
            }
            
            // Activate system camera workaround if enabled
            if (SYSTEM_CAMERA_WORKAROUND) {
                activateSystemCameraWorkaround(ctx);
            }
            
            // Hook FileOutputStream for close stream workaround
            if (CLOSE_STREAM_WORKAROUND) {
                hookFileOutputStream();
            }

            // Install App Support for intents
            FakeCameraAppSupport.install(ctx);

            // Register Activity Lifecycle Callback
            if (ctx.getApplicationContext() instanceof Application) {
                ((Application) ctx.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
                    @Override
                    public void onActivityStarted(Activity activity) {}
                    @Override
                    public void onActivityResumed(Activity activity) {
                        sCurrentActivity = new WeakReference<>(activity);
                        // If overlay was previously showing on this activity, we might need to re-add?
                        // For now, simpler to let user re-request it via notification.
                    }
                    @Override
                    public void onActivityPaused(Activity activity) {}
                    @Override
                    public void onActivityStopped(Activity activity) {}
                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                    @Override
                    public void onActivityDestroyed(Activity activity) {}
                });
            }

            // Show notification on startup
            showNotification();

            Log.i(TAG, "FakeCameraHook active with system camera workaround");
        } catch (Exception e) {
            Log.e(TAG, "Hook failed", e);
        }
    }

    /* ---------- Public Accessors for Helper Classes ---------- */
    public static void setFakeBitmap(Bitmap bitmap) {
        synchronized (sProcessingLock) {
            if (bitmap != null) {
                sFakeBitmap = bitmap;
                // Reset adjustments
                sZoomLevel = 1.0f;
                sTranslateX = 0;
                sTranslateY = 0;
                // Clear the list so getCurrentFakeImage() uses this new bitmap instead of cycling assets
                sFakeBitmaps.clear();
                sFakeJpegData = bitmapToJpeg(bitmap);
                // Invalidate cache by clearing it
                sCachedYuvData = null;
                sImageSetMillis = System.currentTimeMillis();
                Log.i(TAG, "New fake bitmap set. Size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                showNotification();
            }
        }
    }

    public static void adjustZoom(float factor) {
        synchronized (sProcessingLock) {
            sZoomLevel *= factor;
            if (sZoomLevel < 0.1f) sZoomLevel = 0.1f;
            if (sZoomLevel > 10.0f) sZoomLevel = 10.0f;
            sCachedYuvData = null; // Invalidate cache
            Log.i(TAG, "Zoom adjusted: " + sZoomLevel);
        }
    }

    public static void adjustTranslation(int x, int y) {
        synchronized (sProcessingLock) {
            sTranslateX += x;
            sTranslateY += y;
            sCachedYuvData = null; // Invalidate cache
            Log.i(TAG, "Translation adjusted: " + sTranslateX + ", " + sTranslateY);
        }
    }

    public static void resetAdjustments() {
        synchronized (sProcessingLock) {
            sZoomLevel = 1.0f;
            sTranslateX = 0;
            sTranslateY = 0;
            sCachedYuvData = null;
            Log.i(TAG, "Adjustments reset");
        }
    }

    public static Bitmap getFakeBitmap() {
        return sFakeBitmap;
    }

    public static byte[] getFakeJpegData() {
        return sFakeJpegData;
    }

    public static boolean isFakeCameraActive() {
        if (sFakeJpegData == null) {
            return false;
        }
        if (ALTERNATIVE_MODE) {
            return true;
        }
        // Active if an image was set recently (e.g., within 30 seconds)
        return System.currentTimeMillis() - sImageSetMillis < 30000;
    }

    public static void showNotification() {
        if (sContext == null) return;
        sHandler.post(() -> {
            try {
                NotificationManager nm = (NotificationManager) sContext.getSystemService(Context.NOTIFICATION_SERVICE);
                String title = sStringsProperties.getProperty("fake_camera_title", "Fake Camera");
                String text = "Tap to change image.";

                // Intent to show the UI overlay (broadcast to CameraControlReceiver)
                Intent intent = new Intent(sContext, CameraControlReceiver.class);
                intent.setAction(CameraControlReceiver.ACTION_SHOW_UI);
                PendingIntent pi = PendingIntent.getBroadcast(sContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                Notification.Builder builder = new Notification.Builder(sContext);
                builder.setContentTitle(title)
                       .setContentText(text)
                       .setSmallIcon(android.R.drawable.ic_menu_camera)
                       .setContentIntent(pi)
                       .setAutoCancel(false)
                       .setOngoing(true);

                // Add Rotate action
                Intent rotateIntent = new Intent(sContext, CameraControlReceiver.class);
                rotateIntent.setAction(CameraControlReceiver.ACTION_ROTATE_CLOCKWISE);
                PendingIntent rotatePi = PendingIntent.getBroadcast(sContext, 1, rotateIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                builder.addAction(android.R.drawable.ic_menu_rotate, "ROTATE", rotatePi);

                // Add Flip action
                Intent flipIntent = new Intent(sContext, CameraControlReceiver.class);
                flipIntent.setAction(CameraControlReceiver.ACTION_FLIP_HORIZONTALLY);
                PendingIntent flipPi = PendingIntent.getBroadcast(sContext, 2, flipIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                builder.addAction(android.R.drawable.ic_menu_directions, "FLIP", flipPi);

                // Add Scale action
                Intent scaleIntent = new Intent(sContext, CameraControlReceiver.class);
                scaleIntent.setAction(CameraControlReceiver.ACTION_TOGGLE_SCALE);
                PendingIntent scalePi = PendingIntent.getBroadcast(sContext, 3, scaleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                builder.addAction(android.R.drawable.ic_menu_crop, FILL_IMAGE ? "FIT" : "FILL", scalePi);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                     String channelId = "fake_camera_status";
                     android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, "Fake Camera Status", NotificationManager.IMPORTANCE_LOW);
                     nm.createNotificationChannel(channel);
                     builder.setChannelId(channelId);
                }

                if (sFakeBitmap != null) {
                    Notification.BigPictureStyle style = new Notification.BigPictureStyle();
                    style.bigPicture(sFakeBitmap);
                    style.setSummaryText(text);
                    builder.setStyle(style);
                }

                nm.notify(NOTIFICATION_ID + 1, builder.build());
            } catch (Exception e) {
                Log.w(TAG, "Failed to show notification", e);
            }
        });
    }

    // Overlay UI state
    private static boolean sOverlayExpanded = true;
    private static TextView sZoomValueText;
    private static TextView sPanValueText;

    public static boolean isFloatingMenuEnabled() {
        return FLOATING_MENU_ENABLED;
    }

    public static void showOverlay() {
        // Check if floating menu is enabled
        if (!FLOATING_MENU_ENABLED) {
            Log.i(TAG, "Floating menu is disabled in settings, not showing overlay");
            return;
        }

        if (sCurrentActivity == null) {
            Log.w(TAG, "Cannot show overlay: No active activity found.");
            return;
        }

        final Activity activity = sCurrentActivity.get();
        if (activity == null || activity.isFinishing()) {
            Log.w(TAG, "Cannot show overlay: Activity is null or finishing.");
            return;
        }

        sHandler.post(() -> {
            // Remove existing overlay if any
            if (sOverlayView != null && sOverlayView.get() != null) {
                View oldView = sOverlayView.get();
                try {
                    ViewGroup parent = (ViewGroup) oldView.getParent();
                    if (parent != null) {
                        parent.removeView(oldView);
                    }
                } catch (Exception e) {
                    // Ignore
                }
                sOverlayView = null;
            }

            try {
                // Create a draggable floating panel
                final FrameLayout rootContainer = new FrameLayout(activity);
                rootContainer.setClickable(false);
                rootContainer.setFocusable(false);

                // Main floating card
                final LinearLayout floatingCard = new LinearLayout(activity);
                floatingCard.setOrientation(LinearLayout.VERTICAL);
                floatingCard.setBackgroundColor(Color.parseColor("#E8303030"));
                floatingCard.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 12));
                floatingCard.setElevation(dp(activity, 8));

                // Make card draggable
                final float[] lastTouchX = {0};
                final float[] lastTouchY = {0};
                final float[] cardX = {dp(activity, 8)};
                final float[] cardY = {dp(activity, 100)};

                // ===== COMPACT HEADER =====
                LinearLayout headerRow = new LinearLayout(activity);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                headerRow.setGravity(Gravity.CENTER_VERTICAL);

                // Drag handle / Title
                TextView titleText = new TextView(activity);
                titleText.setText("📷 Fake Camera");
                titleText.setTextColor(Color.WHITE);
                titleText.setTextSize(14);
                titleText.setPadding(0, 0, dp(activity, 8), 0);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                headerRow.addView(titleText, titleParams);

                // Toggle expand/collapse button
                final Button btnToggle = new Button(activity);
                btnToggle.setText(sOverlayExpanded ? "▲" : "▼");
                btnToggle.setTextColor(Color.WHITE);
                btnToggle.setBackgroundColor(Color.TRANSPARENT);
                btnToggle.setTextSize(14);
                btnToggle.setMinimumWidth(dp(activity, 40));
                btnToggle.setMinimumHeight(dp(activity, 36));
                btnToggle.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);

                // Close button
                Button btnClose = new Button(activity);
                btnClose.setText("✕");
                btnClose.setTextColor(Color.parseColor("#FF6666"));
                btnClose.setBackgroundColor(Color.TRANSPARENT);
                btnClose.setTextSize(14);
                btnClose.setMinimumWidth(dp(activity, 40));
                btnClose.setMinimumHeight(dp(activity, 36));
                btnClose.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
                btnClose.setOnClickListener(v -> {
                    try {
                        ViewGroup parent = (ViewGroup) rootContainer.getParent();
                        if (parent != null) {
                            parent.removeView(rootContainer);
                        }
                        sOverlayView = null;
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing overlay", e);
                    }
                });

                headerRow.addView(btnToggle);
                headerRow.addView(btnClose);
                floatingCard.addView(headerRow);

                // ===== EXPANDABLE CONTENT =====
                final LinearLayout contentLayout = new LinearLayout(activity);
                contentLayout.setOrientation(LinearLayout.VERTICAL);
                contentLayout.setVisibility(sOverlayExpanded ? View.VISIBLE : View.GONE);
                contentLayout.setPadding(0, dp(activity, 8), 0, 0);

                // ----- STATUS ROW -----
                LinearLayout statusRow = new LinearLayout(activity);
                statusRow.setOrientation(LinearLayout.HORIZONTAL);
                statusRow.setGravity(Gravity.CENTER);
                statusRow.setPadding(0, 0, 0, dp(activity, 8));

                sZoomValueText = new TextView(activity);
                sZoomValueText.setText(String.format("Zoom: %.1fx", sZoomLevel));
                sZoomValueText.setTextColor(Color.parseColor("#88FF88"));
                sZoomValueText.setTextSize(11);
                sZoomValueText.setPadding(0, 0, dp(activity, 16), 0);

                sPanValueText = new TextView(activity);
                sPanValueText.setText(String.format("Pan: %d, %d", sTranslateX, sTranslateY));
                sPanValueText.setTextColor(Color.parseColor("#88AAFF"));
                sPanValueText.setTextSize(11);

                statusRow.addView(sZoomValueText);
                statusRow.addView(sPanValueText);
                contentLayout.addView(statusRow);

                // ----- SELECT IMAGE BUTTON -----
                Button btnSelect = createCompactButton(activity, "📁 Select Image");
                btnSelect.setOnClickListener(v -> {
                    Intent intent = new Intent(activity, FakeCameraActivity.class);
                    intent.putExtra("mode", "pick");
                    activity.startActivity(intent);
                });
                contentLayout.addView(btnSelect);

                addSpacer(activity, contentLayout, dp(activity, 8));

                // ----- ZOOM CONTROL (SeekBar style) -----
                TextView zoomLabel = new TextView(activity);
                zoomLabel.setText("Zoom");
                zoomLabel.setTextColor(Color.LTGRAY);
                zoomLabel.setTextSize(11);
                contentLayout.addView(zoomLabel);

                LinearLayout zoomRow = new LinearLayout(activity);
                zoomRow.setOrientation(LinearLayout.HORIZONTAL);
                zoomRow.setGravity(Gravity.CENTER_VERTICAL);

                // SeekBar for smooth zoom (declare early so buttons can reference it)
                final android.widget.SeekBar zoomSeekBar = new android.widget.SeekBar(activity);

                Button btnZoomOut = createSmallButton(activity, "−");
                btnZoomOut.setOnClickListener(v -> {
                    adjustZoom(0.9f);
                    zoomSeekBar.setProgress(zoomToProgress(sZoomLevel));
                    updateStatusDisplay();
                });
                zoomSeekBar.setMax(100);
                zoomSeekBar.setProgress(zoomToProgress(sZoomLevel));
                LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                seekParams.setMargins(dp(activity, 8), 0, dp(activity, 8), 0);
                zoomSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            float newZoom = progressToZoom(progress);
                            synchronized (sProcessingLock) {
                                sZoomLevel = newZoom;
                                sCachedYuvData = null;
                            }
                            updateStatusDisplay();
                        }
                    }
                    @Override
                    public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                    @Override
                    public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
                });

                Button btnZoomIn = createSmallButton(activity, "+");
                btnZoomIn.setOnClickListener(v -> {
                    adjustZoom(1.1f);
                    zoomSeekBar.setProgress(zoomToProgress(sZoomLevel));
                    updateStatusDisplay();
                });

                zoomRow.addView(btnZoomOut);
                zoomRow.addView(zoomSeekBar, seekParams);
                zoomRow.addView(btnZoomIn);
                contentLayout.addView(zoomRow);

                addSpacer(activity, contentLayout, dp(activity, 8));

                // ----- PAN CONTROL (D-Pad style, more compact) -----
                TextView panLabel = new TextView(activity);
                panLabel.setText("Pan Position");
                panLabel.setTextColor(Color.LTGRAY);
                panLabel.setTextSize(11);
                contentLayout.addView(panLabel);

                // D-Pad layout
                LinearLayout dpadContainer = new LinearLayout(activity);
                dpadContainer.setOrientation(LinearLayout.VERTICAL);
                dpadContainer.setGravity(Gravity.CENTER);

                // Top row (UP button)
                LinearLayout topRow = new LinearLayout(activity);
                topRow.setGravity(Gravity.CENTER);
                Button btnUp = createDpadButton(activity, "▲");
                btnUp.setOnClickListener(v -> { adjustTranslation(0, -30); updateStatusDisplay(); });
                topRow.addView(btnUp);

                // Middle row (LEFT, CENTER/RESET, RIGHT)
                LinearLayout midRow = new LinearLayout(activity);
                midRow.setGravity(Gravity.CENTER);
                Button btnLeft = createDpadButton(activity, "◀");
                btnLeft.setOnClickListener(v -> { adjustTranslation(-30, 0); updateStatusDisplay(); });
                Button btnCenter = createDpadButton(activity, "⟲");
                btnCenter.setBackgroundColor(Color.parseColor("#444444"));
                btnCenter.setOnClickListener(v -> {
                    resetAdjustments();
                    zoomSeekBar.setProgress(zoomToProgress(sZoomLevel));
                    updateStatusDisplay();
                    Toast.makeText(activity, "Reset", Toast.LENGTH_SHORT).show();
                });
                Button btnRight = createDpadButton(activity, "▶");
                btnRight.setOnClickListener(v -> { adjustTranslation(30, 0); updateStatusDisplay(); });
                midRow.addView(btnLeft);
                midRow.addView(btnCenter);
                midRow.addView(btnRight);

                // Bottom row (DOWN button)
                LinearLayout botRow = new LinearLayout(activity);
                botRow.setGravity(Gravity.CENTER);
                Button btnDown = createDpadButton(activity, "▼");
                btnDown.setOnClickListener(v -> { adjustTranslation(0, 30); updateStatusDisplay(); });
                botRow.addView(btnDown);

                dpadContainer.addView(topRow);
                dpadContainer.addView(midRow);
                dpadContainer.addView(botRow);
                contentLayout.addView(dpadContainer);

                addSpacer(activity, contentLayout, dp(activity, 8));

                // ----- QUICK ACTIONS ROW -----
                LinearLayout actionsRow = new LinearLayout(activity);
                actionsRow.setOrientation(LinearLayout.HORIZONTAL);
                actionsRow.setGravity(Gravity.CENTER);

                Button btnRotate = createCompactButton(activity, "↻ Rotate");
                btnRotate.setOnClickListener(v -> {
                    performRotate(true);
                    Toast.makeText(activity, "Rotated 90°", Toast.LENGTH_SHORT).show();
                });

                Button btnFlip = createCompactButton(activity, "↔ Flip");
                btnFlip.setOnClickListener(v -> {
                    performFlip();
                    Toast.makeText(activity, "Flipped", Toast.LENGTH_SHORT).show();
                });

                Button btnScale = createCompactButton(activity, FILL_IMAGE ? "⊡ Fit" : "⊞ Fill");
                btnScale.setOnClickListener(v -> {
                    toggleScale();
                    btnScale.setText(FILL_IMAGE ? "⊡ Fit" : "⊞ Fill");
                });

                actionsRow.addView(btnRotate);
                addSpacer(activity, actionsRow, dp(activity, 4));
                actionsRow.addView(btnFlip);
                addSpacer(activity, actionsRow, dp(activity, 4));
                actionsRow.addView(btnScale);
                contentLayout.addView(actionsRow);

                floatingCard.addView(contentLayout);

                // Toggle expand/collapse
                btnToggle.setOnClickListener(v -> {
                    sOverlayExpanded = !sOverlayExpanded;
                    contentLayout.setVisibility(sOverlayExpanded ? View.VISIBLE : View.GONE);
                    btnToggle.setText(sOverlayExpanded ? "▲" : "▼");
                });

                // Make title/header draggable
                headerRow.setOnTouchListener((v, event) -> {
                    switch (event.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            lastTouchX[0] = event.getRawX();
                            lastTouchY[0] = event.getRawY();
                            return true;
                        case android.view.MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - lastTouchX[0];
                            float dy = event.getRawY() - lastTouchY[0];
                            cardX[0] += dx;
                            cardY[0] += dy;
                            floatingCard.setX(cardX[0]);
                            floatingCard.setY(cardY[0]);
                            lastTouchX[0] = event.getRawX();
                            lastTouchY[0] = event.getRawY();
                            return true;
                    }
                    return false;
                });

                // Position the floating card
                floatingCard.setX(cardX[0]);
                floatingCard.setY(cardY[0]);

                FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                rootContainer.addView(floatingCard, cardParams);

                // Add root container to activity
                FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
                activity.addContentView(rootContainer, rootParams);
                sOverlayView = new WeakReference<>(rootContainer);

                // Update initial status
                updateStatusDisplay();

            } catch (Exception e) {
                Log.e(TAG, "Failed to show overlay", e);
                Toast.makeText(activity, "Could not show Fake Camera controls", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Helper to convert dp to pixels
    private static int dp(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    // Create a compact styled button
    private static Button createCompactButton(Context context, String text) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#555555"));
        btn.setTextSize(12);
        btn.setMinimumHeight(dp(context, 36));
        btn.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4));
        btn.setAllCaps(false);
        return btn;
    }

    // Create a small +/- button
    private static Button createSmallButton(Context context, String text) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#666666"));
        btn.setTextSize(16);
        btn.setMinimumWidth(dp(context, 40));
        btn.setMinimumHeight(dp(context, 36));
        btn.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        return btn;
    }

    // Create a D-pad directional button
    private static Button createDpadButton(Context context, String text) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#505050"));
        btn.setTextSize(14);
        btn.setMinimumWidth(dp(context, 44));
        btn.setMinimumHeight(dp(context, 38));
        btn.setPadding(dp(context, 4), dp(context, 2), dp(context, 4), dp(context, 2));
        return btn;
    }

    // Convert zoom level to seekbar progress (0.1x - 5.0x mapped to 0-100)
    private static int zoomToProgress(float zoom) {
        // Logarithmic scale for better control
        // 0.1 -> 0, 1.0 -> 50, 5.0 -> 100
        float logZoom = (float) Math.log10(zoom);
        float logMin = (float) Math.log10(0.1);
        float logMax = (float) Math.log10(5.0);
        return (int) ((logZoom - logMin) / (logMax - logMin) * 100);
    }

    // Convert seekbar progress to zoom level
    private static float progressToZoom(int progress) {
        float logMin = (float) Math.log10(0.1);
        float logMax = (float) Math.log10(5.0);
        float logZoom = logMin + (progress / 100f) * (logMax - logMin);
        return (float) Math.pow(10, logZoom);
    }

    // Update status display
    private static void updateStatusDisplay() {
        sHandler.post(() -> {
            if (sZoomValueText != null) {
                sZoomValueText.setText(String.format("Zoom: %.1fx", sZoomLevel));
            }
            if (sPanValueText != null) {
                sPanValueText.setText(String.format("Pan: %d, %d", sTranslateX, sTranslateY));
            }
        });
    }

    private static void addSpacer(Context context, LinearLayout layout, int height) {
        View spacer = new View(context);
        layout.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
    }

    public static void performRotate(boolean clockwise) {
        if (sFakeBitmap == null) return;

        Matrix matrix = new Matrix();
        matrix.postRotate(clockwise ? 90 : -90);

        Bitmap rotatedBitmap = Bitmap.createBitmap(sFakeBitmap, 0, 0, sFakeBitmap.getWidth(), sFakeBitmap.getHeight(), matrix, true);
        setFakeBitmap(rotatedBitmap);
        showNotification();
    }

    public static void performFlip() {
        if (sFakeBitmap == null) return;

        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1, sFakeBitmap.getWidth() / 2f, sFakeBitmap.getHeight() / 2f);

        Bitmap flippedBitmap = Bitmap.createBitmap(sFakeBitmap, 0, 0, sFakeBitmap.getWidth(), sFakeBitmap.getHeight(), matrix, true);
        setFakeBitmap(flippedBitmap);
        showNotification();
    }

    public static void toggleScale() {
        FILL_IMAGE = !FILL_IMAGE;
        showNotification();
        Toast.makeText(sContext, FILL_IMAGE ? "Scale: Fill Screen" : "Scale: Fit Image", Toast.LENGTH_SHORT).show();
    }

    /**
     * Processes an image by saving to temp file, adding EXIF, and reading back.
     */
    public static byte[] processImageWithExif(Context context, byte[] jpegData, int width, int height) {
        if (sInstance == null || !ADD_EXIF_ATTRIBUTES) {
            return jpegData;
        }

        File tempFile = sInstance.saveToTempFile(jpegData);
        if (tempFile != null) {
            sInstance.setGeneralExifAttributes(tempFile, width, height);
            if (ADD_SPOOFED_LOCATION) {
                sInstance.setLocationExifAttributes(tempFile);
            }

            try (FileInputStream fis = new FileInputStream(tempFile)) {
                 byte[] newData = new byte[(int) tempFile.length()];
                 fis.read(newData);
                 jpegData = newData;
            } catch (IOException e) {
                Log.w(TAG, "Failed to read back temp file", e);
            }
            tempFile.delete();
        }
        return jpegData;
    }

    /* ---------- 1. Load settings ---------- */
    private void loadSettings(Context ctx) {
        try {
            ClonerSettings settings = ClonerSettings.get(ctx);
            JSONObject cfg = settings.raw();
            
            // Main enable toggle
            ENABLED = settings.fakeCameraEnabled();
            FAKE_IMAGE_PATH = settings.fakeCameraImagePath();
            
            // New organized settings matching screenshot categories
            ALTERNATIVE_MODE = settings.fakeCameraAlternativeMode();
            FLIP_HORIZONTALLY = settings.fakeCameraFlipHorizontally();
            OPEN_STREAM_WORKAROUND = settings.fakeCameraOpenStreamWorkaround();
            CLOSE_STREAM_WORKAROUND = settings.fakeCameraCloseStreamWorkaround();
            FLOATING_MENU_ENABLED = settings.fakeCameraFloatingMenuEnabled();
            RANDOMIZE_IMAGE = settings.fakeCameraRandomizeImage();
            RANDOMIZE_STRENGTH = settings.fakeCameraRandomizeStrength();
            RESIZE_IMAGE = settings.fakeCameraResizeImage();
            
            // Rotation setting - parse from string
            String rotation = settings.fakeCameraRotation();
            if ("NO_CHANGE".equals(rotation)) {
                ROTATE_IMAGE = false;
                ROTATION_ANGLE = 0;
            } else {
                ROTATE_IMAGE = true;
                try {
                    ROTATION_ANGLE = Integer.parseInt(rotation);
                } catch (NumberFormatException e) {
                    ROTATION_ANGLE = 0;
                }
            }
            
            // Legacy settings for backward compatibility
            ADD_EXIF_ATTRIBUTES = settings.fakeCameraAddExifAttributes();
            ADD_SPOOFED_LOCATION = settings.fakeCameraAddSpoofedLocation();
            
            // Additional settings from raw config (not in new organized list)
            USE_RANDOM_IMAGE = cfg.optBoolean("UseRandomImage", false);
            PRESERVE_ASPECT_RATIO = cfg.optBoolean("PreserveAspectRatio", true);
            CENTER_IMAGE = cfg.optBoolean("CenterImage", true);
            FILL_IMAGE = cfg.optBoolean("FillImage", false);
            HOOK_LOW_LEVEL_APIS = cfg.optBoolean("HookLowLevelAPIs", false);
            SYSTEM_CAMERA_WORKAROUND = cfg.optBoolean("SystemCameraWorkaround", false);
            
            // Load multiple image paths if available
            if (cfg.has("FakeImagePaths")) {
                org.json.JSONArray pathsArray = cfg.optJSONArray("FakeImagePaths");
                if (pathsArray != null) {
                    FAKE_IMAGE_PATHS = new String[pathsArray.length()];
                    for (int i = 0; i < pathsArray.length(); i++) {
                        FAKE_IMAGE_PATHS[i] = pathsArray.optString(i, "fake_camera.jpg");
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Cannot read cloner.json – hook disabled", t);
            ENABLED = false;
        }
    }

    /* ---------- 2. Load strings properties ---------- */
    private void loadStringsProperties(Context ctx) {
        sStringsProperties = new Properties();
        
        // Set default values first
        sStringsProperties.setProperty("fake_camera_title", "Fake Camera");
        sStringsProperties.setProperty("fake_camera_text", "Tap to change image");
        sStringsProperties.setProperty("fake_camera_active", "Camera Active");
        sStringsProperties.setProperty("fake_camera_inactive", "Camera Inactive");
        sStringsProperties.setProperty("fake_camera_notification_text", "Fake camera is active. Tap to change the image.");
        sStringsProperties.setProperty("action_rotate", "Rotate");
        sStringsProperties.setProperty("action_flip", "Flip");
        sStringsProperties.setProperty("action_scale_fill", "Fill");
        sStringsProperties.setProperty("action_scale_fit", "Fit");
        sStringsProperties.setProperty("toast_image_rotated", "Image rotated");
        sStringsProperties.setProperty("toast_image_flipped", "Image flipped");
        sStringsProperties.setProperty("toast_scale_fill", "Scale: Fill Screen");
        sStringsProperties.setProperty("toast_scale_fit", "Scale: Fit Image");
        sStringsProperties.setProperty("toast_image_set", "Fake image set successfully");
        sStringsProperties.setProperty("error_no_image", "No fake image available");
        sStringsProperties.setProperty("error_load_failed", "Failed to load fake image");
        
        // Try to load from assets (will override defaults if file exists)
        try {
            InputStream is = ctx.getAssets().open("strings.properties");
            sStringsProperties.load(is);
            is.close();
            Log.d(TAG, "Loaded strings.properties from assets");
        } catch (IOException e) {
            Log.d(TAG, "strings.properties not found in assets, using defaults");
        }
    }

    /* ---------- 3. Load fake images ---------- */
    private void loadFakeImages(Context ctx) throws IOException {
        try {
            // Try to load multiple images first
            if (FAKE_IMAGE_PATHS != null && FAKE_IMAGE_PATHS.length > 0) {
                loadMultipleFakeImages(ctx);
            } else {
                // Fallback to single image
                loadSingleFakeImage(ctx);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load fake images, using fallback", e);
            createFallbackImage();
        }
    }

    private void loadMultipleFakeImages(Context ctx) throws IOException {
        sFakeBitmaps.clear();
        
        for (String path : FAKE_IMAGE_PATHS) {
            try (InputStream is = ctx.getAssets().open(path)) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) {
                    sFakeBitmaps.add(applyImageTransformations(bitmap));
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to load image: " + path, e);
            }
        }
        
        if (!sFakeBitmaps.isEmpty()) {
            sFakeBitmap = sFakeBitmaps.get(0);
            sFakeJpegData = bitmapToJpeg(sFakeBitmap);
        } else {
            throw new IOException("No valid images found");
        }
    }

    private void loadSingleFakeImage(Context ctx) throws IOException {
        try (InputStream is = ctx.getAssets().open(FAKE_IMAGE_PATH)) {
            sFakeBitmap = BitmapFactory.decodeStream(is);
            if (sFakeBitmap == null) {
                throw new IOException("Failed to decode bitmap from assets: " + FAKE_IMAGE_PATH);
            }

            // Apply transformations if needed
            sFakeBitmap = applyImageTransformations(sFakeBitmap);

            // Convert to JPEG for still captures
            sFakeJpegData = bitmapToJpeg(sFakeBitmap);

            Log.d(TAG, "Loaded fake image: " + sFakeBitmap.getWidth() + "x" + sFakeBitmap.getHeight());
        }
    }

    private void createFallbackImage() {
        // Create a simple colored bitmap as fallback
        sFakeBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sFakeBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        canvas.drawRect(0, 0, sFakeBitmap.getWidth(), sFakeBitmap.getHeight(), paint);
        
        // Add text
        paint.setColor(Color.WHITE);
        paint.setTextSize(32);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("FAKE CAMERA", sFakeBitmap.getWidth()/2, sFakeBitmap.getHeight()/2, paint);
        
        sFakeJpegData = bitmapToJpeg(sFakeBitmap);
        Log.d(TAG, "Created fallback image");
    }

    /* ---------- 4. Hook Camera APIs ---------- */
    private void hookCameraAPIs() throws Exception {
        // Hook Camera1 APIs
        hookCamera1APIs();

        // Hook Camera2 APIs if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            hookCamera2APIs();
        }

        // Hook ImageReader APIs (Unconditionally, as CameraX relies on them)
        hookImageReaderAPIs();
        
        // Hook Image Plane APIs for additional coverage
        hookImagePlaneAPIs();

        // Hook ContentResolver APIs if workaround is enabled
        if (OPEN_STREAM_WORKAROUND) {
            hookContentResolverAPIs();
        }

        // Hook Video Recording APIs
        hookVideoRecordingAPIs();
        
        // Hook SurfaceTexture for preview
        hookSurfaceTextureAPIs();
        
        // Hook Bitmap-based camera capture APIs
        hookBitmapCaptureAPIs();
        
        // Install face detection bypass hooks
        try {
            FaceDetectionBypassHook.install(sContext);
            Log.i(TAG, "Face detection bypass hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to install face detection bypass hooks", t);
        }
        
        // Install liveness session manager hooks
        try {
            LivenessSessionManager.install(sContext);
            Log.i(TAG, "Liveness session manager hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to install liveness session manager hooks", t);
        }
        
        // Install Prooface integration hooks for SumSub SDK support
        try {
            ProofaceIntegrationHook.install(sContext);
            Log.i(TAG, "Prooface integration hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to install Prooface integration hooks", t);
        }
    }

    /* ---------- Camera1 API Hooking ---------- */
    private void hookCamera1APIs() throws Exception {
        hookCamera1PreviewCallbacks();

        // Hook Camera.startPreview
        Method startPreviewMethod = Camera.class.getMethod("startPreview");
        Hooking.pineHook(startPreviewMethod, new MethodHook() {
            @Override
            public void beforeCall(Pine.CallFrame callFrame) {
                Log.d(TAG, "Camera.startPreview hooked");
            }
        });

        // Hook Camera.release
        Method releaseMethod = Camera.class.getMethod("release");
        Hooking.pineHook(releaseMethod, new MethodHook() {
            @Override
            public void beforeCall(Pine.CallFrame callFrame) {
                Log.d(TAG, "Camera.release hooked");
            }
        });

        // Hook Camera.takePicture
        Method takePictureMethod = Camera.class.getMethod("takePicture",
                ShutterCallback.class, PictureCallback.class, PictureCallback.class);

        Hooking.pineHook(takePictureMethod, new MethodHook() {
            @Override
            public void beforeCall(Pine.CallFrame callFrame) {
                Camera camera = (Camera) callFrame.thisObject;
                ShutterCallback shutterCallback = (ShutterCallback) callFrame.args[0];
                PictureCallback jpegCallback = (PictureCallback) callFrame.args[2];

                Log.d(TAG, "Camera.takePicture hooked");

                // Get camera resolution
                Point cameraResolution = null;
                if (RESIZE_IMAGE) {
                    cameraResolution = getCameraResolution(camera);
                }

                // Process the fake image with the correct resolution
                byte[] jpegData;
                Bitmap currentBitmap = getCurrentFakeImage();
                
                if (RESIZE_IMAGE && cameraResolution != null) {
                    ImageUtils.ScaleType scaleType = FILL_IMAGE ? ImageUtils.ScaleType.CENTER_CROP : ImageUtils.ScaleType.CENTER_INSIDE;
                    Bitmap resizedBitmap = ImageUtils.resizeBitmap(currentBitmap, cameraResolution.x, cameraResolution.y, scaleType);
                    if (resizedBitmap != null) {
                        jpegData = bitmapToJpeg(resizedBitmap);
                    } else {
                        // Fallback if resize failed
                        Bitmap fallback = createFallbackImage(cameraResolution.x, cameraResolution.y);
                        jpegData = bitmapToJpeg(fallback);
                    }
                } else {
                    jpegData = bitmapToJpeg(currentBitmap);
                }

                // Save to temp file to add EXIF
                File tempFile = saveToTempFile(jpegData);
                if (tempFile != null) {
                    // Add attributes
                    int width = (RESIZE_IMAGE && cameraResolution != null) ? cameraResolution.x : currentBitmap.getWidth();
                    int height = (RESIZE_IMAGE && cameraResolution != null) ? cameraResolution.y : currentBitmap.getHeight();

                    setGeneralExifAttributes(tempFile, width, height);
                    if (ADD_SPOOFED_LOCATION) {
                        setLocationExifAttributes(tempFile);
                    }

                    // Read back
                    try (FileInputStream fis = new FileInputStream(tempFile)) {
                         byte[] newData = new byte[(int) tempFile.length()];
                         fis.read(newData);
                         jpegData = newData;
                    } catch (IOException e) {
                        Log.w(TAG, "Failed to read back temp file", e);
                    }
                    tempFile.delete();
                }

                final byte[] finalJpegData = jpegData;

                // Call the callbacks with our fake image
                sHandler.post(() -> {
                    try {
                        if (shutterCallback != null) {
                            shutterCallback.onShutter();
                        }

                        if (jpegCallback != null) {
                            jpegCallback.onPictureTaken(finalJpegData, camera);
                        }

                        sPictureTakenMillis = System.currentTimeMillis();
                        sPictureTakenNanos = System.nanoTime();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in takePicture callback", e);
                    }
                });

                // Skip the original method
                callFrame.setResult(null);
            }
        });
    }

    private void hookCamera1PreviewCallbacks() throws Exception {
        Method setPreviewCallbackMethod = Camera.class.getMethod("setPreviewCallback", PreviewCallback.class);
        Hooking.pineHook(setPreviewCallbackMethod, new MethodHook() {
            @Override
            public void beforeCall(Pine.CallFrame callFrame) {
                Camera camera = (Camera) callFrame.thisObject;
                PreviewCallback original = (PreviewCallback) callFrame.args[0];

                if (original == null) return;

                callFrame.args[0] = (PreviewCallback) (data, cam) -> {
                    byte[] fakeData = getFakePreviewData(cam != null ? cam : camera);
                    if (original != null) {
                        original.onPreviewFrame(fakeData != null ? fakeData : data, cam != null ? cam : camera);
                    }
                };
            }
        });

        Method setOneShotPreviewCallbackMethod = Camera.class.getMethod("setOneShotPreviewCallback", PreviewCallback.class);
        Hooking.pineHook(setOneShotPreviewCallbackMethod, new MethodHook() {
            @Override
            public void beforeCall(Pine.CallFrame callFrame) {
                Camera camera = (Camera) callFrame.thisObject;
                PreviewCallback original = (PreviewCallback) callFrame.args[0];

                if (original == null) return;

                callFrame.args[0] = (PreviewCallback) (data, cam) -> {
                    byte[] fakeData = getFakePreviewData(cam != null ? cam : camera);
                    if (original != null) {
                        original.onPreviewFrame(fakeData != null ? fakeData : data, cam != null ? cam : camera);
                    }
                };
            }
        });

        Method setPreviewCallbackWithBufferMethod = Camera.class.getMethod("setPreviewCallbackWithBuffer", PreviewCallback.class);
        Hooking.pineHook(setPreviewCallbackWithBufferMethod, new MethodHook() {
            @Override
            public void beforeCall(Pine.CallFrame callFrame) {
                Camera camera = (Camera) callFrame.thisObject;
                PreviewCallback original = (PreviewCallback) callFrame.args[0];

                if (original == null) return;

                callFrame.args[0] = (PreviewCallback) (data, cam) -> {
                    byte[] fakeData = getFakePreviewData(cam != null ? cam : camera);
                    if (fakeData != null && data != null) {
                        System.arraycopy(fakeData, 0, data, 0, Math.min(fakeData.length, data.length));
                    }

                    if (original != null) {
                        original.onPreviewFrame(data, cam != null ? cam : camera);
                    }
                };
            }
        });
    }

    private File saveToTempFile(byte[] data) {
        try {
            File temp = File.createTempFile("fake_cam_", ".jpg", sContext.getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(temp)) {
                fos.write(data);
            }
            return temp;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save temp file", e);
            return null;
        }
    }

    private byte[] getFakePreviewData(Camera camera) {
        if (!isFakeCameraActive() || camera == null) return null;

        synchronized (sProcessingLock) {
            try {
                Camera.Parameters parameters = camera.getParameters();
                Camera.Size previewSize = parameters != null ? parameters.getPreviewSize() : null;
                if (previewSize == null) return null;

                int width = previewSize.width;
                int height = previewSize.height;

                Bitmap currentBitmap = getCurrentFakeImage();
                ImageUtils.ScaleType scaleType = FILL_IMAGE ? ImageUtils.ScaleType.CENTER_CROP : ImageUtils.ScaleType.CENTER_INSIDE;
                Bitmap resizedBitmap = resizeAndAdjustBitmap(currentBitmap, width, height, scaleType);

                if (resizedBitmap == null) {
                    resizedBitmap = createFallbackImage(width, height);
                }

                byte[] nv21 = ImageUtils.bitmapToNV21(resizedBitmap, sCachedPixelBuffer, sCachedNV21Buffer);
                sCachedNV21Buffer = nv21;

                if (resizedBitmap != currentBitmap && resizedBitmap != sIntermediateBitmap) {
                    resizedBitmap.recycle();
                }

                return nv21;
            } catch (Throwable t) {
                Log.e(TAG, "Failed to create fake preview data", t);
                return null;
            }
        }
    }

    /* ---------- Camera2 API Hooking ---------- */
    private void hookCamera2APIs() throws Exception {
        String methodName = "openCamera";
        Class<?>[] paramTypes;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            paramTypes = new Class<?>[]{String.class, Executor.class, CameraDevice.StateCallback.class};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paramTypes = new Class<?>[]{String.class, Executor.class, CameraDevice.StateCallback.class};
        } else {
            paramTypes = new Class<?>[]{String.class, CameraDevice.StateCallback.class, Handler.class};
        }

        Method openCameraMethod = null;
        try {
            openCameraMethod = CameraManager.class.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    openCameraMethod = CameraManager.class.getMethod(methodName, String.class, CameraDevice.StateCallback.class, Executor.class);
                } else {
                    openCameraMethod = CameraManager.class.getMethod(methodName, String.class, CameraDevice.StateCallback.class, Handler.class);
                }
            } catch (NoSuchMethodException e2) {
                Log.e(TAG, "Failed to find any openCamera method signature.", e2);
                return;
            }
        }

        Hooking.pineHook(openCameraMethod, new MethodHook() {
            @Override
            public void beforeCall(Pine.CallFrame callFrame) {
                String cameraId = (String) callFrame.args[0];
                Log.d(TAG, "CameraManager.openCamera hooked for camera: " + cameraId);
            }
        });

        try {
            Class<?> cameraDeviceImplClass = Class.forName("android.hardware.camera2.impl.CameraDeviceImpl");
            Method closeMethod = cameraDeviceImplClass.getMethod("close");
            Hooking.pineHook(closeMethod, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    Log.d(TAG, "CameraDeviceImpl.close hooked");
                }
            });
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "CameraDeviceImpl class not found, skipping hook");
        }
    }

    /* ---------- ImageReader API Hooking ---------- */
    private void hookImageReaderAPIs() throws Exception {
        // Simple throttler for logs
        final long[] lastLogTime = {0};
        final long LOG_INTERVAL_MS = 2000;

        Method acquireLatestImageMethod = ImageReader.class.getMethod("acquireLatestImage");
        Hooking.pineHook(acquireLatestImageMethod, new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                long now = System.currentTimeMillis();
                if (now - lastLogTime[0] > LOG_INTERVAL_MS) {
                    Log.d(TAG, "ImageReader.acquireLatestImage hooked (afterCall)");
                    lastLogTime[0] = now;
                }
                Image realImage = (Image) callFrame.getResult();
                if (realImage != null) {
                    overwriteImageWithFakeData(realImage);
                }
            }
        });

        Method acquireNextImageMethod = ImageReader.class.getMethod("acquireNextImage");
        Hooking.pineHook(acquireNextImageMethod, new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                long now = System.currentTimeMillis();
                if (now - lastLogTime[0] > LOG_INTERVAL_MS) {
                    Log.d(TAG, "ImageReader.acquireNextImage hooked (afterCall)");
                    lastLogTime[0] = now;
                }
                Image realImage = (Image) callFrame.getResult();
                if (realImage != null) {
                    overwriteImageWithFakeData(realImage);
                }
            }
        });
        
        // Hook ImageReader.newInstance to log what formats are being requested
        try {
            Method newInstanceMethod = ImageReader.class.getMethod("newInstance", 
                    int.class, int.class, int.class, int.class);
            Hooking.pineHook(newInstanceMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    int width = (int) callFrame.args[0];
                    int height = (int) callFrame.args[1];
                    int format = (int) callFrame.args[2];
                    int maxImages = (int) callFrame.args[3];
                    Log.d(TAG, "ImageReader.newInstance: " + width + "x" + height + 
                               ", format=" + ImageUtils.getFormatName(format) + 
                               ", maxImages=" + maxImages);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook ImageReader.newInstance", e);
        }
        
        // Hook additional Image acquisition methods if available (API 29+)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // ImageReader.acquireNextImageNoThrowISE (API 29+)
                Method acquireNextImageNoThrowMethod = ImageReader.class.getMethod("acquireNextImageNoThrowISE");
                Hooking.pineHook(acquireNextImageNoThrowMethod, new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame callFrame) {
                        // Log.d(TAG, "ImageReader.acquireNextImageNoThrowISE hooked (afterCall)");
                        Image realImage = (Image) callFrame.getResult();
                        if (realImage != null) {
                            overwriteImageWithFakeData(realImage);
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.d(TAG, "acquireNextImageNoThrowISE not available on this API level");
        }
    }
    
    /* ---------- Image Plane API Hooking ---------- */
    private void hookImagePlaneAPIs() throws Exception {
        // Hook Image.getPlanes to intercept plane data access
        try {
            Method getPlanesMethod = Image.class.getMethod("getPlanes");
            Hooking.pineHook(getPlanesMethod, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    Image image = (Image) callFrame.thisObject;
                    if (image != null) {
                        int format = image.getFormat();
                        Log.d(TAG, "Image.getPlanes called for format: " + 
                                   ImageUtils.getFormatName(format));
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook Image.getPlanes", e);
        }
    }

    /* ---------- ContentResolver API Hooking ---------- */
    private void hookContentResolverAPIs() throws Exception {
        Method openFileDescriptorMethod = ContentResolver.class.getMethod("openFileDescriptor",
                Uri.class, String.class, CancellationSignal.class);

        Hooking.pineHook(openFileDescriptorMethod, new MethodHook() {
            @Override
            public void beforeCall(Pine.CallFrame callFrame) {
                Uri uri = (Uri) callFrame.args[0];
                Log.d(TAG, "ContentResolver.openFileDescriptor hooked for URI: " + uri);
            }

            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                if (!OPEN_STREAM_WORKAROUND || !isFakeCameraActive()) return;

                Uri uri = (Uri) callFrame.args[0];
                String mode = (String) callFrame.args[1];

                if (mode != null && mode.contains("w") && isCameraImageUri(uri)) {
                    Object result = callFrame.getResult();
                    if (result instanceof android.os.ParcelFileDescriptor) {
                        try {
                            android.os.ParcelFileDescriptor pfd = (android.os.ParcelFileDescriptor) result;
                            if (pfd != null) {
                                FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
                                byte[] data = getFakeJpegData();
                                if (data != null) {
                                    fos.write(data);
                                    fos.flush();
                                    Log.d(TAG, "Open Stream Workaround: Injected " + data.length + " bytes into " + uri);
                                }
                                // Do not close fos, as it would close the PFD which the app needs.
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to inject fake image", e);
                        }
                    }
                }
            }
        });

        Method openInputStreamMethod = ContentResolver.class.getMethod("openInputStream", Uri.class);
        Hooking.pineHook(openInputStreamMethod, new MethodHook() {
            @Override
            public void beforeCall(Pine.CallFrame callFrame) {
                Uri uri = (Uri) callFrame.args[0];
                Log.d(TAG, "ContentResolver.openInputStream hooked for URI: " + uri);
                
                if (isCameraImageUri(uri)) {
                    Log.d(TAG, "Camera image URI detected: " + uri);
                }
            }
        });
    }

    /* ---------- Video Recording API Hooking ---------- */
    private void hookVideoRecordingAPIs() throws Exception {
        try {
            Method startMethod = MediaRecorder.class.getMethod("start");
            Hooking.pineHook(startMethod, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    Log.d(TAG, "MediaRecorder.start hooked - starting video recording");
                }
            });
            
            Method stopMethod = MediaRecorder.class.getMethod("stop");
            Hooking.pineHook(stopMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    Log.d(TAG, "MediaRecorder.stop hooked - video recording stopped");
                }
            });
            
            Method setOutputFileMethod = MediaRecorder.class.getMethod("setOutputFile", String.class);
            Hooking.pineHook(setOutputFileMethod, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    String path = (String) callFrame.args[0];
                    Log.d(TAG, "MediaRecorder.setOutputFile hooked: " + path);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook MediaRecorder APIs", e);
        }
    }

    /* ---------- SurfaceTexture API Hooking ---------- */
    private void hookSurfaceTextureAPIs() throws Exception {
        try {
            Class<?> surfaceTextureClass = Class.forName("android.graphics.SurfaceTexture");
            
            Method updateTexImageMethod = surfaceTextureClass.getMethod("updateTexImage");
            Hooking.pineHook(updateTexImageMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    if (sSystemCameraWorkaroundActive) {
                        Log.d(TAG, "System camera workaround: Simulating successful updateTexImage");
                    }
                }
            });
            
            Method getTransformMatrixMethod = surfaceTextureClass.getMethod("getTransformMatrix", float[].class);
            Hooking.pineHook(getTransformMatrixMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    if (sSystemCameraWorkaroundActive) {
                        float[] matrix = (float[]) callFrame.args[0];
                        if (matrix != null && matrix.length >= 16) {
                            for (int i = 0; i < 16; i++) {
                                matrix[i] = (i % 5 == 0) ? 1.0f : 0.0f;
                            }
                            Log.d(TAG, "System camera workaround: Providing default transform matrix");
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook SurfaceTexture APIs", e);
        }
    }

    /* ---------- Bitmap Capture API Hooking ---------- */
    private void hookBitmapCaptureAPIs() throws Exception {
        // Hook PixelCopy APIs if available (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Class<?> pixelCopyClass = Class.forName("android.view.PixelCopy");
                // Note: PixelCopy.request has multiple overloads, we hook the most common ones
                Log.d(TAG, "PixelCopy class found, monitoring for camera captures");
            } catch (ClassNotFoundException e) {
                Log.d(TAG, "PixelCopy not available");
            }
        }
        
        // Hook BitmapFactory.decodeByteArray to intercept JPEG decoding from camera
        try {
            Method decodeByteArrayMethod = BitmapFactory.class.getMethod("decodeByteArray", 
                    byte[].class, int.class, int.class);
            Hooking.pineHook(decodeByteArrayMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    // Only intercept during active camera capture
                    if (isFakeCameraActive()) {
                        byte[] data = (byte[]) callFrame.args[0];
                        // Check if this looks like camera JPEG data
                        if (data != null && data.length > 2 && 
                            data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
                            Log.d(TAG, "BitmapFactory.decodeByteArray intercepted (JPEG data)");
                            // Optionally replace with fake bitmap
                            Bitmap fakeBitmap = getCurrentFakeImage();
                            if (fakeBitmap != null) {
                                Bitmap result = (Bitmap) callFrame.getResult();
                                if (result != null) {
                                    // If dimensions match roughly, consider replacing
                                    float widthRatio = (float) result.getWidth() / fakeBitmap.getWidth();
                                    float heightRatio = (float) result.getHeight() / fakeBitmap.getHeight();
                                    if (widthRatio > 0.5f && widthRatio < 2.0f && 
                                        heightRatio > 0.5f && heightRatio < 2.0f) {
                                        Log.d(TAG, "Camera JPEG decode detected, fake bitmap available");
                                    }
                                }
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook BitmapFactory.decodeByteArray", e);
        }
    }
    
    /* ---------- Low-Level API Hooking ---------- */
    private void hookLowLevelCameraAPIs() throws Exception {
        try {
            Method nativeSetupMethod = Camera.class.getDeclaredMethod("native_setup", Object.class);
            Hooking.pineHook(nativeSetupMethod, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    Log.d(TAG, "Camera.native_setup hooked");
                }
            });
            
            Class<?> cameraServiceClass = Class.forName("android.hardware.camera2.CameraManager$CameraServiceBinderDecorator");
            Method binderDecorateMethod = cameraServiceClass.getMethod("decorate", Object.class);
            Hooking.pineHook(binderDecorateMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    Log.d(TAG, "CameraService binder decorate hooked");
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook low-level camera APIs", e);
        }
    }

    /* ---------- Close Stream Workaround ---------- */
    private void hookFileOutputStream() {
        try {
            Method closeMethod = FileOutputStream.class.getMethod("close");
            Hooking.pineHook(closeMethod, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    if (!CLOSE_STREAM_WORKAROUND || !isFakeCameraActive()) return;

                    FileOutputStream fos = (FileOutputStream) callFrame.thisObject;
                    if (isCameraFileDescriptor(fos)) {
                         Log.d(TAG, "Close Stream Workaround: Intercepting close()");
                         try {
                             byte[] data = getFakeJpegData();
                             if (data != null) {
                                 fos.write(data);
                                 fos.flush();
                                 Log.d(TAG, "Injected " + data.length + " bytes before closing stream");
                             }
                         } catch (IOException e) {
                             Log.e(TAG, "Failed to write fake data in close hook", e);
                         }
                    }
                }
            });
            Log.d(TAG, "Hooked FileOutputStream.close");
        } catch (Exception e) {
            Log.e(TAG, "Failed to hook FileOutputStream.close", e);
        }
    }

    /* ---------- System Camera Workaround ---------- */
    private void activateSystemCameraWorkaround(Context context) {
        sSystemCameraWorkaroundActive = true;
        Log.i(TAG, "System camera workaround activated");
        
        try {
            Class<?> packageManagerClass = Class.forName("android.content.pm.PackageManager");
            Method hasSystemFeatureMethod = packageManagerClass.getMethod("hasSystemFeature", String.class);
            
            Hooking.pineHook(hasSystemFeatureMethod, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    String feature = (String) callFrame.args[0];
                    if (feature != null && 
                        (feature.startsWith("android.hardware.camera") || 
                         feature.contains("camera"))) {
                        Log.d(TAG, "PackageManager.hasSystemFeature intercepted for: " + feature);
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook PackageManager for system camera workaround", e);
        }
        
        try {
            Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
            Method getMemoryInfoMethod = activityManagerClass.getMethod("getMemoryInfo", 
                Class.forName("android.app.ActivityManager$MemoryInfo"));
            
            Hooking.pineHook(getMemoryInfoMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    Log.d(TAG, "ActivityManager.getMemoryInfo intercepted");
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook ActivityManager for system camera workaround", e);
        }
    }

    /* ---------- Helper Methods ---------- */

    private void overwriteImageWithFakeData(Image image) {
        synchronized (sProcessingLock) {
            try {
                int format = image.getFormat();
                int width = image.getWidth();
                int height = image.getHeight();
                Image.Plane[] planes = image.getPlanes();

                if (planes == null || planes.length == 0) {
                    // Log.e(TAG, "Image has no planes to overwrite.");
                    return;
                }
                
                // Notify face detection bypass hook of frame dimensions
                try {
                    FaceDetectionBypassHook.updateFrameDimensions(width, height);
                } catch (Throwable t) {
                    // Hook may not be installed, ignore
                }
                
                // Check liveness session state for frame delivery timing
                try {
                    if (!LivenessSessionManager.shouldDeliverFrame("camera")) {
                        // Frame should be skipped (liveness session not ready)
                        return;
                    }
                } catch (Throwable t) {
                    // Manager may not be installed, proceed with frame
                }

                // Special handling for YUV_420_888 to use caching (performance optimization)
                if (format == ImageUtils.FORMAT_YUV_420_888 || format == ImageUtils.FORMAT_NV21) {
                    Bitmap currentBitmap = getCurrentFakeImage();

                    // If random image is enabled, we cannot cache effectively because the image changes frequently
                    // So we skip cache to ensure randomness works
                    boolean useCache = !USE_RANDOM_IMAGE;

                    // Check if cache is valid
                    boolean cacheValid = useCache &&
                            sCachedYuvData != null &&
                            sCachedWidth == width &&
                            sCachedHeight == height &&
                            sCachedFormat == format;

                    if (!cacheValid) {
                        // Resizing is needed, update cache or temporary buffer
                        // Log.d(TAG, "Processing frame: " + width + "x" + height);

                        ImageUtils.ScaleType scaleType = FILL_IMAGE ? ImageUtils.ScaleType.CENTER_CROP : ImageUtils.ScaleType.CENTER_INSIDE;
                        Bitmap resizedBitmap = resizeAndAdjustBitmap(currentBitmap, width, height, scaleType);

                        if (resizedBitmap != null) {
                            try {
                                if (useCache) {
                                    // Update static cache
                                    sCachedYuvData = ImageUtils.bitmapToNV21(resizedBitmap, sCachedPixelBuffer, sCachedYuvData);
                                    // Update reference to buffers in case they were reallocated
                                    // (bitmapToNV21 returns the buffer it used)
                                    sCachedNV21Buffer = sCachedYuvData;

                                    sCachedWidth = width;
                                    sCachedHeight = height;
                                    sCachedFormat = format;
                                } else {
                                    // Random mode: use temporary buffers but don't save to static cache
                                    // We still use the static buffers to avoid allocation, but we don't mark cache as valid
                                    byte[] data = ImageUtils.bitmapToNV21(resizedBitmap, sCachedPixelBuffer, sCachedNV21Buffer);

                                    // Update our buffer references
                                    sCachedNV21Buffer = data;

                                    // Write directly here for random mode
                                    ImageUtils.writeYuvToPlanes(data, width, height, planes);
                                    return;
                                }
                            } finally {
                                // Do NOT recycle sIntermediateBitmap here if it's the reusable one
                                if (resizedBitmap != currentBitmap && resizedBitmap != sIntermediateBitmap) {
                                    resizedBitmap.recycle();
                                }
                            }
                        }
                    }

                    if (useCache && sCachedYuvData != null) {
                        ImageUtils.writeYuvToPlanes(sCachedYuvData, width, height, planes);
                        return;
                    }
                }

                // Fallback for other formats
                // Check if format is supported using ImageUtils
                if (!ImageUtils.isFormatSupported(format)) {
                    // Try to handle unsupported formats with fallback
                    // Log.w(TAG, "Attempting fallback for unsupported image format: " + ImageUtils.getFormatName(format));

                    if (planes.length > 0) {
                        Bitmap currentBitmap = getCurrentFakeImage();
                        ImageUtils.ScaleType scaleType = FILL_IMAGE ? ImageUtils.ScaleType.CENTER_CROP : ImageUtils.ScaleType.CENTER_INSIDE;
                        Bitmap resizedBitmap = resizeAndAdjustBitmap(currentBitmap, width, height, scaleType);

                        try {
                            // Try RGBA fallback for single-plane formats
                            if (planes.length == 1) {
                                boolean success = ImageUtils.writeRGBAToPlanes(resizedBitmap, planes, width, height);
                                if (success) return;
                            }

                            // Try YUV fallback for multi-plane formats
                            if (planes.length >= 3) {
                                try {
                                    // Use our reusable buffers for fallback too
                                    byte[] yuvData = ImageUtils.bitmapToNV21(resizedBitmap, sCachedPixelBuffer, sCachedNV21Buffer);
                                    sCachedNV21Buffer = yuvData; // Save if reallocated

                                    ImageUtils.writeYuvToPlanes(yuvData, width, height, planes);
                                    return;
                                } catch (Exception e) {
                                    Log.w(TAG, "YUV fallback failed", e);
                                }
                            }
                        } finally {
                            if (resizedBitmap != null && resizedBitmap != currentBitmap && resizedBitmap != sIntermediateBitmap) {
                                resizedBitmap.recycle();
                            }
                        }
                    }

                    return;
                }

                Bitmap currentBitmap = getCurrentFakeImage();
                ImageUtils.ScaleType scaleType = FILL_IMAGE ? ImageUtils.ScaleType.CENTER_CROP : ImageUtils.ScaleType.CENTER_INSIDE;
                Bitmap resizedBitmap = resizeAndAdjustBitmap(currentBitmap, width, height, scaleType);

                if (resizedBitmap == null) {
                    resizedBitmap = createFallbackImage(width, height);
                }

                // Use the unified ImageUtils method for all supported formats
                boolean success = ImageUtils.writeFakeDataToImage(image, resizedBitmap);

                // IMPORTANT: Recycle the resized bitmap to prevent memory leaks,
                // BUT do not recycle if it is our reusable buffer
                if (resizedBitmap != currentBitmap && resizedBitmap != sIntermediateBitmap) {
                    resizedBitmap.recycle();
                }

                if (!success) {
                    Log.e(TAG, "Failed to overwrite Image buffer for format: " +
                            ImageUtils.getFormatName(format));
                }
            } catch (Throwable t) {
                // Catch OOM or other runtime exceptions to prevent app crash
                Log.e(TAG, "Error in overwriteImageWithFakeData", t);
            }
        }
    }

    private static boolean isCameraImageUri(Uri uri) {
        if (uri == null) return false;
        String uriString = uri.toString().toLowerCase();
        return uriString.contains("dcim") || uriString.contains("camera") ||
                uriString.contains("pictures") || uriString.contains(".jpg") ||
                uriString.contains(".jpeg") || uriString.contains("photos");
    }

    /**
     * Custom resize method that applies global Zoom and Translation settings.
     * Uses a reusable bitmap buffer to avoid memory churn.
     */
    private Bitmap resizeAndAdjustBitmap(Bitmap bitmap, int width, int height, ImageUtils.ScaleType scaleType) {
        if (bitmap == null || width <= 0 || height <= 0) return null;

        // 1. Prepare target bitmap (reuse if possible)
        if (sIntermediateBitmap == null || sIntermediateBitmap.getWidth() != width || sIntermediateBitmap.getHeight() != height) {
            if (sIntermediateBitmap != null) {
                sIntermediateBitmap.recycle();
                sIntermediateBitmap = null;
            }
            try {
                sIntermediateBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "OOM creating intermediate bitmap: " + width + "x" + height);
                // Fallback: try to GC and recreate, or return null
                System.gc();
                return null;
            }
        }

        // Clear the bitmap (important if we reuse it and draw smaller image or transparency)
        sIntermediateBitmap.eraseColor(Color.TRANSPARENT);

        Canvas canvas = new Canvas(sIntermediateBitmap);

        // 2. Calculate base scale (Fit or Fill)
        float scaleX = (float) width / bitmap.getWidth();
        float scaleY = (float) height / bitmap.getHeight();

        if (scaleType == ImageUtils.ScaleType.CENTER_CROP) {
            float s = Math.max(scaleX, scaleY);
            scaleX = s;
            scaleY = s;
        } else if (scaleType == ImageUtils.ScaleType.CENTER_INSIDE) {
            float s = Math.min(scaleX, scaleY);
            scaleX = s;
            scaleY = s;
        }

        // 3. Setup Matrix
        Matrix matrix = new Matrix();

        // Center the image first
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;

        matrix.postScale(scaleX, scaleY);
        matrix.postTranslate(centerX - (bitmap.getWidth() * scaleX / 2.0f),
                             centerY - (bitmap.getHeight() * scaleY / 2.0f));

        // 4. Apply User Adjustments (Zoom & Pan)
        // Zoom around center
        matrix.postScale(sZoomLevel, sZoomLevel, centerX, centerY);
        // Translate
        matrix.postTranslate(sTranslateX, sTranslateY);

        // 5. Draw
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bitmap, matrix, paint);

        return sIntermediateBitmap;
    }

    private Point getCameraResolution(Camera camera) {
        try {
            Camera.Size pictureSize = camera.getParameters().getPictureSize();
            int width = pictureSize.width;
            int height = pictureSize.height;
            Log.d(TAG, "Camera1 resolution: " + width + "x" + height);
            return adjustCameraResolution(width, height);
        } catch (Exception e) {
            Log.e(TAG, "Error getting Camera1 resolution", e);
            return null;
        }
    }

    private Point adjustCameraResolution(int width, int height) {
        Canvas canvas = new Canvas();
        int maxBitmapWidth = canvas.getMaximumBitmapWidth();
        int maxBitmapHeight = canvas.getMaximumBitmapHeight();
        int maxSize = Math.min(maxBitmapWidth, maxBitmapHeight);

        Log.d(TAG, "Adjusting resolution: " + width + "x" + height +
                ", max size: " + maxSize);

        if (width > maxSize || height > maxSize) {
            float ratio = (width > height) ? (float) maxSize / width : (float) maxSize / height;
            width = (int) (width * ratio);
            height = (int) (height * ratio);
        }

        Log.d(TAG, "Adjusted resolution: " + width + "x" + height);
        return new Point(width, height);
    }

    private Bitmap createFallbackImage(int width, int height) {
        Bitmap fallback = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fallback);
        Paint paint = new Paint();
        paint.setColor(Color.GRAY);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(20);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("No Image", width / 2, height / 2, paint);
        return fallback;
    }

    /* ---------- Image Processing ---------- */
    private Bitmap applyImageTransformations(Bitmap bitmap) {
        if (!ROTATE_IMAGE && !FLIP_HORIZONTALLY && !RANDOMIZE_IMAGE) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        if (ROTATE_IMAGE && ROTATION_ANGLE != 0) {
            matrix.postRotate(ROTATION_ANGLE);
        }
        if (FLIP_HORIZONTALLY) {
            matrix.postScale(-1.0f, 1.0f);
        }
        Bitmap transformedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        if (RANDOMIZE_IMAGE) {
            transformedBitmap = randomizePicture(transformedBitmap);
        }

        return transformedBitmap;
    }

    private static byte[] bitmapToJpeg(Bitmap bitmap) {
        java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream);
        return stream.toByteArray();
    }

    private Bitmap randomizePicture(Bitmap bitmap) {
        if (!RANDOMIZE_IMAGE) {
            return bitmap;
        }

        float strength = ((float) RANDOMIZE_STRENGTH) / 100.0f;
        Log.d(TAG, "Randomizing picture with strength: " + strength);

        float angle = nextRandomFloat(-5.0f, 5.0f) * strength;

        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap randomizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        return randomizedBitmap;
    }

    private float nextRandomFloat(float min, float max) {
        return min + sRandom.nextFloat() * (max - min);
    }

    /* ---------- EXIF Handling ---------- */
    private void setGeneralExifAttributes(File file, int width, int height) {
        if (!ADD_EXIF_ATTRIBUTES) return;

        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());

            exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER);
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL);
            exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, String.valueOf(width));
            exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, String.valueOf(height));

            String dateTime = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(new Date());
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime);
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime);
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTime);

            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));

            // Additional attributes to make it look real
            // WHITE_BALANCE_AUTO is 0
            exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, "0");
            exif.setAttribute(ExifInterface.TAG_FLASH, "0");

            exif.saveAttributes();
            Log.d(TAG, "Successfully added EXIF attributes to: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error adding EXIF attributes", e);
        }
    }

    private void setLocationExifAttributes(File file) {
        // Since we don't have easy access to the internal SpoofLocation class without reflection,
        // we'll try to find it.
        try {
            Class<?> spoofLocationClass = Class.forName("com.applisto.appcloner.classes.secondary.SpoofLocation");
            Object instance = spoofLocationClass.getDeclaredField("INSTANCE").get(null);

            if (instance != null) {
                Method getLat = spoofLocationClass.getMethod("getSpoofLocationLatitude");
                Method getLon = spoofLocationClass.getMethod("getSpoofLocationLongitude");

                double lat = (double) getLat.invoke(null);
                double lon = (double) getLon.invoke(null);

                ExifInterface exif = new ExifInterface(file.getAbsolutePath());

                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDMS(lat));
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, lat > 0 ? "N" : "S");
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDMS(lon));
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lon > 0 ? "E" : "W");

                exif.saveAttributes();
                Log.i(TAG, "Added spoofed location EXIF: " + lat + ", " + lon);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set location EXIF (SpoofLocation not found or error)", e);
        }
    }

    private static String convertToDMS(double coordinate) {
        coordinate = Math.abs(coordinate);
        int degrees = (int) coordinate;
        coordinate = (coordinate - degrees) * 60;
        int minutes = (int) coordinate;
        coordinate = (coordinate - minutes) * 60;
        int seconds = (int) (coordinate * 1000);

        return degrees + "/1," + minutes + "/1," + seconds + "/1000";
    }

    /* ---------- Utility Methods ---------- */
    private Bitmap getCurrentFakeImage() {
        if (USE_RANDOM_IMAGE && !sFakeBitmaps.isEmpty()) {
            return sFakeBitmaps.get(sRandom.nextInt(sFakeBitmaps.size()));
        } else if (!sFakeBitmaps.isEmpty()) {
            Bitmap image = sFakeBitmaps.get(sCurrentImageIndex);
            sCurrentImageIndex = (sCurrentImageIndex + 1) % sFakeBitmaps.size();
            return image;
        } else {
            return sFakeBitmap;
        }
    }

    private void createFakeVideoFile() {
        Log.d(TAG, "Creating fake video file placeholder");
    }

    private static int getFdInt(java.io.FileDescriptor fd) {
        try {
            java.lang.reflect.Field field = java.io.FileDescriptor.class.getDeclaredField("descriptor");
            field.setAccessible(true);
            return field.getInt(fd);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get FD int", e);
            return -1;
        }
    }

    private static String getPathFromFileDescriptor(java.io.FileDescriptor fd) {
        try {
            int fdInt = getFdInt(fd);
            if (fdInt == -1) return null;

            // On Android, resolving /proc/self/fd/ links usually works via File.getCanonicalPath()
            // or we can try to read the link directly.
            File link = new File("/proc/self/fd/" + fdInt);
            if (Build.VERSION.SDK_INT >= 21) {
                 try {
                     return android.system.Os.readlink(link.getAbsolutePath());
                 } catch (Exception e) {
                     // Fallback
                 }
            }
            return link.getCanonicalPath();
        } catch (Exception e) {
            // Log.w(TAG, "Failed to resolve path for FD", e);
            return null;
        }
    }

    private static boolean isCameraFileDescriptor(Object stream) {
        try {
            if (stream instanceof FileOutputStream) {
                java.io.FileDescriptor fd = ((FileOutputStream) stream).getFD();
                String path = getPathFromFileDescriptor(fd);

                // Check if path matches camera output patterns
                return path != null && (
                    path.contains("/DCIM/") ||
                    path.contains("/Pictures/") ||
                    path.contains("camera") ||
                    path.endsWith(".jpg") ||
                    path.endsWith(".jpeg")
                );
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking camera file descriptor", e);
        }
        return false;
    }
}
