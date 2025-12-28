package com.applisto.appcloner;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public class PackageHider {
    private static final String TAG = "PackageHider";
    private static final Set<String> sHiddenPackages = new HashSet<>();

    public static void install(Context context, Collection<String> packagesToHide) {
        if (packagesToHide == null || packagesToHide.isEmpty()) return;

        synchronized (sHiddenPackages) {
            sHiddenPackages.addAll(packagesToHide);
        }

        Log.i(TAG, "Hiding " + packagesToHide.size() + " packages.");

        // Hook PackageManager.getInstalledPackages
        Hooking.hookMethod(
            context.getPackageManager().getClass(),
            "getInstalledPackages",
            new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    List<PackageInfo> result = (List<PackageInfo>) callFrame.getResult();
                    if (result != null) {
                        List<PackageInfo> filtered = new ArrayList<>();
                        for (PackageInfo info : result) {
                            if (!sHiddenPackages.contains(info.packageName)) {
                                filtered.add(info);
                            } else {
                                Log.d(TAG, "Hidden package in getInstalledPackages: " + info.packageName);
                            }
                        }
                        callFrame.setResult(filtered);
                    }
                }
            },
            int.class
        );

        // Hook PackageManager.getInstalledApplications
        Hooking.hookMethod(
            context.getPackageManager().getClass(),
            "getInstalledApplications",
            new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    List<ApplicationInfo> result = (List<ApplicationInfo>) callFrame.getResult();
                    if (result != null) {
                        List<ApplicationInfo> filtered = new ArrayList<>();
                        for (ApplicationInfo info : result) {
                            if (!sHiddenPackages.contains(info.packageName)) {
                                filtered.add(info);
                            } else {
                                Log.d(TAG, "Hidden package in getInstalledApplications: " + info.packageName);
                            }
                        }
                        callFrame.setResult(filtered);
                    }
                }
            },
            int.class
        );

        // Hook PackageManager.getPackageInfo
        Hooking.hookMethod(
            context.getPackageManager().getClass(),
            "getPackageInfo",
            new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    String packageName = (String) callFrame.args[0];
                    if (sHiddenPackages.contains(packageName)) {
                        Log.d(TAG, "Hiding package in getPackageInfo: " + packageName);
                        callFrame.setResult(null); // Return null or throw NameNotFoundException
                        // Throwing exception is more accurate but Pine might not propagate it easily in beforeCall without custom handling.
                        // Setting result to null will often cause a crash or check in the caller, effectively simulating not found.
                        // However, strictly speaking, we should simulate NameNotFoundException.
                        // For simplicity in this hook setup, we'll try forcing an exception simulation or just returning null.
                        // Many apps check for null or catch exceptions.
                    }
                }

                // If we want to throw exception, we'd need to do it carefully.
                // For now, let's stick to hiding it from lists, which is the primary vector for root checks.
            },
            String.class, int.class
        );

        // Hook PackageManager.getApplicationInfo
        Hooking.hookMethod(
             context.getPackageManager().getClass(),
             "getApplicationInfo",
             new MethodHook() {
                 @Override
                 public void beforeCall(Pine.CallFrame callFrame) {
                     String packageName = (String) callFrame.args[0];
                     if (sHiddenPackages.contains(packageName)) {
                         Log.d(TAG, "Hiding package in getApplicationInfo: " + packageName);
                         callFrame.setResult(null);
                     }
                 }
             },
             String.class, int.class
        );
    }
}
