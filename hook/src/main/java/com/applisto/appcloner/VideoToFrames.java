package com.applisto.appcloner;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

public class VideoToFrames implements Runnable {
    private static final String TAG = "VideoToFrames";
    private static final boolean VERBOSE = false;
    private static final long DEFAULT_TIMEOUT_US = 10000;

    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;

    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

    private LinkedBlockingQueue<byte[]> mQueue;
    private OutputImageFormat outputImageFormat;
    private boolean stopDecode = false;

    private String videoFilePath;
    private Throwable throwable;
    private Thread childThread;
    private Surface play_surf;

    private Callback callback;
    private FrameCallback frameCallback;

    // Target resolution (e.g., set by CameraX requirement)
    private int mTargetWidth = -1;
    private int mTargetHeight = -1;

    public interface Callback {
        void onFinishDecode();
        void onDecodeFrame(int index);
    }

    public interface FrameCallback {
        void onFrameDecoded(byte[] data);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setFrameCallback(FrameCallback frameCallback) {
        this.frameCallback = frameCallback;
    }

    public void setEnqueue(LinkedBlockingQueue<byte[]> queue) {
        mQueue = queue;
    }

    public void setSaveFrames(String dir, OutputImageFormat imageFormat) throws IOException {
        outputImageFormat = imageFormat;
    }

    public void set_surfcae(Surface player_surface) {
        if (player_surface != null) {
            play_surf = player_surface;
        }
    }

    public void setTargetResolution(int width, int height) {
        this.mTargetWidth = width;
        this.mTargetHeight = height;
    }

    public void stopDecode() {
        stopDecode = true;
        if (childThread != null) {
            childThread.interrupt();
        }
    }

    public void decode(String videoFilePath) throws Throwable {
        this.videoFilePath = videoFilePath;
        if (childThread == null) {
            childThread = new Thread(this, "decode");
            childThread.start();
            if (throwable != null) {
                throw throwable;
            }
        }
    }

    public void run() {
        try {
            videoDecode(videoFilePath);
        } catch (Throwable t) {
            throwable = t;
            Log.e(TAG, "Decode error", t);
        }
    }

    @SuppressLint("WrongConstant")
    public void videoDecode(String videoFilePath) throws IOException {
        Log.i(TAG, "Start decoding: " + videoFilePath);
        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        try {
            File videoFile = new File(videoFilePath);
            if (!videoFile.exists()) {
                Log.e(TAG, "Video file not found: " + videoFilePath);
                return;
            }

            extractor = new MediaExtractor();
            extractor.setDataSource(videoFilePath);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                Log.e(TAG, "No video track found in " + videoFilePath);
                return;
            }
            extractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);

            if (isColorFormatSupported(decodeColorFormat, decoder.getCodecInfo().getCapabilitiesForType(mime))) {
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
                Log.d(TAG, "set decode color format to type " + decodeColorFormat);
            } else {
                Log.i(TAG, "unable to set decode color format, color format type " + decodeColorFormat + " not supported");
            }

            // Apply target resolution if set
            if (mTargetWidth > 0 && mTargetHeight > 0) {
                Log.i(TAG, "Configuring decoder for target resolution: " + mTargetWidth + "x" + mTargetHeight);
                // We use MediaFormat scaling if possible, or expect the surface consumer to handle it
                // Ideally, we tell the codec what size we want
                mediaFormat.setInteger(MediaFormat.KEY_WIDTH, mTargetWidth);
                mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, mTargetHeight);
                // Some codecs might need this to enable scaling
                // mediaFormat.setInteger("max-width", mTargetWidth);
                // mediaFormat.setInteger("max-height", mTargetHeight);

                // Set scaling mode to scale to fit
                if (play_surf != null) {
                    try {
                        decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to set video scaling mode", e);
                    }
                }
            }

            decodeFramesToImage(decoder, extractor, mediaFormat);

            // Loop functionality
            try {
                decoder.stop();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping decoder before loop", e);
            }

            while (!stopDecode) {
                Log.d(TAG, "Looping video...");
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                // Re-configure and start is usually safer than just flushing for some codecs
                // but decodeFramesToImage handles configure/start.
                decodeFramesToImage(decoder, extractor, mediaFormat);
                try {
                    decoder.stop();
                } catch (Exception e) {
                    Log.w(TAG, "Error stopping decoder in loop", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Video decode exception", e);
        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                    decoder.release();
                } catch (Exception e) {
                    // Ignore
                }
                decoder = null;
            }
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }
    }

    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
        StringBuilder sb = new StringBuilder("supported color format: ");
        for (int c : caps.colorFormats) {
            sb.append(c).append("\t");
        }
        Log.d(TAG, sb.toString());
    }

    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
    }

    private void decodeFramesToImage(MediaCodec decoder, MediaExtractor extractor, MediaFormat mediaFormat) {
        boolean is_first = false;
        long startWhen = 0;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        try {
            decoder.configure(mediaFormat, play_surf, null, 0);
            decoder.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start decoder", e);
            return;
        }

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        // final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        // final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        int outputFrameCount = 0;

        while (!sawOutputEOS && !stopDecode) {
            if (!sawInputEOS) {
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                    }
                }
            }

            int outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
            if (outputBufferId >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
                boolean doRender = (info.size != 0);
                if (doRender) {
                    outputFrameCount++;
                    if (callback != null) {
                        callback.onDecodeFrame(outputFrameCount);
                    }
                    if (!is_first) {
                        startWhen = System.currentTimeMillis();
                        is_first = true;
                    }

                    if (play_surf == null) {
                        // Buffer mode
                        Image image = decoder.getOutputImage(outputBufferId);
                        if (image != null) {
                            if (outputImageFormat != null) {
                                byte[] data = getDataFromImage(image, COLOR_FormatNV21);
                                if (frameCallback != null) {
                                    frameCallback.onFrameDecoded(data);
                                }
                                if (mQueue != null) {
                                    try {
                                        mQueue.put(data);
                                    } catch (InterruptedException e) {
                                        Log.w(TAG, "Queue put interrupted", e);
                                    }
                                }
                            }
                            image.close();
                        }
                    }

                    // Throttle playback speed
                    long presentationTimeUs = info.presentationTimeUs;
                    long timeSinceStart = (System.currentTimeMillis() - startWhen);
                    // Adjust presentation time base
                    long sleepTime = (presentationTimeUs / 1000) - timeSinceStart;

                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            Log.w(TAG, "Sleep interrupted");
                        }
                    }

                    decoder.releaseOutputBuffer(outputBufferId, true);
                }
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                 // Format changed
                 Log.d(TAG, "Output format changed: " + decoder.getOutputFormat());
            }
        }
        if (callback != null) {
            callback.onFinishDecode();
        }
    }

    private static int selectTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if (VERBOSE) {
                    Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                }
                return i;
            }
        }
        return -1;
    }

    private static boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }

    private static byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        if (VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (VERBOSE) {
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);
                Log.v(TAG, "buffer size " + buffer.remaining());
            }
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
            if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
        }
        return data;
    }

    public enum OutputImageFormat {
        I420("I420"),
        NV21("NV21"),
        JPEG("JPEG");
        private final String friendlyName;

        OutputImageFormat(String friendlyName) {
            this.friendlyName = friendlyName;
        }

        public String toString() {
            return friendlyName;
        }
    }
}
