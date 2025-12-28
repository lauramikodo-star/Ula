package com.applisto.appcloner.classes.secondary.util;

import android.util.Log;
import com.applisto.appcloner.hooking.Hooking;
import java.io.FileDescriptor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import libcore.io.ForwardingOs;
import libcore.io.Libcore;
import libcore.io.Os;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public abstract class FileSystemHook {
    private static final String TAG = FileSystemHook.class.getSimpleName();
    private static final List<FileSystemHook> sHooks = new ArrayList<>();
    private static boolean sInstalled;

    public FileSystemHook() {
        // Default constructor
    }

    protected abstract void handlePath(AtomicReference<String> pathRef);

    public void install() {
        if (sInstalled) {
            sHooks.add(this);
            return;
        }

        try {
            // Hook IoBridge.open(String, int)
            Method openMethod = ReflectionUtil.findMethodByParameterTypes("libcore.io.IoBridge", "open", String.class, int.class);
            if (openMethod != null) {
                Hooking.pineHook(openMethod, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame callFrame) throws Throwable {
                        String path = (String) callFrame.args[0];
                        AtomicReference<String> pathRef = new AtomicReference<>(path);
                        handlePath(pathRef);
                        callFrame.args[0] = pathRef.get();
                    }
                });
            }
        } catch (Exception e) {
            Log.w(TAG, e);
        }

        try {
             // Hook ContextImpl.openFileInput(String)
             Method openFileInput = ReflectionUtil.findMethodByParameterTypes("android.app.ContextImpl", "openFileInput", String.class);
             if (openFileInput != null) {
                 Hooking.pineHook(openFileInput, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame callFrame) throws Throwable {
                        String path = (String) callFrame.args[0];
                        AtomicReference<String> pathRef = new AtomicReference<>(path);
                        handlePath(pathRef);
                        callFrame.args[0] = pathRef.get();
                    }
                 });
             }
        } catch (Exception e) {
             Log.w(TAG, e);
        }

         try {
             // Hook ContextImpl.openFileOutput(String, int)
             Method openFileOutput = ReflectionUtil.findMethodByParameterTypes("android.app.ContextImpl", "openFileOutput", String.class, int.class);
             if (openFileOutput != null) {
                 Hooking.pineHook(openFileOutput, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame callFrame) throws Throwable {
                        String path = (String) callFrame.args[0];
                        AtomicReference<String> pathRef = new AtomicReference<>(path);
                        handlePath(pathRef);
                        callFrame.args[0] = pathRef.get();
                    }
                 });
             }
        } catch (Exception e) {
             Log.w(TAG, e);
        }

        sInstalled = true;
        sHooks.add(this);
        Log.i(TAG, "install; installed FileSystemHook: " + this.getClass());
    }
}
