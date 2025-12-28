package com.applisto.appcloner;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;

import top.canyie.pine.Pine;
import top.canyie.pine.PineConfig;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public final class LiveVideoHook {
    private static final String TAG = "LiveVideoHook";
    private static Context sContext;

    // Original Fake Surface (used for MediaRecorder/Codec replacement)
    private static Surface sFakeSurface;
    private static SurfaceTexture sFakeTexture;

    // Dummy Surface (used to fool Camera into outputting somewhere valid)
    private static Surface sDummySurface;
    private static SurfaceTexture sDummySurfaceTexture;

    private static MediaPlayer sMediaPlayer;

    private static String sOutputPath;
    private static ParcelFileDescriptor sOutputFd;
    private static boolean sIsRecording = false;
    private static HandlerThread sHandlerThread;
    private static Handler sHandler;
    private static boolean sHooksActive = false;
    private static boolean sMediaCodecHooksActive = false;
    private static boolean sFakePersistentSurfaceCreated = false;

    // File path for the fake video
    private static File sFakeVideoFile;

    // Camera 1 Players/Decoders
    private static MediaPlayer sC1MediaPlayer;
    private static VideoToFrames sC1VideoToFrames;
    private static volatile byte[] sC1LatestFrame; // Stores the latest decoded frame for callbacks

    // Camera 2 Players/Decoders
    private static Surface sC2PreviewSurface;
    private static MediaPlayer sC2MediaPlayer;

    private static Surface sC2ReaderSurface;
    private static VideoToFrames sC2VideoToFrames;

    // Registry to track Surface resolutions from ImageReader
    private static final Map<Surface, Size> sSurfaceResolutions = new WeakHashMap<>();

    private static class Size {
        final int width;
        final int height;
        Size(int w, int h) { width = w; height = h; }
    }

    public static void install(Context ctx) {
        if (sHooksActive) {
            Log.w(TAG, "Hooks already installed");
            return;
        }
        try {
            initializePine();
            sContext = ctx.getApplicationContext();
            sHandlerThread = new HandlerThread("VideoHookThread");
            sHandlerThread.start();
            sHandler = new Handler(sHandlerThread.getLooper());

            prepareFakeVideoFile();
            createFakeSurfaceSafe();
            createDummySurface(); // Create the dummy surface for camera redirection

            boolean success = installAllHooks();
            if (success) {
                sHooksActive = true;
                Log.i(TAG, "Video hooks installed successfully");
                sHandler.postDelayed(() -> verifyHooksActive(), 1000);
            } else {
                Log.e(TAG, "Failed to install some hooks");
            }
        } catch (Exception e) {
            Log.e(TAG, "Fatal error during hook installation", e);
        }
    }

    // Called by FakeCameraHook or internally to register Surface resolutions
    public static void registerSurfaceResolution(Surface surface, int width, int height) {
        if (surface != null && width > 0 && height > 0) {
            synchronized(sSurfaceResolutions) {
                sSurfaceResolutions.put(surface, new Size(width, height));
            }
            Log.d(TAG, "Registered surface resolution: " + width + "x" + height);
        }
    }

    private static void initializePine() {
        PineConfig.debug = true;
        PineConfig.debuggable = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PineConfig.antiChecks = true;
            PineConfig.disableHiddenApiPolicy = true;
            PineConfig.disableHiddenApiPolicyForPlatformDomain = true;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    Class<?> vmRuntimeClass = Class.forName("dalvik.system.VMRuntime");
                    Method getRuntimeMethod = vmRuntimeClass.getDeclaredMethod("getRuntime");
                    Object vmRuntime = getRuntimeMethod.invoke(null);
                    Method setHiddenApiExemptionsMethod = vmRuntimeClass.getDeclaredMethod("setHiddenApiExemptions", String[].class);
                    setHiddenApiExemptionsMethod.invoke(vmRuntime, (Object) new String[]{"L"});
                    Log.i(TAG, "Applied additional hidden API exemptions for Android 13+");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to apply additional hidden API exemptions", e);
                }
            }
        }
        Log.i(TAG, "Pine framework initialized");
    }

    private static void prepareFakeVideoFile() {
        try {
            sFakeVideoFile = createFakeVideoFile();
            if (sFakeVideoFile != null && sFakeVideoFile.exists() && sFakeVideoFile.length() > 0) {
                Log.i(TAG, "Fake video file ready: " + sFakeVideoFile.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to prepare fake video file.");
                sFakeVideoFile = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error preparing fake video file", e);
            sFakeVideoFile = null;
        }
    }

    private static void createFakeSurfaceSafe() {
        try {
            sFakeSurface = MediaCodec.createPersistentInputSurface();
            sFakePersistentSurfaceCreated = true;
            Log.i(TAG, "Created persistent fake surface");

            // Fix: SurfaceTexture(int, boolean) does not exist.
            // Using SurfaceTexture(int) for API 11+
            sFakeTexture = new SurfaceTexture(0);
            sFakeTexture.detachFromGLContext();
        } catch (Exception e1) {
            Log.e(TAG, "Failed to create persistent fake surface", e1);
            sFakePersistentSurfaceCreated = false;
        }
    }

    // Create a dummy surface to redirect the real camera output to
    private static void createDummySurface() {
        try {
            sDummySurfaceTexture = new SurfaceTexture(10); // Arbitrary texture ID
            sDummySurface = new Surface(sDummySurfaceTexture);
            Log.i(TAG, "Created dummy surface for camera redirection");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create dummy surface", e);
        }
    }

    private static boolean installAllHooks() {
        boolean allSuccess = true;
        allSuccess &= hookCameraVideoSource();
        allSuccess &= hookMediaRecorderSurface();

        // New aggressive hooks
        allSuccess &= hookCamera1Hooks();
        allSuccess &= hookCamera2Hooks();

        allSuccess &= hookMediaCodec();
        return allSuccess;
    }

    // --------------------------------------------------------------------------
    // Camera 1 Hooks
    // --------------------------------------------------------------------------

    private static boolean hookCamera1Hooks() {
        boolean success = true;
        try {
            // Hook setPreviewTexture (SurfaceTexture)
            Method setPreviewTexture = Camera.class.getDeclaredMethod("setPreviewTexture", SurfaceTexture.class);
            Hooking.pineHook(setPreviewTexture, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    Log.i(TAG, "Camera.setPreviewTexture intercepted");
                    SurfaceTexture appTexture = (SurfaceTexture) cf.args[0];
                    if (appTexture != null && appTexture != sDummySurfaceTexture) {
                        // Start MediaPlayer on this texture
                        startCamera1MediaPlayer(new Surface(appTexture));
                        // Redirect Camera to Dummy
                        cf.args[0] = sDummySurfaceTexture;
                    }
                }
            });

            // Hook setPreviewDisplay (SurfaceHolder)
            Method setPreviewDisplay = Camera.class.getDeclaredMethod("setPreviewDisplay", SurfaceHolder.class);
            Hooking.pineHook(setPreviewDisplay, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    Log.i(TAG, "Camera.setPreviewDisplay intercepted");
                    SurfaceHolder appHolder = (SurfaceHolder) cf.args[0];
                    if (appHolder != null) {
                        // Start MediaPlayer on this surface
                        startCamera1MediaPlayer(appHolder.getSurface());

                        try {
                            Camera camera = (Camera) cf.thisObject;
                            camera.setPreviewTexture(sDummySurfaceTexture);
                            Log.i(TAG, "Redirected setPreviewDisplay to setPreviewTexture(dummy)");
                            cf.setResult(null); // Cancel original call
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to redirect setPreviewDisplay", e);
                        }
                    }
                }
            });

            // Hook startPreview to ensure players are playing
            Method startPreview = Camera.class.getDeclaredMethod("startPreview");
            Hooking.pineHook(startPreview, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    Log.i(TAG, "Camera.startPreview intercepted");
                    if (sC1MediaPlayer != null && !sC1MediaPlayer.isPlaying()) {
                        sC1MediaPlayer.start();
                    }
                    // If we need frame callbacks, ensure decoder is running
                    if (sC1VideoToFrames == null && sFakeVideoFile != null) {
                        startCamera1Decoder();
                    }
                }
            });

            // Hook Preview Callbacks
            hookCamera1PreviewCallbacks();

        } catch (Exception e) {
            Log.e(TAG, "Camera1 hooks failed", e);
            success = false;
        }
        return success;
    }

    private static void startCamera1MediaPlayer(Surface surface) {
        try {
            if (sC1MediaPlayer != null) {
                try {
                    sC1MediaPlayer.release();
                } catch (Exception e) {}
                sC1MediaPlayer = null;
            }
            if (sFakeVideoFile == null) return;

            sC1MediaPlayer = new MediaPlayer();
            sC1MediaPlayer.setSurface(surface);
            sC1MediaPlayer.setDataSource(sFakeVideoFile.getAbsolutePath());
            sC1MediaPlayer.setLooping(true);
            sC1MediaPlayer.setVolume(0, 0);
            sC1MediaPlayer.prepare();
            sC1MediaPlayer.start();
            Log.i(TAG, "Camera1 MediaPlayer started on app surface");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Camera1 MediaPlayer", e);
        }
    }

    private static void startCamera1Decoder() {
        if (sC1VideoToFrames != null) {
            sC1VideoToFrames.stopDecode();
        }
        sC1VideoToFrames = new VideoToFrames();
        try {
            sC1VideoToFrames.setSaveFrames("", VideoToFrames.OutputImageFormat.NV21);
            sC1VideoToFrames.setFrameCallback(data -> sC1LatestFrame = data);
            sC1VideoToFrames.decode(sFakeVideoFile.getAbsolutePath());
            Log.i(TAG, "Camera1 Frame Decoder started");
        } catch (Throwable e) {
            Log.e(TAG, "Failed to start Camera1 Frame Decoder", e);
        }
    }

    private static void hookCamera1PreviewCallbacks() {
        String[] methods = {"setPreviewCallback", "setPreviewCallbackWithBuffer", "setOneShotPreviewCallback"};
        for (String methodName : methods) {
            try {
                Method method = Camera.class.getDeclaredMethod(methodName, Camera.PreviewCallback.class);
                Hooking.pineHook(method, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                        final Camera.PreviewCallback original = (Camera.PreviewCallback) cf.args[0];
                        if (original != null) {
                            // Ensure decoder is running if callback is set
                            if (sC1VideoToFrames == null && sFakeVideoFile != null) {
                                startCamera1Decoder();
                            }

                            cf.args[0] = new Camera.PreviewCallback() {
                                @Override
                                public void onPreviewFrame(byte[] data, Camera camera) {
                                    // Inject fake frame data
                                    if (sC1LatestFrame != null && data != null) {
                                        int len = Math.min(data.length, sC1LatestFrame.length);
                                        System.arraycopy(sC1LatestFrame, 0, data, 0, len);
                                    }
                                    original.onPreviewFrame(data, camera);
                                }
                            };
                        }
                    }
                });
            } catch (Exception e) {
                 // Ignore missing methods
            }
        }
    }

    // --------------------------------------------------------------------------
    // Camera 2 Hooks (Aggressive Surface Replacement)
    // --------------------------------------------------------------------------

    private static boolean hookCamera2Hooks() {
        boolean success = true;
        try {
            // Hook CaptureRequest.Builder.addTarget to capture surfaces
            Class<?> builderClass = Class.forName("android.hardware.camera2.CaptureRequest$Builder");
            Method addTarget = builderClass.getDeclaredMethod("addTarget", Surface.class);
            Hooking.pineHook(addTarget, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    Surface target = (Surface) cf.args[0];
                    if (target == null || target == sDummySurface) return;

                    // Identify surface type
                    String str = target.toString();
                    Log.i(TAG, "CaptureRequest.addTarget: " + str);

                    if (str.contains("Surface(name=null)")) {
                        // Likely an ImageReader surface
                        handleCamera2ReaderSurface(target);
                    } else {
                        // Likely a Preview surface
                        handleCamera2PreviewSurface(target);
                    }

                    // Replace with dummy surface
                    cf.args[0] = sDummySurface;
                }
            });

            // Hook removeTarget to clean up if needed
            Method removeTarget = builderClass.getDeclaredMethod("removeTarget", Surface.class);
            Hooking.pineHook(removeTarget, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    // No-op for now
                }
            });

            // Hook session creation methods to inject dummy surface in configuration
            hookCamera2SessionCreation();

        } catch (Exception e) {
            Log.e(TAG, "Camera2 hooks failed", e);
            success = false;
        }
        return success;
    }

    private static void handleCamera2PreviewSurface(Surface surface) {
        // Robustness: Avoid re-initializing if surface is same
        if (surface == sC2PreviewSurface) return;

        // Release previous player if surface changed
        if (sC2MediaPlayer != null) {
            try {
                if (sC2MediaPlayer.isPlaying()) sC2MediaPlayer.stop();
                sC2MediaPlayer.release();
            } catch(Exception e){}
            sC2MediaPlayer = null;
        }

        sC2PreviewSurface = surface;
        if (surface == null || !surface.isValid()) return;

        try {
            if (sFakeVideoFile == null) return;

            sC2MediaPlayer = new MediaPlayer();
            sC2MediaPlayer.setSurface(surface);
            sC2MediaPlayer.setDataSource(sFakeVideoFile.getAbsolutePath());
            sC2MediaPlayer.setLooping(true);
            sC2MediaPlayer.setVolume(0, 0);
            sC2MediaPlayer.setOnPreparedListener(mp -> mp.start());
            sC2MediaPlayer.prepare();
            Log.i(TAG, "Camera2 Preview Player started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Camera2 Preview Player", e);
        }
    }

    private static void handleCamera2ReaderSurface(Surface surface) {
        // Robustness: Avoid re-initializing if surface is same
        if (surface == sC2ReaderSurface) return;

        if (sC2VideoToFrames != null) {
            sC2VideoToFrames.stopDecode();
        }

        sC2ReaderSurface = surface;
        if (surface == null || !surface.isValid()) return;

        try {
            sC2VideoToFrames = new VideoToFrames();
            // Render directly to surface (Surface Mode)
            // Fix: Use correct method name set_surfcae (typo in VideoToFrames source)
            sC2VideoToFrames.set_surfcae(surface);

            // Check if we have a registered resolution for this surface
            synchronized(sSurfaceResolutions) {
                Size size = sSurfaceResolutions.get(surface);
                if (size != null) {
                    Log.i(TAG, "Setting target resolution for reader surface: " + size.width + "x" + size.height);
                    sC2VideoToFrames.setTargetResolution(size.width, size.height);
                } else {
                    Log.w(TAG, "No resolution found for reader surface, using video resolution");
                }
            }

            sC2VideoToFrames.setSaveFrames(null, VideoToFrames.OutputImageFormat.NV21);
            sC2VideoToFrames.decode(sFakeVideoFile.getAbsolutePath());
            Log.i(TAG, "Camera2 Reader Decoder started");
        } catch (Throwable e) {
            Log.e(TAG, "Failed to start Camera2 Reader Decoder", e);
        }
    }

    private static void hookCamera2SessionCreation() {
        try {
            // Hook CameraDevice methods
            hookSessionCreation(CameraDevice.class);

            // Hook CameraDeviceImpl methods (hidden API)
            try {
                Class<?> implClass = Class.forName("android.hardware.camera2.impl.CameraDeviceImpl");
                Log.i(TAG, "Found CameraDeviceImpl, hooking session creation methods");
                hookSessionCreation(implClass);
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "CameraDeviceImpl not found, skipping specific hooks");
            }

        } catch (Exception e) {
            Log.e(TAG, "Camera2 session hooks failed", e);
        }
    }

    private static void hookSessionCreation(Class<?> clazz) {
        // 1. createCaptureSession(List<Surface>, ...)
        try {
            Method createSession = clazz.getDeclaredMethod(
                    "createCaptureSession", List.class,
                    CameraCaptureSession.StateCallback.class, Handler.class);
            Hooking.pineHook(createSession, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    Log.i(TAG, "Replacing outputs in createCaptureSession(List) for " + clazz.getSimpleName());
                    cf.args[0] = Arrays.asList(sDummySurface);
                }
            });
        } catch (Exception e) {
            // Method might not exist on this class/version
        }

        // 2. createCaptureSessionByOutputConfigurations (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Method createSession = clazz.getDeclaredMethod(
                    "createCaptureSessionByOutputConfigurations", List.class,
                    CameraCaptureSession.StateCallback.class, Handler.class);
                Hooking.pineHook(createSession, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                         Log.i(TAG, "Replacing outputs in createCaptureSessionByOutputConfigurations for " + clazz.getSimpleName());
                         try {
                             Class<?> outConfigClass = Class.forName("android.hardware.camera2.params.OutputConfiguration");
                             Object dummyConfig = outConfigClass.getConstructor(Surface.class).newInstance(sDummySurface);
                             cf.args[0] = Arrays.asList(dummyConfig);
                         } catch (Exception ex) {
                             Log.e(TAG, "Failed to create dummy OutputConfiguration", ex);
                         }
                    }
                });
            } catch (Exception e) {}
        }

        // 3. createConstrainedHighSpeedCaptureSession (API 23+)
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Method createSession = clazz.getDeclaredMethod(
                    "createConstrainedHighSpeedCaptureSession", List.class,
                    CameraCaptureSession.StateCallback.class, Handler.class);
                Hooking.pineHook(createSession, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                        Log.i(TAG, "Replacing outputs in createConstrainedHighSpeedCaptureSession for " + clazz.getSimpleName());
                        cf.args[0] = Arrays.asList(sDummySurface);
                    }
                });
            } catch (Exception e) {}
         }

        // 4. createCaptureSession(SessionConfiguration) (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                Class<?> sessionConfigClass = Class.forName("android.hardware.camera2.params.SessionConfiguration");
                Method createSession = clazz.getDeclaredMethod("createCaptureSession", sessionConfigClass);
                Hooking.pineHook(createSession, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                         Log.i(TAG, "Replacing outputs in createCaptureSession(SessionConfiguration) for " + clazz.getSimpleName());
                         try {
                             Object originalConfig = cf.args[0];
                             Class<?> outConfigClass = Class.forName("android.hardware.camera2.params.OutputConfiguration");
                             Object dummyConfig = outConfigClass.getConstructor(Surface.class).newInstance(sDummySurface);

                             Method getSessionType = sessionConfigClass.getMethod("getSessionType");
                             Method getExecutor = sessionConfigClass.getMethod("getExecutor");
                             Method getStateCallback = sessionConfigClass.getMethod("getStateCallback");

                             Object newConfig = sessionConfigClass.getConstructor(
                                 int.class, List.class, Executor.class, CameraCaptureSession.StateCallback.class
                             ).newInstance(
                                 getSessionType.invoke(originalConfig),
                                 Arrays.asList(dummyConfig),
                                 getExecutor.invoke(originalConfig),
                                 getStateCallback.invoke(originalConfig)
                             );
                             cf.args[0] = newConfig;
                         } catch (Exception ex) {
                             Log.e(TAG, "Failed to replace SessionConfiguration", ex);
                         }
                    }
                });
            } catch (Exception e) {}
        }

        // 5. createReprocessableCaptureSession (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Method createSession = clazz.getDeclaredMethod(
                    "createReprocessableCaptureSession", InputConfiguration.class, List.class,
                    CameraCaptureSession.StateCallback.class, Handler.class);
                Hooking.pineHook(createSession, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                        Log.i(TAG, "Replacing outputs in createReprocessableCaptureSession for " + clazz.getSimpleName());
                        // Replace List<Surface> outputs with dummy surface list
                        cf.args[1] = Arrays.asList(sDummySurface);
                        // NOTE: InputConfiguration might also need tweaking if it refers to the original surfaces,
                        // but usually it refers to dimensions. If the dummy surface dimensions mismatch, this might fail.
                        // For now, we assume standard redirect.
                    }
                });
            } catch (Exception e) {}
        }

        // 6. createReprocessableCaptureSessionByConfigurations (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Method createSession = clazz.getDeclaredMethod(
                    "createReprocessableCaptureSessionByConfigurations", InputConfiguration.class, List.class,
                    CameraCaptureSession.StateCallback.class, Handler.class);
                Hooking.pineHook(createSession, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                        Log.i(TAG, "Replacing outputs in createReprocessableCaptureSessionByConfigurations for " + clazz.getSimpleName());
                        try {
                             Class<?> outConfigClass = Class.forName("android.hardware.camera2.params.OutputConfiguration");
                             Object dummyConfig = outConfigClass.getConstructor(Surface.class).newInstance(sDummySurface);
                             cf.args[1] = Arrays.asList(dummyConfig);
                         } catch (Exception ex) {
                             Log.e(TAG, "Failed to create dummy OutputConfiguration", ex);
                         }
                    }
                });
            } catch (Exception e) {}
        }
    }


    /* ====================================================== */
    /* MediaRecorder/Codec Hooks (Fallback/Legacy)            */
    /* ====================================================== */

    private static boolean hookCameraVideoSource() {
        boolean hooked = false;
        try {
            Method unlock = Camera.class.getDeclaredMethod("unlock");
            Hooking.pineHook(unlock, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    if (sIsRecording) cf.setResult(null);
                }
            });
            hooked = true;
        } catch (Exception e) {}
        return hooked;
    }

    private static boolean hookMediaRecorderSurface() {
        boolean hooked = false;
        try {
            // Hook setCamera
            try {
                Method setCamera = MediaRecorder.class.getDeclaredMethod("setCamera", Camera.class);
                Hooking.pineHook(setCamera, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                        cf.args[0] = null;
                        sIsRecording = true;
                    }
                });
                hooked = true;
            } catch (Exception e) {}

            Method setVideoSource = MediaRecorder.class.getDeclaredMethod("setVideoSource", int.class);
            Hooking.pineHook(setVideoSource, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    int source = (int) cf.args[0];
                    if (source == MediaRecorder.VideoSource.CAMERA || source == MediaRecorder.VideoSource.DEFAULT) {
                        cf.args[0] = MediaRecorder.VideoSource.SURFACE;
                        sIsRecording = true;
                    }
                }
            });

            Method getSurface = MediaRecorder.class.getDeclaredMethod("getSurface");
            Hooking.pineHook(getSurface, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame cf) {
                    // Return fake surface for recorder
                    if (sFakeSurface != null) cf.setResult(sFakeSurface);
                }
            });

             // Track output file
            Method setOutputFileStr = MediaRecorder.class.getDeclaredMethod("setOutputFile", String.class);
            Hooking.pineHook(setOutputFileStr, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    sOutputPath = (String) cf.args[0];
                    sOutputFd = null;
                }
            });

            try {
                Method setOutputFd = MediaRecorder.class.getDeclaredMethod("setOutputFile", ParcelFileDescriptor.class);
                Hooking.pineHook(setOutputFd, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                        sOutputFd = (ParcelFileDescriptor) cf.args[0];
                        sOutputPath = null;
                    }
                });
            } catch (Exception e) {}

            Method prepare = MediaRecorder.class.getDeclaredMethod("prepare");
            Hooking.pineHook(prepare, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    if (sIsRecording) ensureFakeSetup(cf.thisObject);
                }
            });

            Method start = MediaRecorder.class.getDeclaredMethod("start");
            Hooking.pineHook(start, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    startPlayback();
                }
            });

            Method stop = MediaRecorder.class.getDeclaredMethod("stop");
            Hooking.pineHook(stop, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    stopPlayback();
                }
                @Override
                public void afterCall(Pine.CallFrame cf) {
                    if (sIsRecording) {
                        sIsRecording = false;
                    }
                }
            });

            hooked = true;
        } catch (Exception e) {
            hooked = false;
        }
        return hooked;
    }

    private static void ensureFakeSetup(Object recorder) {
        try {
            String fieldName = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? "mNativeSurface" : "mSurface";
            Field surfaceField = MediaRecorder.class.getDeclaredField(fieldName);
            surfaceField.setAccessible(true);
            surfaceField.set(recorder, sFakeSurface);
        } catch (Exception e) {}
    }

    private static boolean hookMediaCodec() {
        boolean hooked = false;
        try {
            Method createPersistentInputSurface = MediaCodec.class.getDeclaredMethod("createPersistentInputSurface");
            Hooking.pineHook(createPersistentInputSurface, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame cf) {
                    cf.setResult(sFakeSurface);
                    sIsRecording = true;
                    sMediaCodecHooksActive = true;
                }
            });

            // Hook configure to detect recording
             Method configure = MediaCodec.class.getDeclaredMethod("configure",
                android.media.MediaFormat.class, Surface.class, android.media.MediaCrypto.class, int.class);
            Hooking.pineHook(configure, new MethodHook() {
                 @Override
                 public void beforeCall(Pine.CallFrame cf) {
                     if (cf.args[1] != null) sIsRecording = true;
                 }
            });

            Method start = MediaCodec.class.getDeclaredMethod("start");
            Hooking.pineHook(start, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    startPlayback();
                }
            });

            Method stop = MediaCodec.class.getDeclaredMethod("stop");
            Hooking.pineHook(stop, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame cf) {
                    stopPlayback();
                    if (sIsRecording && sMediaCodecHooksActive) {
                        sIsRecording = false;
                        ensureFakeVideoExists();
                    }
                }
            });

            hooked = true;
        } catch (Exception e) {}
        return hooked;
    }

    // ... [Helper methods copyFile, ensureFakeVideoExists same as before] ...
    private static void ensureFakeVideoExists() {
        try {
            if (!sMediaCodecHooksActive && !sIsRecording) return;
            File fakeSource = createFakeVideoFile();
            if (fakeSource == null) return;

            if (sOutputPath != null) {
                copyFile(fakeSource, new File(sOutputPath));
            } else if (sOutputFd != null) {
                FileInputStream in = new FileInputStream(fakeSource);
                FileOutputStream out = new FileOutputStream(sOutputFd.getFileDescriptor());
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                in.close();
                out.getFD().sync();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to ensure fake video exists", e);
        }
    }

    private static File createFakeVideoFile() throws Exception {
        File cacheDir = sContext.getCacheDir();
        File fakeVideoInCache = new File(cacheDir, "fake_video.mp4");
        if (fakeVideoInCache.exists() && fakeVideoInCache.length() > 0 && fakeVideoInCache.canWrite()) {
            return fakeVideoInCache;
        }
        InputStream in = sContext.getAssets().open("fake_video.mp4");
        FileOutputStream out = new FileOutputStream(fakeVideoInCache);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.getFD().sync();
        out.close();
        return fakeVideoInCache;
    }

    private static void copyFile(File src, File dst) throws Exception {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close();
        out.getFD().sync();
        out.close();
    }

    private static void startPlayback() {
        if (sMediaPlayer != null && !sMediaPlayer.isPlaying()) {
            sMediaPlayer.start();
        }
    }

    private static void stopPlayback() {
        if (sMediaPlayer != null && sMediaPlayer.isPlaying()) {
            sMediaPlayer.pause();
        }
    }

    public static boolean verifyHooksActive() {
         return sHooksActive;
    }

    private static void initializeMediaPlayer() {
        if (sFakeVideoFile == null || sFakeSurface == null) return;
        try {
            if (sMediaPlayer != null) sMediaPlayer.release();
            sMediaPlayer = new MediaPlayer();
            sMediaPlayer.setSurface(sFakeSurface);
            sMediaPlayer.setDataSource(sFakeVideoFile.getAbsolutePath());
            sMediaPlayer.setLooping(true);
            sMediaPlayer.setVolume(0, 0);
            sMediaPlayer.prepare();
        } catch (Exception e) {}
    }
}
