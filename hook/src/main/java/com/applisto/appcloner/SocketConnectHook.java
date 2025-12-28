package com.applisto.appcloner;

import android.content.Context;

import com.applisto.appcloner.hooking.Hooking;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public abstract class SocketConnectHook {
    private static final String TAG = "SocketConnectHook";
    private static final List<SocketConnectHook> sHooks = new ArrayList<>();
    private static boolean sInstalled;

    public final synchronized void install(Context context) {
        if (!sInstalled) {
            Method m = null;

            // jar uses: "java.net.PlainSocketImpl" socketConnect(InetAddress,int,int)
            // Some Android versions may differ, so we try a couple.
            String[] impls = new String[] {
                    "java.net.PlainSocketImpl",
                    "java.net.AbstractPlainSocketImpl"
            };

            for (String cn : impls) {
                try {
                    m = ReflectionUtil.findMethodByParameterTypes(
                            cn, "socketConnect",
                            InetAddress.class, int.class, int.class
                    );
                    break;
                } catch (Throwable ignored) {}
            }

            if (m != null) {
                Hooking.pineHook(m, new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame cf) throws Throwable {
                        AtomicReference<InetAddress> addrRef = new AtomicReference<>((InetAddress) cf.args[0]);
                        AtomicReference<Integer> portRef = new AtomicReference<>((Integer) cf.args[1]);
                        AtomicReference<Integer> timeoutRef = new AtomicReference<>((Integer) cf.args[2]);

                        try {
                            for (SocketConnectHook h : snapshotHooks()) {
                                h.onSocketConnect(addrRef, portRef, timeoutRef);
                            }
                        } catch (UnknownHostException uhe) {
                            cf.setThrowable(uhe);
                        }

                        cf.args[0] = addrRef.get();
                        cf.args[1] = portRef.get();
                        cf.args[2] = timeoutRef.get();
                    }
                });
            }

            sInstalled = true;
        }

        sHooks.add(this);
    }

    private static List<SocketConnectHook> snapshotHooks() {
        return new ArrayList<>(sHooks);
    }

    protected abstract void onSocketConnect(
            AtomicReference<InetAddress> address,
            AtomicReference<Integer> port,
            AtomicReference<Integer> timeout
    ) throws UnknownHostException;
}
