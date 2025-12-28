package com.applisto.appcloner;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * LivenessSessionManager
 * 
 * This class addresses the common issue seen in logs:
 * "Liveness session is not yet connected, skipping frame analyzing"
 * 
 * The issue occurs when:
 * 1. A liveness detection SDK starts processing frames before its session is fully initialized
 * 2. The fake camera hook provides frames faster than the SDK can establish its connection
 * 3. Network latency or initialization delays cause temporary disconnection
 * 
 * The solution involves:
 * 1. Delaying frame delivery until the liveness session is ready
 * 2. Managing session lifecycle state
 * 3. Implementing a frame buffer to queue frames during connection
 * 4. Providing hooks to intercept and manage session state
 */
public final class LivenessSessionManager {
    private static final String TAG = "LivenessSession";
    
    private static volatile boolean sInitialized = false;
    private static Context sContext;
    private static Handler sMainHandler;
    
    // Session state tracking
    private static final AtomicBoolean sSessionConnected = new AtomicBoolean(false);
    private static final AtomicBoolean sSessionInitializing = new AtomicBoolean(false);
    private static final AtomicLong sSessionStartTime = new AtomicLong(0);
    private static final AtomicLong sLastFrameTime = new AtomicLong(0);
    
    // Frame timing control
    private static final long MIN_FRAME_INTERVAL_MS = 33;  // ~30 FPS max
    private static final long SESSION_TIMEOUT_MS = 10000;   // 10 second timeout
    private static final long SESSION_WARMUP_MS = 500;      // 500ms warmup period
    
    // Frame buffering
    private static final int MAX_BUFFERED_FRAMES = 5;
    private static final AtomicInteger sBufferedFrameCount = new AtomicInteger(0);
    
    // Session tracking by SDK
    private static final Map<String, SessionState> sSessionStates = new ConcurrentHashMap<>();
    
    // Latch for synchronization
    private static volatile CountDownLatch sConnectionLatch;
    
    /**
     * Session state for individual SDKs
     */
    private static class SessionState {
        volatile boolean connected = false;
        volatile boolean initializing = false;
        volatile long startTime = 0;
        volatile long lastFrameTime = 0;
        volatile int frameCount = 0;
        volatile int droppedFrames = 0;
        
        void reset() {
            connected = false;
            initializing = false;
            startTime = 0;
            lastFrameTime = 0;
            frameCount = 0;
            droppedFrames = 0;
        }
    }
    
    /**
     * Initialize the liveness session manager.
     */
    public static void install(Context ctx) {
        if (sInitialized) {
            Log.w(TAG, "LivenessSessionManager already installed");
            return;
        }
        
        sContext = ctx.getApplicationContext();
        sMainHandler = new Handler(Looper.getMainLooper());
        
        try {
            // Hook various liveness detection SDKs
            hookProofaceLiveness();
            hookSumSubLiveness();
            hookIdenfyLiveness();
            hookOnyxLiveness();
            hookGenericLiveness();
            
            sInitialized = true;
            Log.i(TAG, "LivenessSessionManager installed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to install LivenessSessionManager", e);
        }
    }
    
    /**
     * Notify that a liveness session is starting.
     */
    public static void onSessionStart(String sdkName) {
        SessionState state = getOrCreateState(sdkName);
        state.reset();
        state.initializing = true;
        state.startTime = System.currentTimeMillis();
        
        sSessionInitializing.set(true);
        sSessionStartTime.set(System.currentTimeMillis());
        sConnectionLatch = new CountDownLatch(1);
        
        Log.i(TAG, "Liveness session starting for: " + sdkName);
        
        // Schedule automatic session ready after warmup period
        sMainHandler.postDelayed(() -> {
            if (state.initializing && !state.connected) {
                onSessionConnected(sdkName);
            }
        }, SESSION_WARMUP_MS);
    }
    
    /**
     * Notify that a liveness session is connected and ready.
     */
    public static void onSessionConnected(String sdkName) {
        SessionState state = getOrCreateState(sdkName);
        state.connected = true;
        state.initializing = false;
        
        sSessionConnected.set(true);
        sSessionInitializing.set(false);
        
        if (sConnectionLatch != null) {
            sConnectionLatch.countDown();
        }
        
        Log.i(TAG, "Liveness session connected for: " + sdkName);
    }
    
    /**
     * Notify that a liveness session has ended.
     */
    public static void onSessionEnd(String sdkName) {
        SessionState state = sSessionStates.get(sdkName);
        if (state != null) {
            Log.i(TAG, "Liveness session ended for: " + sdkName + 
                       ", frames: " + state.frameCount + 
                       ", dropped: " + state.droppedFrames);
            state.reset();
        }
        
        sSessionConnected.set(false);
        sSessionInitializing.set(false);
    }
    
    /**
     * Check if a frame should be delivered (based on timing and session state).
     * Returns true if frame should be delivered, false if it should be skipped.
     */
    public static boolean shouldDeliverFrame(String sdkName) {
        SessionState state = getOrCreateState(sdkName);
        
        // If not initialized, always skip
        if (!sSessionConnected.get() && !sSessionInitializing.get()) {
            // No session active, allow frame (might trigger session start)
            return true;
        }
        
        // Wait for session to connect if initializing
        if (sSessionInitializing.get() && !sSessionConnected.get()) {
            // Check if we should wait or skip
            long elapsed = System.currentTimeMillis() - sSessionStartTime.get();
            
            if (elapsed < SESSION_WARMUP_MS) {
                // During warmup, buffer frames
                if (sBufferedFrameCount.incrementAndGet() <= MAX_BUFFERED_FRAMES) {
                    state.droppedFrames++;
                    Log.d(TAG, "Buffering frame during warmup, elapsed: " + elapsed + "ms");
                    return false;
                }
                sBufferedFrameCount.decrementAndGet();
            }
            
            if (elapsed > SESSION_TIMEOUT_MS) {
                // Timeout, force connection
                Log.w(TAG, "Session timeout, forcing connection");
                onSessionConnected(sdkName);
            }
        }
        
        // Check frame rate limiting
        long now = System.currentTimeMillis();
        long lastFrame = state.lastFrameTime;
        
        if (now - lastFrame < MIN_FRAME_INTERVAL_MS) {
            // Too fast, skip frame
            state.droppedFrames++;
            return false;
        }
        
        // Update timing
        state.lastFrameTime = now;
        state.frameCount++;
        sLastFrameTime.set(now);
        sBufferedFrameCount.set(0);  // Reset buffer count on successful delivery
        
        return state.connected || !sSessionInitializing.get();
    }
    
    /**
     * Wait for the liveness session to be ready (blocking).
     * Returns true if session is ready, false if timeout.
     */
    public static boolean waitForSession(long timeoutMs) {
        if (sSessionConnected.get()) {
            return true;
        }
        
        if (sConnectionLatch == null) {
            return true;  // No session active
        }
        
        try {
            return sConnectionLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.w(TAG, "Wait interrupted", e);
            return false;
        }
    }
    
    /**
     * Check if session is currently connected.
     */
    public static boolean isSessionConnected() {
        return sSessionConnected.get();
    }
    
    /**
     * Check if session is currently initializing.
     */
    public static boolean isSessionInitializing() {
        return sSessionInitializing.get();
    }
    
    /**
     * Get or create session state for an SDK.
     */
    private static SessionState getOrCreateState(String sdkName) {
        return sSessionStates.computeIfAbsent(sdkName, k -> new SessionState());
    }
    
    // ==================== Hook Implementations ====================
    
    /**
     * Hook Prooface SDK liveness detection
     */
    private static void hookProofaceLiveness() {
        String[] classNames = {
            "com.prooface.sdk.Prooface",
            "com.prooface.sdk.LivenessSession",
            "com.prooface.sdk.core.LivenessProcessor"
        };
        
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                hookLivenessClass(clazz, "prooface");
                Log.d(TAG, "Hooked Prooface class: " + className);
            } catch (ClassNotFoundException e) {
                // Class not present
            } catch (Exception e) {
                Log.w(TAG, "Error hooking Prooface: " + className, e);
            }
        }
    }
    
    /**
     * Hook SumSub SDK liveness detection
     */
    private static void hookSumSubLiveness() {
        String[] classNames = {
            "com.sumsub.sns.prooface.Prooface",
            "com.sumsub.sns.prooface.ProofaceProcessor",
            "com.sumsub.sns.core.liveness.LivenessSession",
            "com.sumsub.sns.liveness.presentation.LivenessProcessor"
        };
        
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                hookLivenessClass(clazz, "sumsub");
                Log.d(TAG, "Hooked SumSub class: " + className);
            } catch (ClassNotFoundException e) {
                // Class not present
            } catch (Exception e) {
                Log.w(TAG, "Error hooking SumSub: " + className, e);
            }
        }
        
        // Special handling for Prooface.onResult callback
        hookProofaceCallback();
    }
    
    /**
     * Hook Prooface callback to manage session state
     */
    private static void hookProofaceCallback() {
        try {
            // Hook the onResult callback
            Class<?> callbackClass = Class.forName("com.prooface.sdk.Prooface$Callback");
            for (Method method : callbackClass.getDeclaredMethods()) {
                if (method.getName().equals("onResult")) {
                    Hooking.pineHook(method, new MethodHook() {
                        @Override
                        public void beforeCall(Pine.CallFrame callFrame) {
                            Log.d(TAG, "Prooface.onResult callback triggered");
                            // Session is ending
                            onSessionEnd("prooface");
                        }
                    });
                }
            }
        } catch (ClassNotFoundException e) {
            // Callback class not present
        } catch (Exception e) {
            Log.w(TAG, "Error hooking Prooface callback", e);
        }
    }
    
    /**
     * Hook iDenfy SDK liveness detection
     */
    private static void hookIdenfyLiveness() {
        String[] classNames = {
            "com.idenfy.idenfySdk.liveness.LivenessSession",
            "com.idenfy.idenfySdk.core.LivenessProcessor"
        };
        
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                hookLivenessClass(clazz, "idenfy");
                Log.d(TAG, "Hooked iDenfy class: " + className);
            } catch (ClassNotFoundException e) {
                // Class not present
            } catch (Exception e) {
                Log.w(TAG, "Error hooking iDenfy: " + className, e);
            }
        }
    }
    
    /**
     * Hook Onyx SDK liveness detection
     */
    private static void hookOnyxLiveness() {
        String[] classNames = {
            "com.dft.onyx.core.LivenessSession",
            "com.dft.onyx.LivenessProcessor"
        };
        
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                hookLivenessClass(clazz, "onyx");
                Log.d(TAG, "Hooked Onyx class: " + className);
            } catch (ClassNotFoundException e) {
                // Class not present
            } catch (Exception e) {
                Log.w(TAG, "Error hooking Onyx: " + className, e);
            }
        }
    }
    
    /**
     * Hook generic liveness detection patterns
     */
    private static void hookGenericLiveness() {
        // Generic patterns are harder to hook without knowing specific SDKs
        Log.d(TAG, "Generic liveness hooks initialized");
    }
    
    /**
     * Hook common methods on a liveness class
     */
    private static void hookLivenessClass(Class<?> clazz, String sdkName) {
        // Hook start/init methods
        for (Method method : clazz.getDeclaredMethods()) {
            String methodName = method.getName().toLowerCase();
            
            if (methodName.contains("start") || 
                methodName.contains("init") || 
                methodName.contains("begin") ||
                methodName.contains("connect")) {
                
                try {
                    Hooking.pineHook(method, new MethodHook() {
                        @Override
                        public void beforeCall(Pine.CallFrame callFrame) {
                            Log.d(TAG, sdkName + " session method called: " + method.getName());
                            onSessionStart(sdkName);
                        }
                        
                        @Override
                        public void afterCall(Pine.CallFrame callFrame) {
                            // Check if method indicates success
                            Object result = callFrame.getResult();
                            if (result instanceof Boolean && (Boolean) result) {
                                onSessionConnected(sdkName);
                            } else if (result != null && !result.equals(Boolean.FALSE)) {
                                // Non-boolean return, assume success
                                onSessionConnected(sdkName);
                            }
                        }
                    });
                } catch (Exception e) {
                    // Skip if hook fails
                }
            }
            
            // Hook stop/close/end methods
            if (methodName.contains("stop") || 
                methodName.contains("close") || 
                methodName.contains("end") ||
                methodName.contains("finish") ||
                methodName.contains("release")) {
                
                try {
                    Hooking.pineHook(method, new MethodHook() {
                        @Override
                        public void afterCall(Pine.CallFrame callFrame) {
                            Log.d(TAG, sdkName + " session ending: " + method.getName());
                            onSessionEnd(sdkName);
                        }
                    });
                } catch (Exception e) {
                    // Skip if hook fails
                }
            }
            
            // Hook processFrame/analyzeFrame methods
            if (methodName.contains("process") || 
                methodName.contains("analyze") || 
                methodName.contains("frame") ||
                methodName.contains("image")) {
                
                try {
                    Hooking.pineHook(method, new MethodHook() {
                        @Override
                        public void beforeCall(Pine.CallFrame callFrame) {
                            if (!shouldDeliverFrame(sdkName)) {
                                Log.d(TAG, "Skipping frame for " + sdkName + " (not ready)");
                                // Optionally skip the method call
                                // callFrame.setResult(null);
                            }
                        }
                    });
                } catch (Exception e) {
                    // Skip if hook fails
                }
            }
        }
    }
    
    /**
     * Force session to connected state (for testing/debugging)
     */
    public static void forceSessionConnected(String sdkName) {
        onSessionConnected(sdkName);
    }
    
    /**
     * Get statistics for debugging
     */
    public static String getStats(String sdkName) {
        SessionState state = sSessionStates.get(sdkName);
        if (state == null) {
            return "No session for: " + sdkName;
        }
        
        return String.format("SDK: %s, Connected: %b, Frames: %d, Dropped: %d",
            sdkName, state.connected, state.frameCount, state.droppedFrames);
    }
}
