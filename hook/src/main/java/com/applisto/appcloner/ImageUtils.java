package com.applisto.appcloner;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class for image format conversions.
 * Provides methods to convert between Bitmap, NV21, YUV_420_888, RGBA_8888, and other formats.
 * 
 * Supported formats:
 * - ImageFormat.JPEG (256)
 * - ImageFormat.YUV_420_888 (35)
 * - ImageFormat.NV21 (17)
 * - PixelFormat.RGBA_8888 (1)
 * - PixelFormat.RGB_565 (4)
 * - ImageFormat.FLEX_RGB_888 (41)
 * - ImageFormat.FLEX_RGBA_8888 (42)
 */
public final class ImageUtils {
    private static final String TAG = "ImageUtils";
    
    public static int DEFAULT_JPEG_QUALITY = 90;

    public enum ScaleType {
        CENTER_CROP,
        CENTER_INSIDE
    }

    // Common image format constants for reference
    public static final int FORMAT_RGBA_8888 = 1;      // PixelFormat.RGBA_8888
    public static final int FORMAT_RGBX_8888 = 2;      // PixelFormat.RGBX_8888
    public static final int FORMAT_RGB_888 = 3;        // PixelFormat.RGB_888
    public static final int FORMAT_RGB_565 = 4;        // PixelFormat.RGB_565
    public static final int FORMAT_NV21 = 17;          // ImageFormat.NV21
    public static final int FORMAT_NV16 = 16;          // ImageFormat.NV16
    public static final int FORMAT_YUY2 = 20;          // ImageFormat.YUY2
    public static final int FORMAT_YUV_420_888 = 35;   // ImageFormat.YUV_420_888
    public static final int FORMAT_YUV_422_888 = 39;   // ImageFormat.YUV_422_888
    public static final int FORMAT_YUV_444_888 = 40;   // ImageFormat.YUV_444_888
    public static final int FORMAT_FLEX_RGB_888 = 41;  // ImageFormat.FLEX_RGB_888
    public static final int FORMAT_FLEX_RGBA_8888 = 42; // ImageFormat.FLEX_RGBA_8888
    public static final int FORMAT_JPEG = 256;         // ImageFormat.JPEG

    public ImageUtils() {
        // Public constructor as per Smali
    }

    public static Uri addImageToMediaStore(ContentResolver cr, Bitmap bitmap, String title, String description, int jpegQuality) {
        Log.i(TAG, "addImageToMediaStore; title: " + title + ", description: " + description + ", jpegQuality: " + jpegQuality);

        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("_display_name", title);
        values.put("description", description);

        String mimeType = (jpegQuality != 0) ? "image/jpeg" : "image/png";
        values.put("mime_type", mimeType);

        long currentTime = System.currentTimeMillis();
        values.put("date_added", currentTime / 1000);

        if (Build.VERSION.SDK_INT >= 29) {
            values.put("datetaken", currentTime);
        }

        Uri uri = null;
        try {
            uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (Exception e) {
            Log.w(TAG, e);
            return null;
        }

        if (uri != null) {
            Log.i(TAG, "addImageToMediaStore; uri: " + uri);
            try {
                if (bitmap != null) {
                    try (OutputStream os = cr.openOutputStream(uri)) {
                        if (jpegQuality != 0) {
                            bitmap.compress(CompressFormat.JPEG, jpegQuality, os);
                        } else {
                            bitmap.compress(CompressFormat.PNG, 0, os);
                        }
                    }
                } else {
                    cr.delete(uri, null, null);
                    return null;
                }
            } catch (Exception e) {
                Log.w(TAG, e);
                cr.delete(uri, null, null);
                return null;
            }

            long id = ContentUris.parseId(uri);
            Bitmap thumb = MediaStore.Images.Thumbnails.getThumbnail(cr, id, MediaStore.Images.Thumbnails.MINI_KIND, null);
            storeThumbnail(cr, thumb, id, 50.0f, 50.0f, MediaStore.Images.Thumbnails.MICRO_KIND);
        }

        return uri;
    }

    public static Bitmap fitBitmap(Bitmap bitmap, int size) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float ratio = (float) width / height;

        int newWidth, newHeight;
        if (ratio < 1.0f) {
            newWidth = (int) (size * ratio);
            newHeight = size;
        } else {
            newWidth = size;
            newHeight = (int) (size / ratio);
        }

        return resizeBitmap(bitmap, newWidth, newHeight);
    }

    public static Bitmap resizeBitmap(Bitmap bitmap, int width, int height) {
        return resizeBitmap(bitmap, width, height, null);
    }

    public static Bitmap resizeBitmapCenterCrop(Bitmap bitmap, int width, int height) {
        return resizeBitmap(bitmap, width, height, ScaleType.CENTER_CROP);
    }

    public static Bitmap resizeBitmapCenterInside(Bitmap bitmap, int width, int height) {
        return resizeBitmap(bitmap, width, height, ScaleType.CENTER_INSIDE);
    }

    public static Bitmap resizeBitmap(Bitmap bitmap, int width, int height, ScaleType scaleType) {
        if (bitmap == null) {
            return null;
        }

        // Prevent crashes on invalid dimensions
        if (width <= 0 || height <= 0) {
            return null;
        }

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        float scaleX = (float) width / bitmap.getWidth();
        float scaleY = (float) height / bitmap.getHeight();

        if (scaleType == ScaleType.CENTER_CROP) {
            float s = Math.max(scaleX, scaleY);
            scaleX = s;
            scaleY = s;
        } else if (scaleType == ScaleType.CENTER_INSIDE) {
            float s = Math.min(scaleX, scaleY);
            scaleX = s;
            scaleY = s;
        }

        Matrix matrix = new Matrix();
        // Scale around the center of the TARGET bitmap
        matrix.setScale(scaleX, scaleY, width / 2.0f, height / 2.0f);

        Canvas canvas = new Canvas(output);
        canvas.setMatrix(matrix);

        // Center source bitmap in target coordinates
        float x = (width / 2.0f) - (bitmap.getWidth() / 2.0f);
        float y = (height / 2.0f) - (bitmap.getHeight() / 2.0f);

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bitmap, x, y, paint);

        return output;
    }

    private static Bitmap storeThumbnail(ContentResolver cr, Bitmap source, long id, float width, float height, int kind) {
        if (source == null) return null;

        Matrix matrix = new Matrix();
        float scaleX = width / source.getWidth();
        float scaleY = height / source.getHeight();
        matrix.setScale(scaleX, scaleY);

        Bitmap thumb = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);

        ContentValues values = new ContentValues(4);
        values.put(MediaStore.Images.Thumbnails.KIND, kind);
        values.put(MediaStore.Images.Thumbnails.IMAGE_ID, (int) id);
        values.put(MediaStore.Images.Thumbnails.HEIGHT, thumb.getHeight());
        values.put(MediaStore.Images.Thumbnails.WIDTH, thumb.getWidth());

        Uri uri = null;
        try {
            uri = cr.insert(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, values);
        } catch (Exception e) {
            return null;
        }

        try {
            if (uri != null) {
                try (OutputStream os = cr.openOutputStream(uri)) {
                    thumb.compress(CompressFormat.JPEG, DEFAULT_JPEG_QUALITY, os);
                }
                return thumb;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * Converts a Bitmap to NV21 format byte array.
     * NV21 is the standard Android camera preview format (YCrCb).
     *
     * @param bitmap The source bitmap (ARGB_8888 or RGB_565)
     * @return NV21 formatted byte array
     */
    public static byte[] bitmapToNV21(Bitmap bitmap) {
        return bitmapToNV21(bitmap, null, null);
    }

    /**
     * Converts a Bitmap to NV21 format byte array using reusable buffers.
     *
     * @param bitmap The source bitmap
     * @param reusableBuffer Optional int[] buffer for pixels (size must be >= width*height)
     * @param reusableOutput Optional byte[] buffer for output (size must be >= width*height*1.5)
     * @return NV21 formatted byte array
     */
    public static byte[] bitmapToNV21(Bitmap bitmap, int[] reusableBuffer, byte[] reusableOutput) {
        if (bitmap == null) {
            Log.w(TAG, "bitmapToNV21: bitmap is null");
            return reusableOutput != null ? reusableOutput : new byte[0];
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = width * height;

        // Use reusable buffer if valid
        int[] pixels = reusableBuffer;
        if (pixels == null || pixels.length < size) {
            pixels = new int[size];
        }

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        return rgbToNV21(pixels, width, height, reusableOutput);
    }

    /**
     * Converts RGB pixel array to NV21 format.
     *
     * @param argb   Array of ARGB pixels
     * @param width  Image width
     * @param height Image height
     * @return NV21 formatted byte array
     */
    public static byte[] rgbToNV21(int[] argb, int width, int height) {
        return rgbToNV21(argb, width, height, null);
    }

    /**
     * Converts RGB pixel array to NV21 format using reusable output buffer.
     *
     * @param argb   Array of ARGB pixels
     * @param width  Image width
     * @param height Image height
     * @param reusableOutput Optional byte[] buffer
     * @return NV21 formatted byte array
     */
    public static byte[] rgbToNV21(int[] argb, int width, int height, byte[] reusableOutput) {
        // NV21 size: Y plane (width * height) + UV plane (width * height / 2)
        int frameSize = width * height;
        int chromaSize = frameSize / 2;
        int totalSize = frameSize + chromaSize;

        byte[] nv21 = reusableOutput;
        if (nv21 == null || nv21.length < totalSize) {
            nv21 = new byte[totalSize];
        }

        int yIndex = 0;
        int uvIndex = frameSize;

        // Optimization: Cache frame size/width in local vars for loop speed?
        // Actually, direct array access is fast. The main cost is the math and loop.

        for (int j = 0; j < height; j++) {
            int rowOffset = j * width;
            for (int i = 0; i < width; i++) {
                int pixel = argb[rowOffset + i];

                // Extract RGB components
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // Convert RGB to YUV (BT.601)
                // y = 0.299R + 0.587G + 0.114B
                // u = -0.169R - 0.331G + 0.500B + 128
                // v = 0.500R - 0.419G - 0.081B + 128

                // Integer math approximation:
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;

                // Clamp values
                // Manual clamp is faster than Math.max/min call overhead in tight loop
                y = (y < 16) ? 16 : ((y > 235) ? 235 : y);
                u = (u < 16) ? 16 : ((u > 240) ? 240 : u);
                v = (v < 16) ? 16 : ((v > 240) ? 240 : v);

                // Store Y value
                nv21[yIndex++] = (byte) y;

                // Store UV values (subsampled 2x2)
                // NV21 format: VU VU VU... (V first, then U)
                if ((j & 1) == 0 && (i & 1) == 0 && uvIndex < totalSize - 1) {
                    nv21[uvIndex++] = (byte) v;
                    nv21[uvIndex++] = (byte) u;
                }
            }
        }

        return nv21;
    }

    /**
     * Writes YUV data (NV21 format) to Image.Plane array for YUV_420_888 format.
     *
     * @param yuvData NV21 formatted data
     * @param width   Image width
     * @param height  Image height
     * @param planes  Image.Plane array from the target Image
     */
    public static void writeYuvToPlanes(byte[] yuvData, int width, int height, Image.Plane[] planes) {
        if (yuvData == null || planes == null || planes.length < 3) {
            Log.w(TAG, "writeYuvToPlanes: invalid parameters");
            return;
        }

        int frameSize = width * height;

        try {
            // Y plane
            ByteBuffer yBuffer = planes[0].getBuffer();
            int yRowStride = planes[0].getRowStride();
            int yPixelStride = planes[0].getPixelStride();

            // U plane (Cb)
            ByteBuffer uBuffer = planes[1].getBuffer();
            int uRowStride = planes[1].getRowStride();
            int uPixelStride = planes[1].getPixelStride();

            // V plane (Cr)
            ByteBuffer vBuffer = planes[2].getBuffer();
            int vRowStride = planes[2].getRowStride();
            int vPixelStride = planes[2].getPixelStride();

            // Write Y plane
            writeYPlane(yuvData, yBuffer, width, height, yRowStride, yPixelStride);

            // Write UV planes from NV21 data
            // NV21 format: YYYYYYYY VUVUVU (V and U interleaved after Y)
            writeUVPlanesFromNV21(yuvData, frameSize, uBuffer, vBuffer,
                    width, height, uRowStride, uPixelStride, vRowStride, vPixelStride);

            // Removed verbose log: Log.d(TAG, "Successfully wrote YUV data to planes");

        } catch (Exception e) {
            Log.e(TAG, "Error writing YUV data to planes", e);
        }
    }

    private static void writeYPlane(byte[] nv21, ByteBuffer yBuffer,
                                    int width, int height, int rowStride, int pixelStride) {
        yBuffer.rewind();

        if (rowStride == width && pixelStride == 1) {
            // Simple case: no padding, contiguous data
            int ySize = Math.min(width * height, yBuffer.remaining());
            yBuffer.put(nv21, 0, ySize);
        } else {
            // Handle row stride and pixel stride
            for (int row = 0; row < height; row++) {
                int srcOffset = row * width;
                for (int col = 0; col < width; col++) {
                    if (yBuffer.hasRemaining()) {
                        yBuffer.put(nv21[srcOffset + col]);
                    }
                }
                // Skip padding bytes at end of row
                int paddingBytes = rowStride - (width * pixelStride);
                if (paddingBytes > 0 && yBuffer.remaining() >= paddingBytes) {
                    // Skip padding bytes instead of overwriting with 0
                    yBuffer.position(yBuffer.position() + paddingBytes);
                }
            }
        }

        yBuffer.rewind();
    }

    private static void writeUVPlanesFromNV21(byte[] nv21, int ySize,
                                              ByteBuffer uBuffer, ByteBuffer vBuffer,
                                              int width, int height,
                                              int uRowStride, int uPixelStride,
                                              int vRowStride, int vPixelStride) {
        uBuffer.rewind();
        vBuffer.rewind();

        int uvHeight = height / 2;
        int uvWidth = width / 2;

        // NV21: V comes first, then U (interleaved)
        for (int row = 0; row < uvHeight; row++) {
            for (int col = 0; col < uvWidth; col++) {
                int nv21Index = ySize + (row * width) + (col * 2);

                if (nv21Index + 1 < nv21.length) {
                    byte v = nv21[nv21Index];     // V
                    byte u = nv21[nv21Index + 1]; // U

                    if (vBuffer.hasRemaining()) {
                        vBuffer.put(v);
                    }
                    if (uBuffer.hasRemaining()) {
                        uBuffer.put(u);
                    }

                    // Handle pixel stride > 1 (skip bytes between pixels)
                    // Skip bytes instead of overwriting with 0 to preserve interleaved data
                    int uSkip = uPixelStride - 1;
                    if (uSkip > 0 && uBuffer.remaining() >= uSkip) {
                        uBuffer.position(uBuffer.position() + uSkip);
                    }

                    int vSkip = vPixelStride - 1;
                    if (vSkip > 0 && vBuffer.remaining() >= vSkip) {
                        vBuffer.position(vBuffer.position() + vSkip);
                    }
                }
            }

            // Handle row stride (skip padding at end of row)
            int uPadding = uRowStride - (uvWidth * uPixelStride);
            if (uPadding > 0 && uBuffer.remaining() >= uPadding) {
                uBuffer.position(uBuffer.position() + uPadding);
            }

            int vPadding = vRowStride - (uvWidth * vPixelStride);
            if (vPadding > 0 && vBuffer.remaining() >= vPadding) {
                vBuffer.position(vBuffer.position() + vPadding);
            }
        }

        uBuffer.rewind();
        vBuffer.rewind();
    }

    /**
     * Converts a Bitmap to YUV_420_888 format byte array.
     * Similar to NV21 but with different UV plane arrangement.
     *
     * @param bitmap Source bitmap
     * @return YUV_420_888 formatted byte array (Y plane, then U plane, then V plane)
     */
    public static byte[] bitmapToYUV420(Bitmap bitmap) {
        if (bitmap == null) {
            return new byte[0];
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int frameSize = width * height;
        int chromaSize = frameSize / 4;

        byte[] yuv = new byte[frameSize + chromaSize * 2];

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + chromaSize;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int pixel = pixels[j * width + i];

                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // Convert RGB to YUV
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;

                yuv[yIndex++] = (byte) clamp(y, 16, 235);

                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uIndex++] = (byte) clamp(u, 16, 240);
                    yuv[vIndex++] = (byte) clamp(v, 16, 240);
                }
            }
        }

        return yuv;
    }

    /**
     * Clamps a value between min and max.
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Checks if the given image format is supported for overwriting.
     * 
     * @param format The image format code
     * @return true if the format is supported
     */
    public static boolean isFormatSupported(int format) {
        switch (format) {
            case FORMAT_JPEG:
            case FORMAT_YUV_420_888:
            case FORMAT_NV21:
            case FORMAT_RGBA_8888:
            case FORMAT_RGBX_8888:
            case FORMAT_RGB_888:
            case FORMAT_RGB_565:
            case FORMAT_FLEX_RGB_888:
            case FORMAT_FLEX_RGBA_8888:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Gets a human-readable name for an image format.
     * 
     * @param format The image format code
     * @return Human-readable format name
     */
    public static String getFormatName(int format) {
        switch (format) {
            case FORMAT_RGBA_8888: return "RGBA_8888";
            case FORMAT_RGBX_8888: return "RGBX_8888";
            case FORMAT_RGB_888: return "RGB_888";
            case FORMAT_RGB_565: return "RGB_565";
            case FORMAT_NV21: return "NV21";
            case FORMAT_NV16: return "NV16";
            case FORMAT_YUY2: return "YUY2";
            case FORMAT_YUV_420_888: return "YUV_420_888";
            case FORMAT_YUV_422_888: return "YUV_422_888";
            case FORMAT_YUV_444_888: return "YUV_444_888";
            case FORMAT_FLEX_RGB_888: return "FLEX_RGB_888";
            case FORMAT_FLEX_RGBA_8888: return "FLEX_RGBA_8888";
            case FORMAT_JPEG: return "JPEG";
            default: return "UNKNOWN(" + format + ")";
        }
    }
    
    /**
     * Converts a Bitmap to RGBA_8888 byte array.
     * 
     * @param bitmap The source bitmap
     * @return RGBA_8888 formatted byte array
     */
    public static byte[] bitmapToRGBA8888(Bitmap bitmap) {
        if (bitmap == null) {
            Log.w(TAG, "bitmapToRGBA8888: bitmap is null");
            return new byte[0];
        }
        
        // Ensure bitmap is in ARGB_8888 format
        Bitmap argbBitmap = bitmap;
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            argbBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        
        int width = argbBitmap.getWidth();
        int height = argbBitmap.getHeight();
        int[] pixels = new int[width * height];
        argbBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        
        // RGBA_8888: 4 bytes per pixel (R, G, B, A)
        byte[] rgba = new byte[width * height * 4];
        
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int offset = i * 4;
            
            // ARGB -> RGBA conversion
            rgba[offset] = (byte) ((pixel >> 16) & 0xFF);     // R
            rgba[offset + 1] = (byte) ((pixel >> 8) & 0xFF);  // G
            rgba[offset + 2] = (byte) (pixel & 0xFF);         // B
            rgba[offset + 3] = (byte) ((pixel >> 24) & 0xFF); // A
        }
        
        if (argbBitmap != bitmap) {
            argbBitmap.recycle();
        }
        
        return rgba;
    }
    
    /**
     * Converts a Bitmap to RGB_565 byte array.
     * 
     * @param bitmap The source bitmap
     * @return RGB_565 formatted byte array
     */
    public static byte[] bitmapToRGB565(Bitmap bitmap) {
        if (bitmap == null) {
            Log.w(TAG, "bitmapToRGB565: bitmap is null");
            return new byte[0];
        }
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // Convert to RGB_565 if needed
        Bitmap rgb565Bitmap = bitmap;
        if (bitmap.getConfig() != Bitmap.Config.RGB_565) {
            rgb565Bitmap = bitmap.copy(Bitmap.Config.RGB_565, false);
        }
        
        // RGB_565: 2 bytes per pixel
        ByteBuffer buffer = ByteBuffer.allocate(width * height * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        rgb565Bitmap.copyPixelsToBuffer(buffer);
        
        if (rgb565Bitmap != bitmap) {
            rgb565Bitmap.recycle();
        }
        
        return buffer.array();
    }
    
    /**
     * Writes RGBA data to an Image plane for RGBA_8888 format.
     * 
     * @param bitmap Source bitmap
     * @param planes Image.Plane array from the target Image
     * @param width  Image width
     * @param height Image height
     * @return true if successful
     */
    public static boolean writeRGBAToPlanes(Bitmap bitmap, Image.Plane[] planes, int width, int height) {
        if (bitmap == null || planes == null || planes.length == 0) {
            Log.w(TAG, "writeRGBAToPlanes: invalid parameters");
            return false;
        }
        
        try {
            // Resize bitmap if dimensions don't match
            Bitmap resizedBitmap = bitmap;
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            
            // Ensure ARGB_8888 format
            Bitmap argbBitmap = resizedBitmap;
            if (resizedBitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                argbBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, false);
            }
            
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int pixelStride = planes[0].getPixelStride();
            
            buffer.rewind();
            
            // Get pixels from bitmap
            int[] pixels = new int[width * height];
            argbBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            
            // Write pixels to buffer
            if (rowStride == width * 4 && pixelStride == 4) {
                // Simple case: contiguous buffer
                for (int pixel : pixels) {
                    if (buffer.remaining() >= 4) {
                        // ARGB -> RGBA
                        buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                        buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                        buffer.put((byte) (pixel & 0xFF));         // B
                        buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
                    }
                }
            } else {
                // Handle row stride and pixel stride
                for (int row = 0; row < height; row++) {
                    for (int col = 0; col < width; col++) {
                        if (buffer.remaining() >= pixelStride) {
                            int pixel = pixels[row * width + col];
                            buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                            buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                            buffer.put((byte) (pixel & 0xFF));         // B
                            buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
                            
                            // Skip extra bytes for pixel stride > 4
                            for (int s = 4; s < pixelStride && buffer.hasRemaining(); s++) {
                                buffer.put((byte) 0);
                            }
                        }
                    }
                    // Skip row padding
                    int rowPadding = rowStride - (width * pixelStride);
                    for (int p = 0; p < rowPadding && buffer.hasRemaining(); p++) {
                        buffer.put((byte) 0);
                    }
                }
            }
            
            buffer.rewind();
            
            // Cleanup
            if (argbBitmap != resizedBitmap) {
                argbBitmap.recycle();
            }
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
            
            // Removed verbose log: Log.d(TAG, "Successfully wrote RGBA data to plane");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error writing RGBA data to planes", e);
            return false;
        }
    }
    
    /**
     * Writes RGB_565 data to an Image plane.
     * 
     * @param bitmap Source bitmap
     * @param planes Image.Plane array from the target Image
     * @param width  Image width
     * @param height Image height
     * @return true if successful
     */
    public static boolean writeRGB565ToPlanes(Bitmap bitmap, Image.Plane[] planes, int width, int height) {
        if (bitmap == null || planes == null || planes.length == 0) {
            Log.w(TAG, "writeRGB565ToPlanes: invalid parameters");
            return false;
        }
        
        try {
            // Resize bitmap if dimensions don't match
            Bitmap resizedBitmap = bitmap;
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            
            // Convert to RGB_565
            Bitmap rgb565Bitmap = resizedBitmap;
            if (resizedBitmap.getConfig() != Bitmap.Config.RGB_565) {
                rgb565Bitmap = resizedBitmap.copy(Bitmap.Config.RGB_565, false);
            }
            
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            
            buffer.rewind();
            
            // RGB_565: 2 bytes per pixel
            if (rowStride == width * 2) {
                // Simple case: contiguous buffer
                rgb565Bitmap.copyPixelsToBuffer(buffer);
            } else {
                // Handle row stride
                ByteBuffer rowBuffer = ByteBuffer.allocate(width * 2);
                for (int row = 0; row < height; row++) {
                    rowBuffer.rewind();
                    // Copy one row from bitmap
                    Bitmap rowBitmap = Bitmap.createBitmap(rgb565Bitmap, 0, row, width, 1);
                    rowBitmap.copyPixelsToBuffer(rowBuffer);
                    rowBitmap.recycle();
                    
                    // Write to main buffer
                    rowBuffer.rewind();
                    byte[] rowBytes = new byte[width * 2];
                    rowBuffer.get(rowBytes);
                    buffer.put(rowBytes);
                    
                    // Skip row padding
                    int rowPadding = rowStride - (width * 2);
                    for (int p = 0; p < rowPadding && buffer.hasRemaining(); p++) {
                        buffer.put((byte) 0);
                    }
                }
            }
            
            buffer.rewind();
            
            // Cleanup
            if (rgb565Bitmap != resizedBitmap) {
                rgb565Bitmap.recycle();
            }
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
            
            // Removed verbose log: Log.d(TAG, "Successfully wrote RGB565 data to plane");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error writing RGB565 data to planes", e);
            return false;
        }
    }
    
    /**
     * Writes fake image data to an Image object based on its format.
     * This is the main entry point for overwriting camera images.
     * 
     * @param image  The target Image to overwrite
     * @param bitmap The source bitmap with fake image data
     * @return true if successful, false if format is unsupported or an error occurred
     */
    public static boolean writeFakeDataToImage(Image image, Bitmap bitmap) {
        if (image == null || bitmap == null) {
            Log.w(TAG, "writeFakeDataToImage: null parameters");
            return false;
        }
        
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        
        if (planes == null || planes.length == 0) {
            Log.e(TAG, "Image has no planes to overwrite");
            return false;
        }
        
        Log.d(TAG, "Attempting to write fake data to format: " + getFormatName(format) + 
                   " (" + width + "x" + height + ")");
        
        try {
            switch (format) {
                case FORMAT_JPEG:
                    return writeJpegToPlanes(bitmap, planes, width, height);
                    
                case FORMAT_YUV_420_888:
                case FORMAT_NV21:
                    return writeYuvToImage(bitmap, planes, width, height);
                    
                case FORMAT_RGBA_8888:
                case FORMAT_RGBX_8888:
                case FORMAT_FLEX_RGBA_8888:
                    return writeRGBAToPlanes(bitmap, planes, width, height);
                    
                case FORMAT_RGB_565:
                    return writeRGB565ToPlanes(bitmap, planes, width, height);
                    
                case FORMAT_RGB_888:
                case FORMAT_FLEX_RGB_888:
                    return writeRGB888ToPlanes(bitmap, planes, width, height);
                    
                default:
                    Log.w(TAG, "Unsupported image format: " + getFormatName(format));
                    // Try RGBA as fallback for unknown formats with single plane
                    if (planes.length == 1) {
                        Log.d(TAG, "Attempting RGBA fallback for unknown format");
                        return writeRGBAToPlanes(bitmap, planes, width, height);
                    }
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing fake data to image", e);
            return false;
        }
    }
    
    /**
     * Writes JPEG data to an Image plane.
     * 
     * @param bitmap Source bitmap
     * @param planes Image.Plane array
     * @param width  Image width
     * @param height Image height
     * @return true if successful
     */
    private static boolean writeJpegToPlanes(Bitmap bitmap, Image.Plane[] planes, int width, int height) {
        try {
            // Resize bitmap if needed
            Bitmap resizedBitmap = bitmap;
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            
            // Compress to JPEG
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, baos);
            byte[] jpegData = baos.toByteArray();
            
            ByteBuffer buffer = planes[0].getBuffer();
            buffer.rewind();
            
            int bufferSize = buffer.remaining();
            if (jpegData.length > bufferSize) {
                buffer.put(jpegData, 0, bufferSize);
                Log.w(TAG, "JPEG data truncated to fit buffer");
            } else {
                buffer.put(jpegData);
            }
            
            buffer.rewind();
            
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
            
            // Removed verbose log: Log.d(TAG, "Successfully wrote JPEG data to plane");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error writing JPEG data", e);
            return false;
        }
    }
    
    /**
     * Writes YUV data to an Image (wrapper for existing writeYuvToPlanes).
     */
    private static boolean writeYuvToImage(Bitmap bitmap, Image.Plane[] planes, int width, int height) {
        try {
            // Resize bitmap if needed
            Bitmap resizedBitmap = bitmap;
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            
            byte[] yuvData = bitmapToNV21(resizedBitmap);
            writeYuvToPlanes(yuvData, width, height, planes);
            
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error writing YUV data", e);
            return false;
        }
    }
    
    /**
     * Writes RGB_888 data to an Image plane.
     * 
     * @param bitmap Source bitmap
     * @param planes Image.Plane array
     * @param width  Image width
     * @param height Image height
     * @return true if successful
     */
    public static boolean writeRGB888ToPlanes(Bitmap bitmap, Image.Plane[] planes, int width, int height) {
        if (bitmap == null || planes == null || planes.length == 0) {
            Log.w(TAG, "writeRGB888ToPlanes: invalid parameters");
            return false;
        }
        
        try {
            // Resize bitmap if dimensions don't match
            Bitmap resizedBitmap = bitmap;
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            
            // Ensure ARGB_8888 format for pixel access
            Bitmap argbBitmap = resizedBitmap;
            if (resizedBitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                argbBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, false);
            }
            
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int pixelStride = planes[0].getPixelStride();
            
            buffer.rewind();
            
            // Get pixels from bitmap
            int[] pixels = new int[width * height];
            argbBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            
            // Write pixels to buffer (RGB_888: 3 bytes per pixel)
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    if (buffer.remaining() >= 3) {
                        int pixel = pixels[row * width + col];
                        buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                        buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                        buffer.put((byte) (pixel & 0xFF));         // B
                        
                        // Skip extra bytes for pixel stride > 3
                        for (int s = 3; s < pixelStride && buffer.hasRemaining(); s++) {
                            buffer.put((byte) 0);
                        }
                    }
                }
                // Skip row padding
                int rowPadding = rowStride - (width * pixelStride);
                for (int p = 0; p < rowPadding && buffer.hasRemaining(); p++) {
                    buffer.put((byte) 0);
                }
            }
            
            buffer.rewind();
            
            // Cleanup
            if (argbBitmap != resizedBitmap) {
                argbBitmap.recycle();
            }
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
            
            // Removed verbose log: Log.d(TAG, "Successfully wrote RGB888 data to plane");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error writing RGB888 data to planes", e);
            return false;
        }
    }
    
    // ==================== Enhanced Methods for Liveness Detection ====================
    
    /**
     * Write fake image data with enhanced error handling and format detection.
     * This method provides additional resilience for liveness detection scenarios.
     * 
     * @param image Target image to overwrite
     * @param bitmap Source bitmap with fake image data
     * @param allowFallback Whether to try fallback methods if primary method fails
     * @return true if successful
     */
    public static boolean writeFakeDataToImageRobust(Image image, Bitmap bitmap, boolean allowFallback) {
        if (image == null || bitmap == null) {
            Log.w(TAG, "writeFakeDataToImageRobust: null parameters");
            return false;
        }
        
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        
        if (planes == null || planes.length == 0) {
            Log.e(TAG, "Image has no planes to overwrite");
            return false;
        }
        
        // Log format details for debugging
        Log.d(TAG, "Robust write: format=" + getFormatName(format) + 
                   ", size=" + width + "x" + height +
                   ", planes=" + planes.length);
        
        // Try primary method first
        try {
            boolean success = writeFakeDataToImage(image, bitmap);
            if (success) {
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Primary write method failed", e);
        }
        
        if (!allowFallback) {
            return false;
        }
        
        // Fallback methods
        Log.d(TAG, "Attempting fallback write methods");
        
        // Fallback 1: Try direct buffer copy
        try {
            if (tryDirectBufferWrite(image, bitmap)) {
                Log.d(TAG, "Direct buffer write succeeded");
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Direct buffer fallback failed", e);
        }
        
        // Fallback 2: Try YUV conversion for any format
        try {
            if (planes.length >= 3) {
                byte[] yuvData = bitmapToNV21(bitmap);
                writeYuvToPlanes(yuvData, width, height, planes);
                Log.d(TAG, "YUV fallback write succeeded");
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "YUV fallback failed", e);
        }
        
        // Fallback 3: Try RGBA for single plane formats
        try {
            if (planes.length == 1) {
                if (writeRGBAToPlanes(bitmap, planes, width, height)) {
                    Log.d(TAG, "RGBA fallback write succeeded");
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "RGBA fallback failed", e);
        }
        
        Log.e(TAG, "All write methods failed for format: " + getFormatName(format));
        return false;
    }
    
    /**
     * Try direct buffer write by copying bitmap bytes.
     */
    private static boolean tryDirectBufferWrite(Image image, Bitmap bitmap) {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return false;
        
        ByteBuffer buffer = planes[0].getBuffer();
        if (buffer == null || !buffer.isDirect()) return false;
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Resize bitmap if needed
        Bitmap resizedBitmap = bitmap;
        if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
            resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }
        
        try {
            buffer.rewind();
            
            // Try to determine bytes per pixel from buffer capacity
            int bufferCapacity = buffer.capacity();
            int totalPixels = width * height;
            int bytesPerPixel = bufferCapacity / totalPixels;
            
            if (bytesPerPixel == 4) {
                // RGBA or ARGB format
                Bitmap argbBitmap = resizedBitmap.getConfig() == Bitmap.Config.ARGB_8888 
                    ? resizedBitmap 
                    : resizedBitmap.copy(Bitmap.Config.ARGB_8888, false);
                
                ByteBuffer tempBuffer = ByteBuffer.allocate(width * height * 4);
                argbBitmap.copyPixelsToBuffer(tempBuffer);
                tempBuffer.rewind();
                
                // Copy to image buffer
                int copySize = Math.min(buffer.remaining(), tempBuffer.remaining());
                byte[] data = new byte[copySize];
                tempBuffer.get(data);
                buffer.put(data);
                
                if (argbBitmap != resizedBitmap) {
                    argbBitmap.recycle();
                }
                
                buffer.rewind();
                return true;
            } else if (bytesPerPixel == 2) {
                // RGB_565 format
                Bitmap rgb565Bitmap = resizedBitmap.getConfig() == Bitmap.Config.RGB_565
                    ? resizedBitmap
                    : resizedBitmap.copy(Bitmap.Config.RGB_565, false);
                
                ByteBuffer tempBuffer = ByteBuffer.allocate(width * height * 2);
                rgb565Bitmap.copyPixelsToBuffer(tempBuffer);
                tempBuffer.rewind();
                
                int copySize = Math.min(buffer.remaining(), tempBuffer.remaining());
                byte[] data = new byte[copySize];
                tempBuffer.get(data);
                buffer.put(data);
                
                if (rgb565Bitmap != resizedBitmap) {
                    rgb565Bitmap.recycle();
                }
                
                buffer.rewind();
                return true;
            }
        } finally {
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
        }
        
        return false;
    }
    
    /**
     * Enhanced method to write RGBA data with proper buffer boundary checking.
     * Addresses the "write fake data" log messages.
     */
    public static boolean writeRGBAToPlanesSafe(Bitmap bitmap, Image.Plane[] planes, 
                                                 int width, int height) {
        if (bitmap == null || planes == null || planes.length == 0) {
            Log.w(TAG, "writeRGBAToPlanesSafe: invalid parameters");
            return false;
        }
        
        try {
            // Resize bitmap if dimensions don't match
            Bitmap workBitmap = bitmap;
            boolean needsRecycle = false;
            
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                workBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                needsRecycle = true;
            }
            
            // Ensure ARGB_8888 format
            if (workBitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                Bitmap converted = workBitmap.copy(Bitmap.Config.ARGB_8888, false);
                if (needsRecycle) {
                    workBitmap.recycle();
                }
                workBitmap = converted;
                needsRecycle = true;
            }
            
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int pixelStride = planes[0].getPixelStride();
            
            // Safety check: verify buffer has enough space
            int requiredBytes = rowStride * height;
            if (buffer.capacity() < requiredBytes) {
                Log.w(TAG, "Buffer too small: capacity=" + buffer.capacity() + 
                          ", required=" + requiredBytes);
                // Adjust our write to fit available space
            }
            
            buffer.rewind();
            
            // Get pixels from bitmap
            int[] pixels = new int[width * height];
            workBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            
            // Calculate actual row width in bytes
            int effectiveRowStride = Math.min(rowStride, width * pixelStride);
            
            // Write pixels to buffer with boundary checking
            for (int row = 0; row < height; row++) {
                int rowOffset = row * width;
                
                for (int col = 0; col < width && buffer.remaining() >= 4; col++) {
                    int pixel = pixels[rowOffset + col];
                    
                    // ARGB -> RGBA conversion
                    buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                    buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                    buffer.put((byte) (pixel & 0xFF));         // B
                    buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
                    
                    // Handle pixel stride > 4 with bounds checking
                    int extraBytes = pixelStride - 4;
                    for (int s = 0; s < extraBytes && buffer.hasRemaining(); s++) {
                        buffer.put((byte) 0);
                    }
                }
                
                // Handle row padding with bounds checking
                int actualWritten = width * pixelStride;
                int paddingNeeded = rowStride - actualWritten;
                for (int p = 0; p < paddingNeeded && buffer.hasRemaining(); p++) {
                    buffer.put((byte) 0);
                }
            }
            
            buffer.rewind();
            
            if (needsRecycle) {
                workBitmap.recycle();
            }
            
            Log.d(TAG, "RGBA safe write completed for " + width + "x" + height);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in writeRGBAToPlanesSafe", e);
            return false;
        }
    }
    
    /**
     * Validates image plane configuration for debugging purposes.
     */
    public static String validateImagePlanes(Image image) {
        if (image == null) {
            return "Image is null";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Format: ").append(getFormatName(image.getFormat()));
        sb.append(", Size: ").append(image.getWidth()).append("x").append(image.getHeight());
        
        Image.Plane[] planes = image.getPlanes();
        if (planes == null) {
            sb.append(", Planes: null");
        } else {
            sb.append(", Planes: ").append(planes.length);
            for (int i = 0; i < planes.length; i++) {
                Image.Plane plane = planes[i];
                sb.append("\n  Plane ").append(i).append(": ");
                sb.append("rowStride=").append(plane.getRowStride());
                sb.append(", pixelStride=").append(plane.getPixelStride());
                ByteBuffer buffer = plane.getBuffer();
                if (buffer != null) {
                    sb.append(", capacity=").append(buffer.capacity());
                    sb.append(", remaining=").append(buffer.remaining());
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Creates a test pattern bitmap for debugging camera injection.
     */
    public static Bitmap createTestPatternBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        Paint paint = new Paint();
        
        // Draw gradient background
        for (int y = 0; y < height; y++) {
            int color = android.graphics.Color.HSVToColor(new float[] {
                (y * 360f) / height, 0.8f, 0.8f
            });
            paint.setColor(color);
            canvas.drawLine(0, y, width, y, paint);
        }
        
        // Draw timestamp text
        paint.setColor(android.graphics.Color.WHITE);
        paint.setTextSize(Math.min(width, height) / 15f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK);
        
        String timestamp = String.valueOf(System.currentTimeMillis() % 100000);
        canvas.drawText("FAKE: " + timestamp, width / 2f, height / 2f, paint);
        
        // Draw corner markers
        paint.setColor(android.graphics.Color.RED);
        float markerSize = Math.min(width, height) / 10f;
        canvas.drawRect(0, 0, markerSize, markerSize, paint);
        canvas.drawRect(width - markerSize, 0, width, markerSize, paint);
        canvas.drawRect(0, height - markerSize, markerSize, height, paint);
        canvas.drawRect(width - markerSize, height - markerSize, width, height, paint);
        
        return bitmap;
    }
}
