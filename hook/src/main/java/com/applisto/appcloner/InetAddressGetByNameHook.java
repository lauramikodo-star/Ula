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

public abstract class InetAddressGetByNameHook {
    private static final String TAG = "InetAddressGetByNameHook";
    private static final List<InetAddressGetByNameHook> sHooks = new ArrayList<>();
    private static boolean sInstalled;

    public final synchronized void install(Context context) {
        if (!sInstalled) {
            try {
                Method getByName = ReflectionUtil.findMethodByParameterTypes(
                        InetAddress.class, "getByName", String.class
                );
                Hooking.pineHook(getByName, new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame cf) throws Throwable {
                        AtomicReference<String> hostRef = new AtomicReference<>((String) cf.args[0]);

                        for (InetAddressGetByNameHook h : snapshotHooks()) {
                            InetAddress override = h.onGetByName(hostRef);
                            if (override != null) {
                                cf.setResult(override);
                                break;
                            }
                        }
                        cf.args[0] = hostRef.get();
                    }
                });
            } catch (Throwable ignored) {}

            try {
                Method getAllByName = ReflectionUtil.findMethodByParameterTypes(
                        InetAddress.class, "getAllByName", String.class
                );
                Hooking.pineHook(getAllByName, new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame cf) throws Throwable {
                        AtomicReference<String> hostRef = new AtomicReference<>((String) cf.args[0]);

                        for (InetAddressGetByNameHook h : snapshotHooks()) {
                            InetAddress[] override = h.onGetAllByName(hostRef);
                            if (override != null) {
                                cf.setResult(override);
                                break;
                            }
                        }
                        cf.args[0] = hostRef.get();
                    }
                });
            } catch (Throwable ignored) {}

            sInstalled = true;
        }

        sHooks.add(this);
    }

    private static List<InetAddressGetByNameHook> snapshotHooks() {
        // avoid ConcurrentModification without CopyOnWrite (matches jar style)
        return new ArrayList<>(sHooks);
    }

    protected abstract InetAddress onGetByName(AtomicReference<String> host) throws UnknownHostException;
    protected abstract InetAddress[] onGetAllByName(AtomicReference<String> host) throws UnknownHostException;
}
