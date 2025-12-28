package com.applisto.appcloner;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * ProofaceIntegrationHook
 * 
 * This hook provides deep integration with the Prooface SDK used by SumSub
 * for liveness detection. It addresses several issues seen in logs:
 * 
 * 1. "Prooface.onResult" being called prematurely or repeatedly
 * 2. Session timing issues
 * 3. Frame processing synchronization
 * 4. Network request handling
 * 
 * The solution involves:
 * 1. Managing Prooface session lifecycle
 * 2. Coordinating with FakeCameraHook for frame injection timing
 * 3. Handling callback coordination
 * 4. Providing workarounds for SDK-specific issues
 */
public final class ProofaceIntegrationHook {
    private static final String TAG = "ProofaceIntegration";
    
    private static volatile boolean sInitialized = false;
    private static Context sContext;
    private static Handler sMainHandler;
    
    // Prooface session state
    private static final AtomicBoolean sProofaceActive = new AtomicBoolean(false);
    private static final AtomicBoolean sProofaceSessionStarted = new AtomicBoolean(false);
    private static final AtomicLong sSessionStartTime = new AtomicLong(0);
    private static final AtomicInteger sFramesProcessed = new AtomicInteger(0);
    private static final AtomicInteger sResultCallbackCount = new AtomicInteger(0);
    
    // Configuration
    private static final long MIN_SESSION_DURATION_MS = 2000;  // Minimum session duration
    private static final int MIN_FRAMES_BEFORE_RESULT = 30;    // Minimum frames before allowing result
    
    // Callback interception
    private static Object sOriginalCallback;
    
    /**
     * Initialize the Prooface integration hooks.
     */
    public static void install(Context ctx) {
        if (sInitialized) {
            Log.w(TAG, "ProofaceIntegrationHook already installed");
            return;
        }
        
        sContext = ctx.getApplicationContext();
        sMainHandler = new Handler(Looper.getMainLooper());
        
        try {
            // Hook Prooface SDK classes
            hookProofaceClass();
            hookProofaceCallback();
            hookProofaceSession();
            hookProofaceProcessor();
            hookSumSubTracking();
            
            sInitialized = true;
            Log.i(TAG, "ProofaceIntegrationHook installed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to install ProofaceIntegrationHook", e);
        }
    }
    
    /**
     * Check if Prooface is currently active.
     */
    public static boolean isProofaceActive() {
        return sProofaceActive.get();
    }
    
    /**
     * Notify that a frame was processed.
     */
    public static void onFrameProcessed() {
        sFramesProcessed.incrementAndGet();
    }
    
    /**
     * Check if enough frames have been processed for a valid result.
     */
    public static boolean hasEnoughFrames() {
        return sFramesProcessed.get() >= MIN_FRAMES_BEFORE_RESULT;
    }
    
    /**
     * Check if the session has run long enough.
     */
    public static boolean hasEnoughTime() {
        long elapsed = System.currentTimeMillis() - sSessionStartTime.get();
        return elapsed >= MIN_SESSION_DURATION_MS;
    }
    
    // ==================== Hook Implementations ====================
    
    /**
     * Hook the main Prooface class
     */
    private static void hookProofaceClass() {
        String[] classNames = {
            "com.prooface.sdk.Prooface",
            "com.sumsub.sns.prooface.Prooface"
        };
        
        for (String className : classNames) {
            try {
                Class<?> proofaceClass = Class.forName(className);
                
                // Hook start method
                for (Method method : proofaceClass.getDeclaredMethods()) {
                    String methodName = method.getName();
                    
                    if (methodName.equals("start") || methodName.equals("startLiveness")) {
                        Hooking.pineHook(method, new MethodHook() {
                            @Override
                            public void beforeCall(Pine.CallFrame callFrame) {
                                Log.i(TAG, "Prooface.start() called");
                                onProofaceStart();
                            }
                            
                            @Override
                            public void afterCall(Pine.CallFrame callFrame) {
                                Log.i(TAG, "Prooface.start() completed");
                            }
                        });
                    } else if (methodName.equals("stop") || methodName.equals("stopLiveness")) {
                        Hooking.pineHook(method, new MethodHook() {
                            @Override
                            public void afterCall(Pine.CallFrame callFrame) {
                                Log.i(TAG, "Prooface.stop() called");
                                onProofaceStop();
                            }
                        });
                    } else if (methodName.equals("setCallback")) {
                        Hooking.pineHook(method, new MethodHook() {
                            @Override
                            public void beforeCall(Pine.CallFrame callFrame) {
                                // Intercept callback to wrap it
                                if (callFrame.args.length > 0 && callFrame.args[0] != null) {
                                    sOriginalCallback = callFrame.args[0];
                                    Object wrappedCallback = createCallbackProxy(sOriginalCallback);
                                    if (wrappedCallback != null) {
                                        callFrame.args[0] = wrappedCallback;
                                        Log.d(TAG, "Wrapped Prooface callback");
                                    }
                                }
                            }
                        });
                    }
                }
                
                Log.d(TAG, "Hooked Prooface class: " + className);
            } catch (ClassNotFoundException e) {
                // Class not present
            } catch (Exception e) {
                Log.w(TAG, "Error hooking Prooface class: " + className, e);
            }
        }
    }
    
    /**
     * Hook Prooface callback interfaces
     */
    private static void hookProofaceCallback() {
        String[] callbackClasses = {
            "com.prooface.sdk.Prooface$Callback",
            "com.prooface.sdk.Prooface$LivenessCallback",
            "com.sumsub.sns.prooface.Prooface$Callback"
        };
        
        for (String className : callbackClasses) {
            try {
                Class<?> callbackClass = Class.forName(className);
                
                for (Method method : callbackClass.getDeclaredMethods()) {
                    String methodName = method.getName();
                    
                    if (methodName.equals("onResult")) {
                        Hooking.pineHook(method, new MethodHook() {
                            @Override
                            public void beforeCall(Pine.CallFrame callFrame) {
                                int count = sResultCallbackCount.incrementAndGet();
                                Log.d(TAG, "Prooface.onResult callback #" + count);
                                
                                // Check if result is premature
                                if (!hasEnoughTime() || !hasEnoughFrames()) {
                                    Log.w(TAG, "Result callback seems premature. " +
                                              "Frames: " + sFramesProcessed.get() + "/" + MIN_FRAMES_BEFORE_RESULT +
                                              ", Time: " + (System.currentTimeMillis() - sSessionStartTime.get()) + 
                                              "/" + MIN_SESSION_DURATION_MS + "ms");
                                }
                            }
                            
                            @Override
                            public void afterCall(Pine.CallFrame callFrame) {
                                // Session may end after result
                                Log.d(TAG, "Prooface.onResult completed");
                            }
                        });
                    } else if (methodName.equals("onError")) {
                        Hooking.pineHook(method, new MethodHook() {
                            @Override
                            public void beforeCall(Pine.CallFrame callFrame) {
                                Log.w(TAG, "Prooface.onError callback triggered");
                                // Log error details if available
                                if (callFrame.args.length > 0) {
                                    Log.w(TAG, "Error: " + callFrame.args[0]);
                                }
                            }
                        });
                    } else if (methodName.equals("onProgress")) {
                        Hooking.pineHook(method, new MethodHook() {
                            @Override
                            public void beforeCall(Pine.CallFrame callFrame) {
                                // Track progress
                                if (callFrame.args.length > 0) {
                                    Log.d(TAG, "Prooface progress: " + callFrame.args[0]);
                                }
                            }
                        });
                    }
                }
                
                Log.d(TAG, "Hooked Prooface callback: " + className);
            } catch (ClassNotFoundException e) {
                // Callback class not present
            } catch (Exception e) {
                Log.w(TAG, "Error hooking callback: " + className, e);
            }
        }
    }
    
    /**
     * Hook Prooface session management
     */
    private static void hookProofaceSession() {
        String[] sessionClasses = {
            "com.prooface.sdk.session.LivenessSession",
            "com.prooface.sdk.core.LivenessSession",
            "com.sumsub.sns.prooface.session.LivenessSession"
        };
        
        for (String className : sessionClasses) {
            try {
                Class<?> sessionClass = Class.forName(className);
                
                for (Method method : sessionClass.getDeclaredMethods()) {
                    String methodName = method.getName().toLowerCase();
                    
                    if (methodName.contains("connect")) {
                        Hooking.pineHook(method, new MethodHook() {
                            @Override
                            public void beforeCall(Pine.CallFrame callFrame) {
                                Log.d(TAG, "LivenessSession connecting");
                                sProofaceSessionStarted.set(true);
                            }
                            
                            @Override
                            public void afterCall(Pine.CallFrame callFrame) {
                                Log.d(TAG, "LivenessSession connect completed");
                                // Notify LivenessSessionManager
                                try {
                                    LivenessSessionManager.onSessionConnected("prooface");
                                } catch (Throwable t) {
                                    // Manager may not be installed
                                }
                            }
                        });
                    } else if (methodName.contains("disconnect") || methodName.contains("close")) {
                        Hooking.pineHook(method, new MethodHook() {
                            @Override
                            public void afterCall(Pine.CallFrame callFrame) {
                                Log.d(TAG, "LivenessSession disconnected");
                                sProofaceSessionStarted.set(false);
                                // Notify LivenessSessionManager
                                try {
                                    LivenessSessionManager.onSessionEnd("prooface");
                                } catch (Throwable t) {
                                    // Manager may not be installed
                                }
                            }
                        });
                    } else if (methodName.contains("process") || methodName.contains("analyze")) {
                        Hooking.pineHook(method, new MethodHook() {
                            @Override
                            public void beforeCall(Pine.CallFrame callFrame) {
                                onFrameProcessed();
                            }
                        });
                    }
                }
                
                Log.d(TAG, "Hooked LivenessSession: " + className);
            } catch (ClassNotFoundException e) {
                // Session class not present
            } catch (Exception e) {
                Log.w(TAG, "Error hooking session: " + className, e);
            }
        }
    }
    
    /**
     * Hook Prooface processor classes
     */
    private static void hookProofaceProcessor() {
        String[] processorClasses = {
            "com.prooface.sdk.processor.FrameProcessor",
            "com.prooface.sdk.core.FrameProcessor",
            "com.sumsub.sns.prooface.processor.FrameProcessor"
        };
        
        for (String className : processorClasses) {
            try {
                Class<?> processorClass = Class.forName(className);
                
                for (Method method : processorClass.getDeclaredMethods()) {
                    String methodName = method.getName().toLowerCase();
                    
                    if (methodName.contains("process") || methodName.contains("onframe")) {
                        Hooking.pineHook(method, new MethodHook() {
                            @Override
                            public void beforeCall(Pine.CallFrame callFrame) {
                                // Track frame processing
                                onFrameProcessed();
                            }
                        });
                    }
                }
                
                Log.d(TAG, "Hooked FrameProcessor: " + className);
            } catch (ClassNotFoundException e) {
                // Processor class not present
            } catch (Exception e) {
                Log.w(TAG, "Error hooking processor: " + className, e);
            }
        }
    }
    
    /**
     * Hook SumSub tracking API (network requests)
     */
    private static void hookSumSubTracking() {
        // The logs show: POST https://api.sumsub.com/resources/tracking/trackEventsComp
        // This is a tracking endpoint we can monitor
        
        try {
            // Use OkHttp RealCall directly to avoid interface hooking issue
            Class<?> realCallClass = Class.forName("okhttp3.RealCall");
            Method enqueueMethod = realCallClass.getDeclaredMethod("enqueue",
                Class.forName("okhttp3.Callback"));
            
            Hooking.pineHook(enqueueMethod, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    try {
                        // Get request URL for logging
                        Object call = callFrame.thisObject;
                        Method requestMethod = call.getClass().getMethod("request");
                        Object request = requestMethod.invoke(call);
                        Method urlMethod = request.getClass().getMethod("url");
                        Object url = urlMethod.invoke(request);
                        String urlString = url.toString();
                        
                        if (urlString.contains("sumsub.com") || urlString.contains("prooface")) {
                            Log.d(TAG, "SumSub network request: " + urlString);
                        }
                    } catch (Exception e) {
                        // Ignore reflection errors
                    }
                }
            });
            
            Log.d(TAG, "Hooked OkHttp RealCall for SumSub tracking");
        } catch (ClassNotFoundException e) {
            // Try internal class name as fallback (sometimes it's okhttp3.internal.connection.RealCall or similar depending on version)
            Log.d(TAG, "okhttp3.RealCall not found, trying obfuscated/alternate names");
        } catch (Exception e) {
            Log.w(TAG, "Error hooking SumSub tracking", e);
        }
    }
    
    // ==================== Event Handlers ====================
    
    /**
     * Called when Prooface starts
     */
    private static void onProofaceStart() {
        sProofaceActive.set(true);
        sSessionStartTime.set(System.currentTimeMillis());
        sFramesProcessed.set(0);
        sResultCallbackCount.set(0);
        
        // Notify LivenessSessionManager
        try {
            LivenessSessionManager.onSessionStart("prooface");
        } catch (Throwable t) {
            // Manager may not be installed
        }
        
        // Activate face detection bypass
        try {
            FaceDetectionBypassHook.setActive(true);
        } catch (Throwable t) {
            // Hook may not be installed
        }
        
        Log.i(TAG, "Prooface session started");
    }
    
    /**
     * Called when Prooface stops
     */
    private static void onProofaceStop() {
        long duration = System.currentTimeMillis() - sSessionStartTime.get();
        int frames = sFramesProcessed.get();
        
        Log.i(TAG, "Prooface session stopped. Duration: " + duration + 
                   "ms, Frames: " + frames);
        
        sProofaceActive.set(false);
        sProofaceSessionStarted.set(false);
        
        // Notify LivenessSessionManager
        try {
            LivenessSessionManager.onSessionEnd("prooface");
        } catch (Throwable t) {
            // Manager may not be installed
        }
        
        // Deactivate face detection bypass (optional, may want to keep active)
        // FaceDetectionBypassHook.setActive(false);
    }
    
    /**
     * Create a proxy for the Prooface callback to intercept results
     */
    private static Object createCallbackProxy(Object originalCallback) {
        if (originalCallback == null) return null;
        
        try {
            Class<?>[] interfaces = originalCallback.getClass().getInterfaces();
            if (interfaces.length == 0) {
                // Try to get interface from superclass
                Class<?> superClass = originalCallback.getClass().getSuperclass();
                if (superClass != null) {
                    interfaces = superClass.getInterfaces();
                }
            }
            
            if (interfaces.length == 0) {
                Log.w(TAG, "Callback has no interfaces to proxy");
                return null;
            }
            
            return Proxy.newProxyInstance(
                originalCallback.getClass().getClassLoader(),
                interfaces,
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    
                    // Log all callback methods
                    Log.d(TAG, "Callback method: " + methodName);
                    
                    // Forward to original callback
                    try {
                        return method.invoke(originalCallback, args);
                    } catch (Exception e) {
                        Log.e(TAG, "Error forwarding callback", e);
                        return null;
                    }
                }
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to create callback proxy", e);
            return null;
        }
    }
    
    /**
     * Get statistics for debugging
     */
    public static String getStats() {
        return String.format(
            "ProofaceActive: %b, SessionStarted: %b, Frames: %d, Results: %d, Duration: %dms",
            sProofaceActive.get(),
            sProofaceSessionStarted.get(),
            sFramesProcessed.get(),
            sResultCallbackCount.get(),
            System.currentTimeMillis() - sSessionStartTime.get()
        );
    }
}
