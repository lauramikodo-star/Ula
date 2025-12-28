package com.applisto.appcloner;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import com.applisto.appcloner.hooking.Hooking;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public class MockNetworkConnection {
    private static final String TAG = "MockNetworkConnection";

    private static Boolean sWifiConnected;
    private static Boolean sMobileConnected;
    private static Boolean sEthernetConnected;

    public static void install(Context context, String mockWifi, String mockMobile, String mockEthernet) {
        sWifiConnected = parseState(mockWifi);
        sMobileConnected = parseState(mockMobile);
        sEthernetConnected = parseState(mockEthernet);

        if (sWifiConnected == null && sMobileConnected == null && sEthernetConnected == null) {
            return;
        }

        Log.i(TAG, "Installing MockNetworkConnection: Wifi=" + sWifiConnected +
                   ", Mobile=" + sMobileConnected + ", Ethernet=" + sEthernetConnected);

        Hooking.initHooking(context);

        try {
            hookWifiInfo();
            hookNetworkInfo();
            hookConnectivityManager();
            hookWifiManager();
            hookNetworkInterface();
            if (Build.VERSION.SDK_INT >= 21) {
                hookNetworkCapabilities();
                hookLinkProperties();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install hooks", t);
        }
    }

    private static Boolean parseState(String state) {
        if ("CONNECTED".equals(state)) return Boolean.TRUE;
        if ("DISCONNECTED".equals(state)) return Boolean.FALSE;
        return null;
    }

    /* 1) Wi-Fi supplicant state */
    private static void hookWifiInfo() {
        try {
            Hooking.hookMethod(WifiInfo.class, "getSupplicantState", new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    if (sWifiConnected != null) {
                        callFrame.setResult(sWifiConnected ? SupplicantState.COMPLETED : SupplicantState.DISCONNECTED);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "hookWifiInfo failed", t);
        }
    }

    /* 2) & 3) NetworkInfo hooks */
    private static void hookNetworkInfo() {
        MethodHook infoHook = new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                NetworkInfo info = (NetworkInfo) callFrame.thisObject;
                int type = info.getType();
                Boolean mock = getMockForType(type);
                if (mock != null) {
                    String name = ((Method) callFrame.args[0]).getName(); // Wait, pine passes callFrame? No, need hook logic
                    // We need to know which method was called. Pine doesn't easily give method name in callback unless we separate hooks
                    // Or we assume logic based on return type.

                    // Actually, simpler to separate hooks for clarity
                }
            }
        };

        // isConnected / isConnectedOrConnecting
        MethodHook connectedHook = new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                NetworkInfo info = (NetworkInfo) callFrame.thisObject;
                Boolean mock = getMockForType(info.getType());
                if (mock != null) {
                    callFrame.setResult(mock);
                }
            }
        };
        Hooking.hookMethod(NetworkInfo.class, "isConnected", connectedHook);
        Hooking.hookMethod(NetworkInfo.class, "isConnectedOrConnecting", connectedHook);

        // getState
        Hooking.hookMethod(NetworkInfo.class, "getState", new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                NetworkInfo info = (NetworkInfo) callFrame.thisObject;
                Boolean mock = getMockForType(info.getType());
                if (mock != null) {
                    callFrame.setResult(mock ? NetworkInfo.State.CONNECTED : NetworkInfo.State.DISCONNECTED);
                }
            }
        });
    }

    private static Boolean getMockForType(int type) {
        if (type == ConnectivityManager.TYPE_WIFI) return sWifiConnected;
        if (type == ConnectivityManager.TYPE_MOBILE) return sMobileConnected;
        if (type == ConnectivityManager.TYPE_ETHERNET) return sEthernetConnected;
        return null;
    }

    /* 4) & 5) ConnectivityManager hooks */
    private static void hookConnectivityManager() {
        // getNetworkInfo(int)
        Hooking.hookMethod(ConnectivityManager.class, "getNetworkInfo", new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                if (callFrame.getResult() == null) {
                    int type = (Integer) callFrame.args[0];
                    Boolean mock = getMockForType(type);
                    if (mock != null) { // If we are mocking this type, ensure it exists
                        callFrame.setResult(createNetworkInfo(type));
                    }
                }
            }
        }, int.class);

        // getActiveNetworkInfo()
        Hooking.hookMethod(ConnectivityManager.class, "getActiveNetworkInfo", new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                NetworkInfo active = (NetworkInfo) callFrame.getResult();

                // If mocked connected, force it to be the active one?
                // Logic: if sWifiConnected==true, return fabricated WIFI info.
                // Priority: Ethernet > Wifi > Mobile usually.
                // If sWifiConnected==false and active is WIFI, return null.

                if (sEthernetConnected == Boolean.TRUE) {
                    callFrame.setResult(createConnectedNetworkInfo(ConnectivityManager.TYPE_ETHERNET));
                    return;
                }
                if (sWifiConnected == Boolean.TRUE) {
                    callFrame.setResult(createConnectedNetworkInfo(ConnectivityManager.TYPE_WIFI));
                    return;
                }
                if (sMobileConnected == Boolean.TRUE) {
                    callFrame.setResult(createConnectedNetworkInfo(ConnectivityManager.TYPE_MOBILE));
                    return;
                }

                // If not forcing connection, check if we need to force disconnection
                if (active != null) {
                    Boolean mock = getMockForType(active.getType());
                    if (mock == Boolean.FALSE) {
                        callFrame.setResult(null);
                    }
                }
            }
        });
    }

    /* 6) NetworkCapabilities (API 21+) */
    private static void hookNetworkCapabilities() {
        Hooking.hookMethod(ConnectivityManager.class, "getNetworkCapabilities", new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                NetworkCapabilities nc = (NetworkCapabilities) callFrame.getResult();
                if (nc != null) {
                    // This is hard to map back to a type without the Network object context matching what we mocked.
                    // But usually apps assume active network.
                    // Ideally we should know which network is which.
                    // The "secondary.jar" description implies logic based on mock state directly?
                    // "It modifies NetworkCapabilities transport types: WIFI=1, CELLULAR=0..."
                    // It probably applies the mocks blindly if they are "CONNECTED".

                    if (sWifiConnected == Boolean.TRUE) {
                        addTransport(nc, NetworkCapabilities.TRANSPORT_WIFI);
                        // remove others?
                    }
                    // This part is tricky without contextual mapping.
                    // Let's implement addTransport helper.
                }
            }
        }, Network.class);
    }

    private static void addTransport(NetworkCapabilities nc, int transport) {
        try {
            // Try setTransportType(int, boolean) - private/hidden? No, it's not standard public.
            // Standard is addTransportType(int) (API 21)
            // But we might need reflection if we can't compile against it or to access hidden bits.
            Method add = NetworkCapabilities.class.getMethod("addTransportType", int.class);
            add.invoke(nc, transport);
        } catch (Throwable t) {
            // Try internal setTransportType
        }
    }

    /* 7) WifiManager */
    private static void hookWifiManager() {
        Hooking.hookMethod(WifiManager.class, "isWifiEnabled", new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                if (sWifiConnected != null) {
                    callFrame.setResult(sWifiConnected);
                }
            }
        });
    }

    /* 8) NetworkInterface */
    private static void hookNetworkInterface() {
        Hooking.hookMethod(NetworkInterface.class, "getNetworkInterfaces", new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                Enumeration<NetworkInterface> en = (Enumeration<NetworkInterface>) callFrame.getResult();
                if (en != null) {
                    List<NetworkInterface> list = Collections.list(en);
                    List<NetworkInterface> filtered = new ArrayList<>();
                    for (NetworkInterface ni : list) {
                        String name = ni.getName();
                        if (sWifiConnected == Boolean.FALSE && name.startsWith("wlan")) continue;
                        if (sMobileConnected == Boolean.FALSE && name.startsWith("rmnet")) continue;
                        if (sEthernetConnected == Boolean.FALSE && name.startsWith("eth")) continue;
                        filtered.add(ni);
                    }
                    callFrame.setResult(Collections.enumeration(filtered));
                }
            }
        });
    }

    /* 9) LinkProperties */
    private static void hookLinkProperties() {
        MethodHook hook = new MethodHook() {
            @Override
            public void afterCall(Pine.CallFrame callFrame) {
                LinkProperties lp = (LinkProperties) callFrame.getResult();
                if (lp != null) {
                    String iface = lp.getInterfaceName();
                    if (iface != null) {
                        // rewrite interface name if needed
                        // e.g. if wlan0 but mocked wifi false, change to something else?
                        // Or if mocked wifi TRUE, ensure it is wlan0?
                        // Jar logic: "if starts with interface that your mock says is DISCONNECTED, rewrite to a CONNECTED one"

                        String newIface = iface;
                        if (sWifiConnected == Boolean.FALSE && iface.startsWith("wlan")) {
                            newIface = getConnectedInterface();
                        } else if (sMobileConnected == Boolean.FALSE && iface.startsWith("rmnet")) {
                            newIface = getConnectedInterface();
                        }

                        if (newIface != null && !newIface.equals(iface)) {
                            // Set interface name via reflection
                            ReflectionUtil.setStaticFieldValue(LinkProperties.class, "mIfaceName", newIface); // Field likely different
                            // LinkProperties.setInterfaceName is hidden
                            ReflectionUtil.invokeMethod(lp, "setInterfaceName", newIface);
                        }
                    }
                }
            }
        };

        Hooking.hookMethod(ConnectivityManager.class, "getLinkProperties", hook, Network.class);
        // Hook int variant if exists (deprecated/hidden)
    }

    private static String getConnectedInterface() {
        if (sWifiConnected == Boolean.TRUE) return "wlan0";
        if (sMobileConnected == Boolean.TRUE) return "rmnet0";
        if (sEthernetConnected == Boolean.TRUE) return "eth0";
        return "lo";
    }

    // --- Helpers ---

    private static NetworkInfo createNetworkInfo(int type) {
        try {
            // NetworkInfo(int type, int subtype, String typeName, String subtypeName)
            Constructor<NetworkInfo> c = NetworkInfo.class.getDeclaredConstructor(int.class, int.class, String.class, String.class);
            c.setAccessible(true);
            if (type == ConnectivityManager.TYPE_WIFI) {
                return c.newInstance(1, 0, "WIFI", "UNKNOWN");
            } else if (type == ConnectivityManager.TYPE_MOBILE) {
                return c.newInstance(0, 13, "MOBILE", "LTE");
            } else if (type == ConnectivityManager.TYPE_ETHERNET) {
                return c.newInstance(9, 0, "ETHERNET", "UNKNOWN");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to create NetworkInfo", t);
        }
        return null;
    }

    private static NetworkInfo createConnectedNetworkInfo(int type) {
        NetworkInfo info = createNetworkInfo(type);
        if (info != null) {
            try {
                // setDetailedState(DetailedState detailedState, String reason, String extraInfo)
                Method setDetailedState = NetworkInfo.class.getDeclaredMethod("setDetailedState",
                        NetworkInfo.DetailedState.class, String.class, String.class);
                setDetailedState.setAccessible(true);
                setDetailedState.invoke(info, NetworkInfo.DetailedState.CONNECTED, null, null);

                // setIsAvailable(boolean)
                Method setIsAvailable = NetworkInfo.class.getDeclaredMethod("setIsAvailable", boolean.class);
                setIsAvailable.setAccessible(true);
                setIsAvailable.invoke(info, true);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to set NetworkInfo state", t);
            }
        }
        return info;
    }
}
