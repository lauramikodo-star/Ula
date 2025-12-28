package com.appcloner.replica;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class IconProcessor {
    private static final String TAG = "IconProcessor";

    /**
     * Processes the app icon based on the provided settings and saves it to a file.
     *
     * @param context   The context.
     * @param original  The original app icon drawable.
     * @param colorHex  The hex color string (e.g., "#FF0000"). Null or "#FFFFFF" for no change.
     * @param rotation  The rotation angle in degrees (0, 90, 180, 270).
     * @param flipH     Whether to flip horizontally.
     * @param flipV     Whether to flip vertically.
     * @param hue       Hue rotation (-180 to 180). 0 means unchanged.
     * @param saturation Saturation delta percentage (-100 to 100). 0 means unchanged.
     * @param lightness Lightness delta percentage (-100 to 100). 0 means unchanged.
     * @param autoHue   Whether to derive hue rotation from the provided color.
     * @param invertColors Whether to invert the icon colors.
     * @param sepia     Whether to apply a sepia tone.
     * @param badgeText The text to display as a badge. Null or empty for no badge.
     * @param badgePos  The position of the badge (0=TR, 1=TL, 2=BR, 3=BL).
     * @param outFile   The file to save the processed icon to.
     * @throws IOException If saving fails.
     */
    public static void processAndSaveIcon(Context context, Drawable original, String colorHex,
                                          int rotation, boolean flipH, boolean flipV,
                                          float hue, float saturation, float lightness,
                                          boolean autoHue, boolean invertColors, boolean sepia,
                                          String badgeText, int badgePos, File outFile) throws IOException {

        Bitmap bitmap = processIconDrawable(original, colorHex, rotation, flipH, flipV,
                hue, saturation, lightness, autoHue, invertColors, sepia, badgeText, badgePos);

        // Save to file
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    /**
     * Processes the app icon and returns the modified bitmap without writing it to disk.
     */
    public static Bitmap processIconDrawable(Drawable original, String colorHex,
                                             int rotation, boolean flipH, boolean flipV,
                                             float hue, float saturation, float lightness,
                                             boolean autoHue, boolean invertColors, boolean sepia,
                                             String badgeText, int badgePos) throws IOException {
        Bitmap bitmap = drawableToBitmap(original);
        if (bitmap == null) {
            throw new IOException("Failed to convert drawable to bitmap");
        }

        // Apply modifications
        bitmap = applyColor(bitmap, colorHex, hue, saturation, lightness, autoHue, invertColors, sepia);
        bitmap = applyRotationAndFlip(bitmap, rotation, flipH, flipV);
        bitmap = applyBadge(bitmap, badgeText, badgePos);
        return bitmap;
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        // Handle AdaptiveIconDrawable or others by drawing to canvas
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();

        if (width <= 0 || height <= 0) {
            width = 192; // Default size if intrinsic is invalid
            height = 192;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private static Bitmap applyColor(Bitmap source, String colorHex, float hue, float saturation,
                                     float lightness, boolean autoHue, boolean invertColors, boolean sepia) {
        Bitmap working = source.copy(Bitmap.Config.ARGB_8888, true);
        ColorMatrix masterMatrix = new ColorMatrix();

        float hueRotation = hue;
        if (autoHue && colorHex != null && !colorHex.isEmpty()) {
            try {
                float[] hsv = new float[3];
                Color.colorToHSV(Color.parseColor(colorHex), hsv);
                hueRotation = hsv[0] - 180f; // center around zero for rotation
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid color hex for auto hue: " + colorHex, e);
            }
        }

        if (Math.abs(hueRotation) > 0.01f) {
            masterMatrix.postConcat(createHueMatrix(hueRotation));
        }

        if (Math.abs(saturation) > 0.01f) {
            ColorMatrix saturationMatrix = new ColorMatrix();
            saturationMatrix.setSaturation(1f + (saturation / 100f));
            masterMatrix.postConcat(saturationMatrix);
        }

        if (Math.abs(lightness) > 0.01f) {
            float translate = 255f * (lightness / 100f);
            ColorMatrix lightMatrix = new ColorMatrix(new float[]{
                    1, 0, 0, 0, translate,
                    0, 1, 0, 0, translate,
                    0, 0, 1, 0, translate,
                    0, 0, 0, 1, 0
            });
            masterMatrix.postConcat(lightMatrix);
        }

        if (invertColors) {
            ColorMatrix invertMatrix = new ColorMatrix(new float[]{
                    -1, 0, 0, 0, 255,
                    0, -1, 0, 0, 255,
                    0, 0, -1, 0, 255,
                    0, 0, 0, 1, 0
            });
            masterMatrix.postConcat(invertMatrix);
        }

        if (sepia) {
            ColorMatrix sepiaMatrix = new ColorMatrix(new float[]{
                    0.393f, 0.769f, 0.189f, 0, 0,
                    0.349f, 0.686f, 0.168f, 0, 0,
                    0.272f, 0.534f, 0.131f, 0, 0,
                    0, 0, 0, 1, 0
            });
            masterMatrix.postConcat(sepiaMatrix);
        }

        boolean hasMatrixEffect = !isIdentity(masterMatrix);
        if (hasMatrixEffect) {
            Bitmap temp = Bitmap.createBitmap(working.getWidth(), working.getHeight(), working.getConfig());
            Canvas canvas = new Canvas(temp);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColorFilter(new ColorMatrixColorFilter(masterMatrix));
            canvas.drawBitmap(working, 0, 0, paint);
            working = temp;
        }

        // Apply a final color tint if provided and not default white
        if (colorHex != null && !colorHex.isEmpty() && !"#FFFFFF".equalsIgnoreCase(colorHex)) {
            try {
                int color = Color.parseColor(colorHex);
                Bitmap tinted = Bitmap.createBitmap(working.getWidth(), working.getHeight(), working.getConfig());
                Canvas canvas = new Canvas(tinted);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                canvas.drawBitmap(working, 0, 0, paint);
                working = tinted;
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid color hex: " + colorHex, e);
            }
        }

        return working;
    }

    private static Bitmap applyRotationAndFlip(Bitmap source, int rotation, boolean flipH, boolean flipV) {
        if (rotation == 0 && !flipH && !flipV) {
            return source;
        }

        Matrix matrix = new Matrix();

        if (rotation != 0) {
            matrix.postRotate(rotation);
        }

        float sx = flipH ? -1 : 1;
        float sy = flipV ? -1 : 1;
        if (flipH || flipV) {
            matrix.postScale(sx, sy);
        }

        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static ColorMatrix createHueMatrix(float value) {
        float angle = value % 360f;
        float rad = (float) Math.toRadians(angle);
        float cosVal = (float) Math.cos(rad);
        float sinVal = (float) Math.sin(rad);

        float lumR = 0.213f;
        float lumG = 0.715f;
        float lumB = 0.072f;

        ColorMatrix hueMatrix = new ColorMatrix(new float[] {
                lumR + cosVal * (1 - lumR) + sinVal * (-lumR), lumG + cosVal * (-lumG) + sinVal * (-lumG), lumB + cosVal * (-lumB) + sinVal * (1 - lumB), 0, 0,
                lumR + cosVal * (-lumR) + sinVal * 0.143f,      lumG + cosVal * (1 - lumG) + sinVal * 0.140f,      lumB + cosVal * (-lumB) + sinVal * (-0.283f),     0, 0,
                lumR + cosVal * (-lumR) + sinVal * (-(1 - lumR)), lumG + cosVal * (-lumG) + sinVal * lumG,         lumB + cosVal * (1 - lumB) + sinVal * lumB,        0, 0,
                0, 0, 0, 1, 0
        });
        return hueMatrix;
    }

    private static boolean isIdentity(ColorMatrix matrix) {
        float[] m = matrix.getArray();
        float[] identity = new float[] {
                1, 0, 0, 0, 0,
                0, 1, 0, 0, 0,
                0, 0, 1, 0, 0,
                0, 0, 0, 1, 0
        };

        for (int i = 0; i < m.length; i++) {
            if (Math.abs(m[i] - identity[i]) > 0.0001f) {
                return false;
            }
        }
        return true;
    }

    private static Bitmap applyBadge(Bitmap source, String badgeText, int badgePos) {
        if (badgeText == null || badgeText.trim().isEmpty()) {
            return source;
        }

        // Use a mutable copy to draw on
        Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);

        int width = result.getWidth();
        int height = result.getHeight();
        float density = width / 192f; // Assuming 192x192 base
        if (density < 1) density = 1;

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.RED);
        bgPaint.setStyle(Paint.Style.FILL);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Calculate badge size
        float badgeSize = width / 3.5f;
        float textSize = badgeSize * 0.6f;
        textPaint.setTextSize(textSize);

        float x = 0, y = 0;
        float padding = width * 0.05f;

        // 0=TR, 1=TL, 2=BR, 3=BL
        switch (badgePos) {
            case 0: // Top Right
                x = width - badgeSize / 2 - padding;
                y = badgeSize / 2 + padding;
                break;
            case 1: // Top Left
                x = badgeSize / 2 + padding;
                y = badgeSize / 2 + padding;
                break;
            case 2: // Bottom Right
                x = width - badgeSize / 2 - padding;
                y = height - badgeSize / 2 - padding;
                break;
            case 3: // Bottom Left
                x = badgeSize / 2 + padding;
                y = height - badgeSize / 2 - padding;
                break;
        }

        canvas.drawCircle(x, y, badgeSize / 2, bgPaint);

        // Center text vertically
        Rect textBounds = new Rect();
        textPaint.getTextBounds(badgeText, 0, badgeText.length(), textBounds);
        float textHeight = textBounds.height();
        canvas.drawText(badgeText, x, y + textHeight / 2 - textBounds.bottom, textPaint);

        return result;
    }
}
