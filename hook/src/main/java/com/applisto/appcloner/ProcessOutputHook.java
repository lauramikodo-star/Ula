package com.applisto.appcloner;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.applisto.appcloner.hooking.Hooking;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public class ProcessOutputHook {
    private static final String TAG = "ProcessOutputHook";

    public static void install() {
        try {
            Hooking.hookMethod(ProcessBuilder.class, "start", new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    ProcessBuilder pb = (ProcessBuilder) callFrame.thisObject;
                    Process process = (Process) callFrame.getResult();
                    if (process == null) return;

                    List<String> command = pb.command();
                    if (command != null && !command.isEmpty()) {
                        String cmd = command.get(0);
                        if (cmd.endsWith("getprop")) {
                            String arg = command.size() > 1 ? command.get(1) : null;
                            callFrame.setResult(new WrappedProcess(process, arg));
                        }
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook ProcessBuilder.start", t);
        }
    }

    private static class WrappedProcess extends Process {
        private final Process delegate;
        private final InputStream inputStream;

        WrappedProcess(Process delegate, String key) {
            this.delegate = delegate;
            if (key != null) {
                String val = CustomBuildProps.sCustomBuildProps.get(key);
                if (val != null) {
                    // Single key: replace stdout entirely
                    this.inputStream = new ByteArrayInputStream((val + "\n").getBytes(StandardCharsets.UTF_8));
                } else {
                    this.inputStream = delegate.getInputStream();
                }
            } else {
                // List all: prepend our props
                this.inputStream = new FilterInputStream(delegate.getInputStream()) {
                    // This is a naive implementation; properly merging streams is complex.
                    // For now, let's just assume we want to inject our props at the start.
                    // A proper implementation would buffer the original stream, filter out overrides, and prepend new ones.
                    // But blocking read is tricky.
                    // Simplified: just return our props first, then the rest.

                    private ByteArrayInputStream prefixStream;

                    @Override
                    public int read() throws IOException {
                        if (prefixStream == null) {
                            StringBuilder sb = new StringBuilder();
                            for (Map.Entry<String, String> e : CustomBuildProps.sCustomBuildProps.entrySet()) {
                                sb.append("[").append(e.getKey()).append("]: [").append(e.getValue()).append("]\n");
                            }
                            prefixStream = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
                        }
                        int b = prefixStream.read();
                        if (b != -1) return b;
                        return super.read();
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        if (prefixStream == null) {
                             StringBuilder sb = new StringBuilder();
                            for (Map.Entry<String, String> e : CustomBuildProps.sCustomBuildProps.entrySet()) {
                                sb.append("[").append(e.getKey()).append("]: [").append(e.getValue()).append("]\n");
                            }
                            prefixStream = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
                        }
                        int read = prefixStream.read(b, off, len);
                        if (read != -1) return read;
                        return super.read(b, off, len);
                    }
                };
            }
        }

        @Override public java.io.OutputStream getOutputStream() { return delegate.getOutputStream(); }
        @Override public java.io.InputStream getInputStream() { return inputStream; }
        @Override public java.io.InputStream getErrorStream() { return delegate.getErrorStream(); }
        @Override public int waitFor() throws InterruptedException { return delegate.waitFor(); }
        @Override public int exitValue() { return delegate.exitValue(); }
        @Override public void destroy() { delegate.destroy(); }
    }
}
