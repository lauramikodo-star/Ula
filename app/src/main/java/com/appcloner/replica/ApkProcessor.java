package com.appcloner.replica;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.android.apksig.ApkSigner;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.value.ValueType;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ApkProcessor {
    private static final String TAG = "ApkProcessor";

    private static final String ANDROID_MANIFEST = "AndroidManifest.xml";

    private static final String E_MANIFEST     = "manifest";
    private static final String E_APPLICATION  = "application";
    private static final String E_ACTIVITY     = "activity";
    private static final String E_PROVIDER     = "provider";
    private static final String E_RECEIVER     = "receiver";
    private static final String E_INTENT_FILTER = "intent-filter";
    private static final String E_ACTION       = "action";
    private static final String E_USES_PERMISSION = "uses-permission";

    private static final String A_NAME         = "name";
    private static final String A_AUTHORITIES  = "authorities";
    private static final String A_EXPORTED     = "exported";
    private static final String A_INIT_ORDER   = "initOrder";
    private static final String A_PERMISSION   = "permission";

    private static final int ID_ANDROID_NAME        = 0x01010003;
    private static final int ID_ANDROID_AUTHORITIES = 0x01010018;
    private static final int ID_ANDROID_EXPORTED    = 0x0101001e;
    private static final int ID_ANDROID_INIT_ORDER  = 0x01010427;
    private static final int ID_ANDROID_MIN_SDK     = 0x0101020c;
    private static final int ID_ANDROID_PERMISSION  = 0x01010006;
    private static final int ID_ANDROID_THEME       = 0x01010000;
    private static final int ID_ANDROID_LABEL       = 0x01010001;
    private static final int ID_ANDROID_ICON        = 0x01010002;
    private static final int ID_ANDROID_VALUE       = 0x01010024;
    private static final int ID_ANDROID_SCREEN_ORIENTATION = 0x0101001d;
    private static final int ID_ANDROID_EXTRACT_NATIVE_LIBS = 0x010104ea;
    private static final int ID_ANDROID_CONFIG_CHANGES = 0x0101001f;
    private static final int ID_ANDROID_TARGET_SDK_VERSION = 0x01010270;
    private static final int ID_ANDROID_DEBUGGABLE = 0x0101000f;
    private static final int ID_ANDROID_TEST_ONLY = 0x01010272;

    private static final Pattern SIG_PATH = Pattern.compile(
            "^META-INF/(.+\\.(RSA|DSA|EC|SF)|MANIFEST\\.MF)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEX_NAME = Pattern.compile(
            "^classes(\\d*)\\.dex$", Pattern.CASE_INSENSITIVE);

    private static final String KEYSTORE_ASSET = "debug.keystore";
    private static final String STORE_PWD      = "appklown";
    private static final String KEY_PWD        = "appklown";
    private static final String ALIAS          = "Harrygp";

    private static final String IPC_PERMISSION = "com.appcloner.replica.permission.REPLICA_IPC";
    private static final String PERM_READ_EXTERNAL = "android.permission.READ_EXTERNAL_STORAGE";
    private static final String PERM_WRITE_EXTERNAL = "android.permission.WRITE_EXTERNAL_STORAGE";

    private static final String RECEIVER_NAME = "com.applisto.appcloner.DataExportReceiver";
    private static final String EXPORT_ACTION = "com.applisto.appcloner.ACTION_EXPORT_DATA";
    private static final String IDENTITY_REGENERATION_RECEIVER_NAME = "com.applisto.appcloner.IdentityRegenerationReceiver";
    private static final String ACTION_REGENERATE_IDENTITY = "com.applisto.appcloner.ACTION_REGENERATE_IDENTITY";

    private static final String CAMERA_CONTROL_RECEIVER_NAME = "com.applisto.appcloner.CameraControlReceiver";
    private static final String FAKE_CAMERA_ACTIVITY_NAME = "com.applisto.appcloner.FakeCameraActivity";
    private static final String FAKE_CALCULATOR_ACTIVITY_NAME = "com.applisto.appcloner.FakeCalculatorActivity";
    private static final String INTERNAL_BROWSER_ACTIVITY_NAME = "com.applisto.appcloner.InternalBrowserActivity";

    private static final String CLONING_MODE_KEY = "cloning_mode";
    private static final String CLONING_MODE_REPLACE = "replace_original";
    private static final String CLONING_MODE_GENERATE = "generate_new_package";
    private static final String CLONING_MODE_CUSTOM = "custom_package";
    private static final String CUSTOM_PACKAGE_NAME_KEY = "custom_package_name";

    // Use a static authority for the provider
    private static final String PROVIDER_AUTHORITY = "com.applisto.appcloner.DefaultProvider";

    private final Context ctx;

    public ApkProcessor(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void injectHook(Uri inApk,
                           Uri outApk,
                           File hookDex,
                           File clonerJson,
                           File nativeLibDir,
                           File bundledDataFile,
                           File liveVideoFile,
                           File iconFile) throws Exception {
        if (inApk == null || outApk == null || hookDex == null || clonerJson == null) {
            throw new IllegalArgumentException("Required parameters cannot be null");
        }

        File tempRoot = new File(ctx.getCacheDir(), "apk_" + System.currentTimeMillis());
        if (!tempRoot.mkdirs()) throw new IOException("mkdir failed: " + tempRoot);

        Set<Integer> dexNumbers = new HashSet<>();
        Set<String> abiDirs = new HashSet<>();
        byte[] manifestRaw = null;

        String basePath;
        try {
            basePath = tempRoot.getCanonicalPath() + File.separator;
        } catch (IOException e) {
            deleteRec(tempRoot);
            throw e;
        }

        // Unpack input APK (strip signatures, capture manifest and dex indices)
        try (InputStream is = ctx.getContentResolver().openInputStream(inApk);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {

            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                String name = ze.getName();
                if (name == null || name.isEmpty()) continue;

                // Remove all existing signature files
                if (SIG_PATH.matcher(name).matches()) continue;

                // Track ABIs
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    int slash = name.indexOf('/', 4);
                    if (slash > 0) abiDirs.add(name.substring(0, slash + 1));
                }

                // Track DEX numbers
                Matcher m = DEX_NAME.matcher(name);
                if (m.matches()) {
                    int idx = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
                    dexNumbers.add(idx);
                    Log.d(TAG, "Found DEX: " + name + " -> index " + idx);
                }

                // Read manifest into memory, don't write original out
                if (ANDROID_MANIFEST.equals(name)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    copyStream(zis, baos);
                    manifestRaw = baos.toByteArray();
                    continue;
                }

                File out = safeResolve(tempRoot, basePath, name);
                if (ze.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) {
                        throw new IOException("Failed to create dir: " + out);
                    }
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Failed to create parent: " + parent);
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    copyStream(zis, fos);
                }
            }
        }

        if (manifestRaw == null) {
            deleteRec(tempRoot);
            throw new IOException("AndroidManifest.xml missing in APK");
        }

        // Read cloner.json
        JSONObject clonerConfig;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(clonerJson), "UTF-8")) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer, 0, buffer.length)) != -1) {
                sb.append(buffer, 0, read);
            }
            clonerConfig = new JSONObject(sb.toString());
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to read or parse cloner.json, using default empty config.", e);
            clonerConfig = new JSONObject();
        }

        // Patch manifest with validation + safe fallback + retry logic
        byte[] patchedManifest;
        try {
            ManifestPatchResult manifestResult = patchManifest(manifestRaw, clonerConfig);
            patchedManifest = manifestResult.manifestBytes;
            validateManifest(patchedManifest);
            Log.d(TAG, "Manifest patched and validated successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "Manifest patching/validation failed on first attempt", t);
            // Try a safer patching approach without icon/label modifications
            try {
                Log.d(TAG, "Attempting safe manifest patch without app name modification...");
                JSONObject safeConfig = clonerConfig != null ? new JSONObject(clonerConfig.toString()) : new JSONObject();
                safeConfig.remove("app_name");  // Remove app name modification
                ManifestPatchResult safeResult = patchManifest(manifestRaw, safeConfig);
                patchedManifest = safeResult.manifestBytes;
                validateManifest(patchedManifest);
                Log.d(TAG, "Safe manifest patch succeeded.");
            } catch (Throwable t2) {
                Log.e(TAG, "Safe manifest patching also failed; falling back to original manifest", t2);
                patchedManifest = manifestRaw;
            }
        }

        // Decide next DEX index and add hook dex
        int nextIdx = dexNumbers.isEmpty() ? 2 : Collections.max(dexNumbers) + 1;
        String dexName = (nextIdx == 1) ? "classes.dex" : "classes" + nextIdx + ".dex";
        Log.d(TAG, "Adding hook DEX as: " + dexName + " (nextIdx=" + nextIdx + ")");
        copyFile(hookDex, new File(tempRoot, dexName));

        // Copy cloner.json into assets
        File assetsDir = new File(tempRoot, "assets");
        if (!assetsDir.exists() && !assetsDir.mkdirs()) {
            deleteRec(tempRoot);
            throw new IOException("Failed to create assets dir");
        }
        copyFile(clonerJson, new File(assetsDir, "cloner.json"));

        // Optional live video file
        if (liveVideoFile != null && liveVideoFile.exists()) {
             copyFile(liveVideoFile, new File(assetsDir, "fake_video.mp4"));
             Log.d(TAG, "Bundled fake_video.mp4 into assets");
        }

        // Optional icon file (copy to root)
        if (iconFile != null && iconFile.exists()) {
            copyFile(iconFile, new File(tempRoot, "_launcher_icon.png"));
            int replacedIcons = replaceLauncherIcons(tempRoot, iconFile);
            Log.d(TAG, "Added modified icon to root as _launcher_icon.png and replaced " + replacedIcons + " launcher resources");
        }

        // Optional bundled app data
        if (bundledDataFile != null && bundledDataFile.exists()) {
            try {
                if (isProbablyZip(bundledDataFile) && bundledDataFile.length() <= 100L * 1024 * 1024) {
                    copyFile(bundledDataFile, new File(assetsDir, "app_data_export.zip"));
                    Log.d(TAG, "App data bundled into assets/app_data_export.zip");
                } else {
                    Log.w(TAG, "Bundled data file rejected (not a ZIP or too large). Skipping.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error bundling app data. Skipping.", e);
            }
        }

        // Inject native libs for appropriate ABI(s)
        // Check for both arm64 and armv7 support in the target APK
        boolean hasArm64 = abiDirs.contains("lib/arm64-v8a/");
        boolean hasArmV7 = abiDirs.contains("lib/armeabi-v7a/");

        // If neither is detected but we are here, it might be an APK without native libs (Java only)
        // In that case, we might default to injecting both or just one.
        // For now, if no libs are found, we assume it supports at least armv7 or both.
        if (!hasArm64 && !hasArmV7) {
            // Default behavior for pure Java apps: inject both if we have them
            hasArm64 = true;
            hasArmV7 = true;
        }

        if (nativeLibDir != null) {
            // Helper to copy libs for a specific ABI
            if (hasArm64) {
                File arm64Source = new File(nativeLibDir, "arm64-v8a");
                if (arm64Source.exists() && arm64Source.isDirectory()) {
                     File dstDir = new File(tempRoot, "lib/arm64-v8a/");
                     if (!dstDir.exists() && !dstDir.mkdirs()) {
                         deleteRec(tempRoot);
                         throw new IOException("Failed to create ABI dir: " + dstDir);
                     }
                     File[] soFiles = arm64Source.listFiles((d, n) -> n != null && n.endsWith(".so"));
                     if (soFiles != null) {
                         for (File so : soFiles) {
                             copyFile(so, new File(dstDir, so.getName()));
                         }
                     }
                }
            }

            if (hasArmV7) {
                File armV7Source = new File(nativeLibDir, "armeabi-v7a");
                if (armV7Source.exists() && armV7Source.isDirectory()) {
                     File dstDir = new File(tempRoot, "lib/armeabi-v7a/");
                     if (!dstDir.exists() && !dstDir.mkdirs()) {
                         deleteRec(tempRoot);
                         throw new IOException("Failed to create ABI dir: " + dstDir);
                     }
                     File[] soFiles = armV7Source.listFiles((d, n) -> n != null && n.endsWith(".so"));
                     if (soFiles != null) {
                         for (File so : soFiles) {
                             copyFile(so, new File(dstDir, so.getName()));
                         }
                     }
                }
            }
        }

        // Repack unsigned APK
        File unsignedApk = new File(ctx.getCacheDir(), "unsigned_" + System.nanoTime() + ".apk");
        zipDir(tempRoot, unsignedApk, patchedManifest);
        deleteRec(tempRoot);

        // Sign APK
        File signedApk = new File(ctx.getCacheDir(), "signed_" + System.nanoTime() + ".apk");
        try {
            signApk(unsignedApk, signedApk);
        } catch (Exception e) {
            Log.e(TAG, "Signing failed", e);
            unsignedApk.delete();
            signedApk.delete();
            throw e;
        }
        // Delete unsigned file
        unsignedApk.delete();

        // Write to output Uri
        try (OutputStream os = ctx.getContentResolver().openOutputStream(outApk)) {
            if (os == null) {
                throw new IOException("Cannot open output stream for: " + outApk);
            }
            copyFileToStream(signedApk, os);
        } catch (Exception e) {
            Log.e(TAG, "Error writing output APK", e);
            signedApk.delete();
            throw e;
        }

        signedApk.delete();
        Log.i(TAG, "APK injection + signing completed successfully");
    }

    private ManifestPatchResult patchManifest(byte[] raw, JSONObject clonerConfig) throws IOException {
        ResXmlDocument doc = new ResXmlDocument();
        doc.readBytes(new ByteArrayInputStream(raw));
        ResXmlElement root = doc.getDocumentElement();
        if (root == null || !E_MANIFEST.equals(root.getName()))
            throw new IOException("Invalid manifest");

        ResXmlAttribute pkgAttr = root.searchAttributeByName("package");
        if (pkgAttr == null) throw new IOException("No package attribute");

        String pkg = pkgAttr.getValueAsString();
        String originalPkg = pkg;

        // Remove build metadata attributes that might cause parsing errors on newer Android versions
        // These attributes can cause "package appears to be invalid" errors on Android 11+ (SDK 30+)
        removeAttribute(root, "android:compileSdkVersion");
        removeAttribute(root, "android:compileSdkVersionCodename");
        removeAttribute(root, "platformBuildVersionCode");
        removeAttribute(root, "platformBuildVersionName");
        // Remove additional problematic attributes that cause installation failures
        removeAttribute(root, "android:sharedUserId");
        removeAttribute(root, "android:sharedUserLabel");
        // Remove test instrumentation components that can cause validation failures
        removeTestInstrumentation(root);
        // Fix common manifest issues (meta-data placement, invalid permissions, etc.)
        fixManifestCommonIssues(root);

        ResXmlElement app = root.getElement(E_APPLICATION);
        if (app == null) throw new IOException("<application> missing");

        // Apply custom app name if provided
        if (clonerConfig != null) {
            String customAppName = clonerConfig.optString("app_name", "").trim();
            if (!customAppName.isEmpty()) {
                ResXmlAttribute labelAttr = app.searchAttributeByResourceId(ID_ANDROID_LABEL);
                if (labelAttr == null) {
                    labelAttr = app.searchAttributeByName("android:label");
                }
                if (labelAttr != null) {
                    try {
                        labelAttr.setValueAsString(customAppName);
                        Log.d(TAG, "App label changed to: " + customAppName);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to set custom app label, skipping", e);
                    }
                } else {
                    try {
                        app.getOrCreateAndroidAttribute("label", ID_ANDROID_LABEL)
                                .setValueAsString(customAppName);
                        Log.d(TAG, "App label added: " + customAppName);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to add custom app label, skipping", e);
                    }
                }
            }
        }

        // NOTE: Do NOT force extractNativeLibs="true" anymore; keep original behavior
        // to reduce risk of OEM-specific installer bugs.

        // Cloning mode / package rename (optional, driven by cloner.json)
        String cloningMode = CLONING_MODE_REPLACE;
        if (clonerConfig != null) {
            cloningMode = clonerConfig.optString(CLONING_MODE_KEY, CLONING_MODE_REPLACE);
        }
        if (CLONING_MODE_GENERATE.equalsIgnoreCase(cloningMode)) {
            String generatedPkg = generateVariantPackageName(pkg);
            if (generatedPkg != null && !generatedPkg.isEmpty() && !generatedPkg.equals(pkg)) {
                Log.d(TAG, "Cloning mode enabled: package will change from " + pkg + " to " + generatedPkg);
                pkgAttr.setValueAsString(generatedPkg);
                pkg = generatedPkg;
                ensureAbsoluteComponentNames(root, app, originalPkg);
                updateProviderAuthorities(app, originalPkg, pkg);
            } else {
                Log.w(TAG, "Cloning mode requested package rename but no valid variant was produced. Keeping original package.");
            }
        } else if (CLONING_MODE_CUSTOM.equalsIgnoreCase(cloningMode)) {
            String customPkg = clonerConfig != null ? clonerConfig.optString(CUSTOM_PACKAGE_NAME_KEY, "").trim() : "";
            if (!customPkg.isEmpty() && isValidPackageName(customPkg) && !customPkg.equals(pkg)) {
                Log.d(TAG, "Custom package mode: package will change from " + pkg + " to " + customPkg);
                pkgAttr.setValueAsString(customPkg);
                pkg = customPkg;
                ensureAbsoluteComponentNames(root, app, originalPkg);
                updateProviderAuthorities(app, originalPkg, pkg);
            } else {
                Log.w(TAG, "Custom package mode requested but no valid custom package name provided. Keeping original package.");
            }
        }

        String auth = pkg + "." + PROVIDER_AUTHORITY;
        Log.d(TAG, "Processing package: " + pkg + ", provider authority: " + auth);

        // Ensure required permissions
        addPermissionIfMissing(root, PERM_READ_EXTERNAL);
        addPermissionIfMissing(root, PERM_WRITE_EXTERNAL);
        addPermissionIfMissing(root, IPC_PERMISSION);

        // Inject location permissions if location spoofing is enabled
        if (clonerConfig != null && clonerConfig.optBoolean("SpoofLocation", false)) {
            addPermissionIfMissing(root, "android.permission.ACCESS_FINE_LOCATION");
            addPermissionIfMissing(root, "android.permission.ACCESS_COARSE_LOCATION");
        }
        
        // Inject camera permissions if fake camera is enabled
        if (clonerConfig != null && clonerConfig.optBoolean("FakeCamera", false)) {
            addPermissionIfMissing(root, "android.permission.CAMERA");
        }
        
        // Inject internet permission for background media, proxy, etc.
        if (clonerConfig != null && (clonerConfig.optBoolean("background_media", false) ||
                clonerConfig.optBoolean("socks_proxy", false) ||
                clonerConfig.optBoolean("internal_browser_enabled", false))) {
            addPermissionIfMissing(root, "android.permission.INTERNET");
            addPermissionIfMissing(root, "android.permission.ACCESS_NETWORK_STATE");
        }
        
        // Inject foreground service permission for background media
        if (clonerConfig != null && clonerConfig.optBoolean("background_media", false)) {
            addPermissionIfMissing(root, "android.permission.FOREGROUND_SERVICE");
        }

        // Provider injection or detection
        boolean providerInjectedOrPresent = false;
        for (ResXmlElement p : app.listElements(E_PROVIDER)) {
            ResXmlAttribute a = p.searchAttributeByResourceId(ID_ANDROID_AUTHORITIES);
            if (a != null && auth.equals(a.getValueAsString())) {
                Log.d(TAG, "Provider already present.");
                providerInjectedOrPresent = true;
                // Force export=true even if present (fix for re-cloning scenarios)
                p.getOrCreateAndroidAttribute(A_EXPORTED, ID_ANDROID_EXPORTED)
                        .setValueAsBoolean(true);
                p.getOrCreateAndroidAttribute(A_PERMISSION, ID_ANDROID_PERMISSION)
                        .setValueAsString(IPC_PERMISSION);
                break;
            }
        }

        boolean needInitOrder = true;
        ResXmlElement usesSdk = root.getElement("uses-sdk");
        if (usesSdk != null) {
            ResXmlAttribute minA = usesSdk.searchAttributeByResourceId(ID_ANDROID_MIN_SDK);
            if (minA != null) {
                try {
                    int min = -1;
                    ValueType vt = minA.getValueType();
                    if (vt == ValueType.DEC || vt == ValueType.HEX) {
                        min = minA.getData();
                    } else {
                        String s = minA.getValueAsString();
                        if (s != null) {
                            String numericOnly = s.replaceAll("[^0-9]", "");
                            if (!numericOnly.isEmpty()) min = Integer.parseInt(numericOnly);
                        }
                    }
                    if (min >= 0) {
                        needInitOrder = min >= 24;
                        Log.d(TAG, "MinSdk: " + min + ", needInitOrder: " + needInitOrder);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not parse minSdk, using default", e);
                }
            }
        }

        if (!providerInjectedOrPresent) {
            Log.d(TAG, "Injecting DefaultProvider");
            try {
                ResXmlElement prov = app.newElement(E_PROVIDER);
                prov.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                        .setValueAsString("com.applisto.appcloner.DefaultProvider");
                prov.getOrCreateAndroidAttribute(A_AUTHORITIES, ID_ANDROID_AUTHORITIES)
                        .setValueAsString(auth);
                prov.getOrCreateAndroidAttribute(A_EXPORTED, ID_ANDROID_EXPORTED)
                        .setValueAsBoolean(true);
                // Do not set android:permission here.
                // We rely on DefaultProvider.hasIpcPermissionOrSameApp() runtime check
                // to avoid issues where the system permission check fails even for the legitimate app.
                if (needInitOrder) {
                    ResXmlAttribute orderAttr = prov.getOrCreateAndroidAttribute(A_INIT_ORDER, ID_ANDROID_INIT_ORDER);
                    orderAttr.setData(Integer.MAX_VALUE);
                    orderAttr.setValueType(ValueType.DEC);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to inject provider, continuing without it", e);
                // Continue without provider injection - may still work for some apps
            }
        }

        boolean identityNotificationsEnabled = clonerConfig != null
                && clonerConfig.optBoolean("identity_notifications", false);

        // IdentityRegenerationReceiver (only when identity notifications are enabled)
        if (identityNotificationsEnabled) {
            boolean identityReceiverExists = false;
            for (ResXmlElement r : app.listElements(E_RECEIVER)) {
                ResXmlAttribute nameAttr = r.searchAttributeByResourceId(ID_ANDROID_NAME);
                if (nameAttr != null && IDENTITY_REGENERATION_RECEIVER_NAME.equals(nameAttr.getValueAsString())) {
                    identityReceiverExists = true;
                    break;
                }
            }
            if (!identityReceiverExists) {
                Log.d(TAG, "Injecting IdentityRegenerationReceiver");
                try {
                    ResXmlElement receiver = app.newElement(E_RECEIVER);
                    receiver.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                            .setValueAsString(IDENTITY_REGENERATION_RECEIVER_NAME);
                    receiver.getOrCreateAndroidAttribute(A_EXPORTED, ID_ANDROID_EXPORTED)
                            .setValueAsBoolean(true);
                    receiver.getOrCreateAndroidAttribute(A_PERMISSION, ID_ANDROID_PERMISSION)
                            .setValueAsString(IPC_PERMISSION);

                    ResXmlElement intentFilter = receiver.newElement(E_INTENT_FILTER);
                    ResXmlElement action = intentFilter.newElement(E_ACTION);
                    action.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                            .setValueAsString(ACTION_REGENERATE_IDENTITY);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to inject IdentityRegenerationReceiver, continuing without it", e);
                }
            }
        }

        // DataExportReceiver
        boolean receiverExists = false;
        for (ResXmlElement r : app.listElements(E_RECEIVER)) {
            ResXmlAttribute nameAttr = r.searchAttributeByResourceId(ID_ANDROID_NAME);
            if (nameAttr != null && RECEIVER_NAME.equals(nameAttr.getValueAsString())) {
                receiverExists = true;
                break;
            }
        }
        if (!receiverExists) {
            Log.d(TAG, "Injecting DataExportReceiver");
            try {
                ResXmlElement receiver = app.newElement(E_RECEIVER);
                receiver.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                        .setValueAsString(RECEIVER_NAME);
                receiver.getOrCreateAndroidAttribute(A_EXPORTED, ID_ANDROID_EXPORTED)
                        .setValueAsBoolean(true);
                // Require IPC permission to receive broadcasts
                receiver.getOrCreateAndroidAttribute(A_PERMISSION, ID_ANDROID_PERMISSION)
                        .setValueAsString(IPC_PERMISSION);

                ResXmlElement intentFilter = receiver.newElement(E_INTENT_FILTER);
                ResXmlElement action = intentFilter.newElement(E_ACTION);
                action.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                        .setValueAsString(EXPORT_ACTION);
            } catch (Exception e) {
                Log.e(TAG, "Failed to inject DataExportReceiver, continuing without it", e);
            }
        }

        // CameraControlReceiver
        boolean cameraControlReceiverExists = false;
        for (ResXmlElement r : app.listElements(E_RECEIVER)) {
            ResXmlAttribute nameAttr = r.searchAttributeByResourceId(ID_ANDROID_NAME);
            if (nameAttr != null && CAMERA_CONTROL_RECEIVER_NAME.equals(nameAttr.getValueAsString())) {
                cameraControlReceiverExists = true;
                break;
            }
        }
        if (!cameraControlReceiverExists) {
            Log.d(TAG, "Injecting CameraControlReceiver");
            try {
                ResXmlElement receiver = app.newElement(E_RECEIVER);
                receiver.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                        .setValueAsString(CAMERA_CONTROL_RECEIVER_NAME);
                receiver.getOrCreateAndroidAttribute(A_EXPORTED, ID_ANDROID_EXPORTED)
                        .setValueAsBoolean(true);

                ResXmlElement intentFilter = receiver.newElement(E_INTENT_FILTER);
                String[] actions = new String[] {
                        "com.applisto.appcloner.ACTION_ROTATE_CLOCKWISE",
                        "com.applisto.appcloner.ACTION_ROTATE_COUNTERCLOCKWISE",
                        "com.applisto.appcloner.ACTION_FLIP_HORIZONTALLY",
                        "com.applisto.appcloner.ACTION_ZOOM_IN",
                        "com.applisto.appcloner.ACTION_ZOOM_OUT"
                };
                for (String actionName : actions) {
                    ResXmlElement action = intentFilter.newElement(E_ACTION);
                    action.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                            .setValueAsString(actionName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to inject CameraControlReceiver, continuing without it", e);
            }
        }

        // FakeCameraActivity - only inject when fake camera is enabled
        boolean fakeCameraEnabled = clonerConfig != null && clonerConfig.optBoolean("FakeCamera", false);
        if (fakeCameraEnabled) {
            boolean fakeCameraActivityExists = false;
            ResXmlElement fakeCameraActivity = null;
            for (ResXmlElement a : app.listElements(E_ACTIVITY)) {
                ResXmlAttribute nameAttr = a.searchAttributeByResourceId(ID_ANDROID_NAME);
                if (nameAttr != null && FAKE_CAMERA_ACTIVITY_NAME.equals(nameAttr.getValueAsString())) {
                    fakeCameraActivityExists = true;
                    fakeCameraActivity = a;
                    break;
                }
            }
            if (!fakeCameraActivityExists) {
                Log.d(TAG, "Injecting FakeCameraActivity (fake camera enabled)");
                try {
                    ResXmlElement activity = app.newElement(E_ACTIVITY);
                    activity.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                            .setValueAsString(FAKE_CAMERA_ACTIVITY_NAME);
                    activity.getOrCreateAndroidAttribute(A_EXPORTED, ID_ANDROID_EXPORTED)
                            .setValueAsBoolean(true);
                    fakeCameraActivity = activity;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to inject FakeCameraActivity, continuing without it", e);
                }
            }
            // Theme setup kept disabled to avoid potential manifest corruption issues.
            // ensureFakeCameraActivityTheme(fakeCameraActivity);
        } else {
            Log.d(TAG, "Skipping FakeCameraActivity injection (fake camera disabled)");
        }

        // InternalBrowserActivity - inject when internal_browser_enabled is true
        boolean internalBrowserEnabled = clonerConfig != null && clonerConfig.optBoolean("internal_browser_enabled", false);
        if (internalBrowserEnabled) {
            boolean browserActivityExists = false;
            ResXmlElement existingBrowserActivity = null;
            for (ResXmlElement a : app.listElements(E_ACTIVITY)) {
                ResXmlAttribute nameAttr = a.searchAttributeByResourceId(ID_ANDROID_NAME);
                if (nameAttr != null && INTERNAL_BROWSER_ACTIVITY_NAME.equals(nameAttr.getValueAsString())) {
                    browserActivityExists = true;
                    existingBrowserActivity = a;
                    break;
                }
            }
            if (!browserActivityExists) {
                Log.d(TAG, "Injecting InternalBrowserActivity");
                try {
                    ResXmlElement activity = app.newElement(E_ACTIVITY);
                    activity.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                            .setValueAsString(INTERNAL_BROWSER_ACTIVITY_NAME);
                    activity.getOrCreateAndroidAttribute(A_EXPORTED, ID_ANDROID_EXPORTED)
                            .setValueAsBoolean(true);
                    
                    // Set theme to Material Light NoActionBar for proper WebView display
                    // Theme.Material.Light.NoActionBar = 0x01030224
                    try {
                        ResXmlAttribute themeAttr = activity.getOrCreateAndroidAttribute("theme", ID_ANDROID_THEME);
                        themeAttr.setValueType(ValueType.REFERENCE);
                        themeAttr.setData(0x01030224); // android.R.style.Theme_Material_Light_NoActionBar
                    } catch (Exception te) {
                        Log.w(TAG, "Failed to set theme for InternalBrowserActivity, continuing", te);
                    }
                    
                    // Set configChanges for orientation|screenSize|keyboardHidden
                    // orientation=0x0080, screenSize=0x0400, keyboardHidden=0x0020 = 0x04A0
                    try {
                        ResXmlAttribute configAttr = activity.getOrCreateAndroidAttribute("configChanges", ID_ANDROID_CONFIG_CHANGES);
                        configAttr.setValueType(ValueType.HEX);
                        configAttr.setData(0x04A0);
                    } catch (Exception ce) {
                        Log.w(TAG, "Failed to set configChanges for InternalBrowserActivity, continuing", ce);
                    }
                    
                    // Add intent filter for VIEW action with http/https schemes
                    ResXmlElement intentFilter = activity.newElement(E_INTENT_FILTER);
                    
                    ResXmlElement action = intentFilter.newElement(E_ACTION);
                    action.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                            .setValueAsString("android.intent.action.VIEW");
                    
                    ResXmlElement categoryDefault = intentFilter.newElement("category");
                    categoryDefault.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                            .setValueAsString("android.intent.category.DEFAULT");
                    
                    ResXmlElement categoryBrowsable = intentFilter.newElement("category");
                    categoryBrowsable.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                            .setValueAsString("android.intent.category.BROWSABLE");
                    
                    // Add data elements for http and https schemes
                    ResXmlElement dataHttp = intentFilter.newElement("data");
                    dataHttp.getOrCreateAndroidAttribute("scheme", 0x01010027)
                            .setValueAsString("http");
                    
                    ResXmlElement dataHttps = intentFilter.newElement("data");
                    dataHttps.getOrCreateAndroidAttribute("scheme", 0x01010027)
                            .setValueAsString("https");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Failed to inject InternalBrowserActivity", e);
                }
            } else {
                // Activity already exists, ensure it has required attributes
                Log.d(TAG, "InternalBrowserActivity already exists, ensuring attributes are set");
                try {
                    existingBrowserActivity.getOrCreateAndroidAttribute(A_EXPORTED, ID_ANDROID_EXPORTED)
                            .setValueAsBoolean(true);
                    
                    // Ensure theme is set
                    ResXmlAttribute themeAttr = existingBrowserActivity.searchAttributeByResourceId(ID_ANDROID_THEME);
                    if (themeAttr == null) {
                        try {
                            themeAttr = existingBrowserActivity.getOrCreateAndroidAttribute("theme", ID_ANDROID_THEME);
                            themeAttr.setValueType(ValueType.REFERENCE);
                            themeAttr.setData(0x01030224);
                        } catch (Exception te) {
                            Log.w(TAG, "Failed to set theme for existing InternalBrowserActivity", te);
                        }
                    }
                    
                    // Ensure configChanges is set
                    ResXmlAttribute configAttr = existingBrowserActivity.searchAttributeByResourceId(ID_ANDROID_CONFIG_CHANGES);
                    if (configAttr == null) {
                        try {
                            configAttr = existingBrowserActivity.getOrCreateAndroidAttribute("configChanges", ID_ANDROID_CONFIG_CHANGES);
                            configAttr.setValueType(ValueType.HEX);
                            configAttr.setData(0x04A0);
                        } catch (Exception ce) {
                            Log.w(TAG, "Failed to set configChanges for existing InternalBrowserActivity", ce);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to update existing InternalBrowserActivity attributes", e);
                }
            }
        }

        // FakeCalculatorActivity
        boolean fakeCalculatorEnabled = clonerConfig != null && clonerConfig.optBoolean("fake_calculator_enabled", false);
        String originalLauncherName = null;

        if (fakeCalculatorEnabled) {
            // Find original launcher activity and remove LAUNCHER category
            for (ResXmlElement a : app.listElements(E_ACTIVITY)) {
                ResXmlElement intentFilter = null;
                ResXmlElement launcherCategory = null;
                boolean isMain = false;

                for (ResXmlElement filter : a.listElements(E_INTENT_FILTER)) {
                    for (ResXmlElement action : filter.listElements(E_ACTION)) {
                        ResXmlAttribute nameAttr = action.searchAttributeByResourceId(ID_ANDROID_NAME);
                        if (nameAttr == null) nameAttr = action.searchAttributeByName("android:name");
                        if (nameAttr != null && "android.intent.action.MAIN".equals(nameAttr.getValueAsString())) {
                            isMain = true;
                        }
                    }
                    if (isMain) {
                        for (ResXmlElement category : filter.listElements("category")) {
                            ResXmlAttribute nameAttr = category.searchAttributeByResourceId(ID_ANDROID_NAME);
                            if (nameAttr == null) nameAttr = category.searchAttributeByName("android:name");
                            if (nameAttr != null && "android.intent.category.LAUNCHER".equals(nameAttr.getValueAsString())) {
                                launcherCategory = category;
                                intentFilter = filter;
                                break;
                            }
                        }
                    }
                    if (launcherCategory != null) break;
                    isMain = false; // reset if not found in this filter
                }

                if (launcherCategory != null) {
                    ResXmlAttribute nameAttr = a.searchAttributeByResourceId(ID_ANDROID_NAME);
                    if (nameAttr == null) nameAttr = a.searchAttributeByName("android:name");
                    if (nameAttr != null) {
                        originalLauncherName = nameAttr.getValueAsString();
                        Log.d(TAG, "Found original launcher activity: " + originalLauncherName);
                        // Remove intent filter (simplest way to remove launcher entry)
                        // Note: ideally we just remove the category, but removing the whole filter is often safer/easier
                        // if it's just the main entry point.
                        // However, to be precise, let's remove just the filter that contains MAIN+LAUNCHER.
                        intentFilter.removeSelf();
                        Log.d(TAG, "Removed MAIN/LAUNCHER filter from original activity.");
                        break;
                    }
                }
            }
        }

        boolean fakeCalculatorActivityExists = false;
        ResXmlElement fakeCalculatorActivity = null;
        for (ResXmlElement a : app.listElements(E_ACTIVITY)) {
            ResXmlAttribute nameAttr = a.searchAttributeByResourceId(ID_ANDROID_NAME);
            if (nameAttr != null && FAKE_CALCULATOR_ACTIVITY_NAME.equals(nameAttr.getValueAsString())) {
                fakeCalculatorActivityExists = true;
                fakeCalculatorActivity = a;
                break;
            }
        }

        if (!fakeCalculatorActivityExists) {
            Log.d(TAG, "Injecting FakeCalculatorActivity");
            try {
                ResXmlElement activity = app.newElement(E_ACTIVITY);
                activity.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                        .setValueAsString(FAKE_CALCULATOR_ACTIVITY_NAME);
                fakeCalculatorActivity = activity;
            } catch (Exception e) {
                Log.e(TAG, "Failed to inject FakeCalculatorActivity", e);
            }
        }

        if (fakeCalculatorActivity != null) {
            if (fakeCalculatorEnabled) {
                // Configure as launcher
                fakeCalculatorActivity.getOrCreateAndroidAttribute(A_EXPORTED, ID_ANDROID_EXPORTED)
                        .setValueAsBoolean(true);

                // Set orientation to portrait (screenOrientation=1)
                ResXmlAttribute orientationAttr = fakeCalculatorActivity.getOrCreateAndroidAttribute("screenOrientation", ID_ANDROID_SCREEN_ORIENTATION);
                orientationAttr.setValueType(ValueType.DEC);
                orientationAttr.setData(1); // 1 = portrait

                // Add intent filter
                ResXmlElement filter = fakeCalculatorActivity.newElement(E_INTENT_FILTER);
                ResXmlElement action = filter.newElement(E_ACTION);
                action.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                        .setValueAsString("android.intent.action.MAIN");
                ResXmlElement category = filter.newElement("category");
                category.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                        .setValueAsString("android.intent.category.LAUNCHER");

                // Add meta-data for original launcher
                if (originalLauncherName != null) {
                    ResXmlElement meta = fakeCalculatorActivity.newElement("meta-data");
                    meta.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                            .setValueAsString("com.applisto.appcloner.original_launcher_activity");
                    meta.getOrCreateAndroidAttribute("value", ID_ANDROID_VALUE)
                            .setValueAsString(originalLauncherName);
                }
            } else {
                // Just ensure it exists but isn't exported (default behavior from before)
                fakeCalculatorActivity.getOrCreateAndroidAttribute(A_EXPORTED, ID_ANDROID_EXPORTED)
                        .setValueAsBoolean(false);
            }
        }

        doc.refresh();

        ManifestPatchResult result = new ManifestPatchResult();
        result.manifestBytes = doc.getBytes();
        return result;
    }

    private String generateVariantPackageName(String pkg) {
        if (pkg == null) {
            return null;
        }
        String trimmed = pkg.trim();
        if (trimmed.isEmpty()) {
            return pkg;
        }
        char[] chars = trimmed.toCharArray();
        for (int i = chars.length - 1; i >= 0; i--) {
            char c = chars[i];
            if (Character.isLetter(c)) {
                if (Character.isUpperCase(c)) {
                    chars[i] = (c == 'Z') ? 'A' : (char) (c + 1);
                } else {
                    chars[i] = (c == 'z') ? 'a' : (char) (c + 1);
                }
                return new String(chars);
            }
        }
        return trimmed + "a";
    }

    private boolean isValidPackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        // Package name must have at least two segments separated by dot
        if (!packageName.contains(".")) {
            return false;
        }
        // Check for valid characters and format
        String[] segments = packageName.split("\\.");
        if (segments.length < 2) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty()) {
                return false;
            }
            // First character must be a letter or underscore
            char first = segment.charAt(0);
            if (!Character.isLetter(first) && first != '_') {
                return false;
            }
            // Rest can be letters, digits, or underscores
            for (int i = 1; i < segment.length(); i++) {
                char c = segment.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    return false;
                }
            }
        }
        return true;
    }

    private void ensureAbsoluteComponentNames(ResXmlElement root, ResXmlElement app, String originalPkg) {
        if (originalPkg == null || originalPkg.isEmpty()) {
            return;
        }

        if (app != null) {
            ensureAbsoluteClassReference(app.searchAttributeByResourceId(ID_ANDROID_NAME), originalPkg);
            ensureAbsoluteClassReference(app.searchAttributeByName("android:name"), originalPkg);
            ensureAbsoluteClassReference(app.searchAttributeByName("android:appComponentFactory"), originalPkg);
            ensureAbsoluteClassReference(app.searchAttributeByName("android:backupAgent"), originalPkg);
            ensureAbsoluteClassReference(app.searchAttributeByName("android:manageSpaceActivity"), originalPkg);
        }

        if (app != null) {
            normalizeComponentCollection(app.listElements(E_ACTIVITY), originalPkg, true);
            normalizeComponentCollection(app.listElements("activity-alias"), originalPkg, true);
            normalizeComponentCollection(app.listElements("service"), originalPkg, false);
            normalizeComponentCollection(app.listElements(E_RECEIVER), originalPkg, false);
            normalizeComponentCollection(app.listElements(E_PROVIDER), originalPkg, false);
        }

        for (ResXmlElement instrumentation : root.listElements("instrumentation")) {
            ensureAbsoluteClassReference(instrumentation.searchAttributeByResourceId(ID_ANDROID_NAME), originalPkg);
            ensureAbsoluteClassReference(instrumentation.searchAttributeByName("android:name"), originalPkg);
        }
    }

    private void normalizeComponentCollection(Iterable<ResXmlElement> elements,
                                              String originalPkg,
                                              boolean includeActivityExtras) {
        if (elements == null) {
            return;
        }
        for (ResXmlElement element : elements) {
            if (element == null) {
                continue;
            }
            ensureAbsoluteClassReference(element.searchAttributeByResourceId(ID_ANDROID_NAME), originalPkg);
            ensureAbsoluteClassReference(element.searchAttributeByName("android:name"), originalPkg);
            if (includeActivityExtras) {
                ensureAbsoluteClassReference(element.searchAttributeByName("android:targetActivity"), originalPkg);
                ensureAbsoluteClassReference(element.searchAttributeByName("android:parentActivityName"), originalPkg);
            }
        }
    }

    private void ensureAbsoluteClassReference(ResXmlAttribute attr, String originalPkg) {
        if (attr == null) {
            return;
        }
        String value = attr.getValueAsString();
        if (value == null || value.isEmpty()) {
            return;
        }

        // Already absolute (com.foo.Bar) – leave unchanged
        if (value.contains(".") && !value.startsWith(".")) {
            return;
        }

        if (value.startsWith(".")) {
            attr.setValueAsString(originalPkg + value);
        } else if (!value.contains(".")) {
            attr.setValueAsString(originalPkg + "." + value);
        }
    }

    private void updateProviderAuthorities(ResXmlElement app, String originalPkg, String newPkg) {
        if (app == null || originalPkg == null || originalPkg.isEmpty()
                || newPkg == null || newPkg.isEmpty()) {
            return;
        }
        for (ResXmlElement provider : app.listElements(E_PROVIDER)) {
            ResXmlAttribute authAttr = provider.searchAttributeByResourceId(ID_ANDROID_AUTHORITIES);
            if (authAttr == null) {
                authAttr = provider.searchAttributeByName("android:authorities");
            }
            if (authAttr == null) {
                continue;
            }
            String value = authAttr.getValueAsString();
            if (value == null || value.isEmpty()) {
                continue;
            }
            String updated = rewriteAuthorityValue(value, originalPkg, newPkg);
            if (updated != null && !updated.equals(value)) {
                authAttr.setValueAsString(updated);
            }
        }
    }

    private String rewriteAuthorityValue(String value, String originalPkg, String newPkg) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return value;
        }
        if (originalPkg == null || originalPkg.isEmpty() || newPkg == null || newPkg.isEmpty()) {
            return value;
        }

        String delimiter = null;
        if (value.contains(";")) {
            delimiter = ";";
        } else if (value.contains(",")) {
            delimiter = ",";
        }

        if (delimiter == null) {
            String updatedSingle = rewriteSingleAuthority(trimmed, originalPkg, newPkg);
            return updatedSingle.equals(trimmed) ? value : updatedSingle;
        }

        String regex = "\\s*" + Pattern.quote(delimiter) + "\\s*";
        String[] parts = value.split(regex);
        boolean changed = false;
        for (int i = 0; i < parts.length; i++) {
            String originalPart = parts[i].trim();
            String updatedPart = rewriteSingleAuthority(originalPart, originalPkg, newPkg);
            if (!updatedPart.equals(originalPart)) {
                parts[i] = updatedPart;
                changed = true;
            } else {
                parts[i] = originalPart;
            }
        }
        if (!changed) {
            return value;
        }
        return String.join(delimiter, Arrays.asList(parts));
    }

    private String rewriteSingleAuthority(String authority, String originalPkg, String newPkg) {
        if (authority == null || authority.isEmpty()) {
            return authority;
        }
        if (authority.equals(originalPkg)) {
            return newPkg;
        }
        if (authority.startsWith(originalPkg + ".")) {
            return newPkg + authority.substring(originalPkg.length());
        }
        return authority;
    }

    private void ensureFakeCameraActivityTheme(ResXmlElement activityElement) {
        if (activityElement == null) {
            return;
        }
        ResXmlAttribute themeAttr = activityElement.getOrCreateAndroidAttribute("theme", ID_ANDROID_THEME);
        try {
            themeAttr.setValueAsString("@android:style/Theme.NoTitleBar.Fullscreen");
            themeAttr.setValueType(ValueType.REFERENCE);
            themeAttr.setData(android.R.style.Theme_NoTitleBar_Fullscreen);
            if (themeAttr.getData() == 0) {
                throw new IllegalStateException("Theme reference resolved to ID 0");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to encode FakeCameraActivity theme; removing theme attribute to avoid installer parse errors.", t);
            themeAttr.removeSelf();
        }
    }

    private void addPermissionIfMissing(ResXmlElement root, String permission) {
        try {
            for (ResXmlElement perm : root.listElements(E_USES_PERMISSION)) {
                ResXmlAttribute nameAttr = perm.searchAttributeByResourceId(ID_ANDROID_NAME);
                if (nameAttr != null && permission.equals(nameAttr.getValueAsString())) {
                    return;
                }
            }
            ResXmlElement permElement = root.newElement(E_USES_PERMISSION);
            permElement.getOrCreateAndroidAttribute(A_NAME, ID_ANDROID_NAME)
                    .setValueAsString(permission);
            Log.d(TAG, "Added permission: " + permission);
        } catch (Exception e) {
            Log.w(TAG, "Failed to add permission " + permission + ", continuing without it", e);
        }
    }

    // --- Manifest validation to catch corruption early ---
    private void validateManifest(byte[] manifestBytes) throws IOException {
        if (manifestBytes == null || manifestBytes.length == 0) {
            throw new IOException("Manifest bytes are empty");
        }
        ResXmlDocument testDoc = new ResXmlDocument();
        testDoc.readBytes(new ByteArrayInputStream(manifestBytes));
        ResXmlElement root = testDoc.getDocumentElement();
        if (root == null || !E_MANIFEST.equals(root.getName())) {
            throw new IOException("Patched manifest is invalid (root element)");
        }
        if (root.getElement(E_APPLICATION) == null) {
            throw new IOException("Patched manifest is invalid (<application> missing)");
        }
    }

    // --- ZIP packing ---

    private void zipDir(File root, File outFile, byte[] manifestBytes) throws IOException {
        if (manifestBytes == null || manifestBytes.length == 0) {
            throw new IOException("Manifest bytes are null/empty when zipping APK");
        }
        try (OutputStream os = new FileOutputStream(outFile);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))) {
            zos.setLevel(9);
            writeZip(root, zos, manifestBytes);
        }
    }

    private void writeZip(File root, ZipOutputStream zos, byte[] manifestBytes) throws IOException {
        // Write manifest first
        ZipEntry manifestEntry = createZipEntry(ANDROID_MANIFEST, manifestBytes);
        zos.putNextEntry(manifestEntry);
        zos.write(manifestBytes);
        zos.closeEntry();

        // Then all other files
        addRec(root, root.getAbsolutePath(), zos);
    }

    private int replaceLauncherIcons(File apkRoot, File newIconFile) {
        File resDir = new File(apkRoot, "res");
        if (!resDir.isDirectory()) {
            Log.w(TAG, "No res directory found when attempting to replace launcher icons");
            return 0;
        }

        java.util.List<File> targets = new ArrayList<>();
        collectLauncherIconTargets(resDir, targets);
        if (targets.isEmpty()) {
            Log.w(TAG, "No launcher icon resources found to replace");
            return 0;
        }

        Bitmap cachedBitmap = null;
        int replaced = 0;
        for (File target : targets) {
            try {
                String nameLower = target.getName().toLowerCase(Locale.US);
                if (nameLower.endsWith(".webp")) {
                    if (cachedBitmap == null) {
                        cachedBitmap = BitmapFactory.decodeFile(newIconFile.getAbsolutePath());
                    }
                    if (cachedBitmap != null) {
                        try (FileOutputStream fos = new FileOutputStream(target)) {
                            cachedBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, fos);
                            replaced++;
                        }
                    } else {
                        Log.w(TAG, "Failed to decode processed icon for WebP conversion; skipping " + target.getAbsolutePath());
                    }
                } else {
                    copyFile(newIconFile, target);
                    replaced++;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to replace launcher icon at " + target.getAbsolutePath(), e);
            }
        }

        return replaced;
    }

    private void collectLauncherIconTargets(File dir, java.util.List<File> targets) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                String nameLower = f.getName().toLowerCase(Locale.US);
                if (nameLower.startsWith("mipmap") || nameLower.startsWith("drawable")) {
                    collectLauncherIconTargets(f, targets);
                }
                continue;
            }

            String nameLower = f.getName().toLowerCase(Locale.US);
            boolean looksLikeLauncher = nameLower.startsWith("ic_launcher") ||
                    nameLower.startsWith("ic_launcher_round") ||
                    nameLower.startsWith("ic_launcher_foreground") ||
                    nameLower.startsWith("ic_launcher_background") ||
                    nameLower.startsWith("app_icon") ||
                    nameLower.startsWith("launcher_icon");
            boolean supportedExt = nameLower.endsWith(".png") || nameLower.endsWith(".webp");
            if (looksLikeLauncher && supportedExt) {
                targets.add(f);
            }
        }
    }

    private void addRec(File node, String base, ZipOutputStream zos) throws IOException {
        if (node.isDirectory()) {
            File[] kids = node.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    addRec(k, base, zos);
                }
            }
            return;
        }

        String rel = node.getAbsolutePath()
                .substring(base.length() + 1)
                .replace(File.separatorChar, '/');

        // Manifest already written
        if (ANDROID_MANIFEST.equals(rel)) return;

        ZipEntry entry = createZipEntry(rel, node);
        zos.putNextEntry(entry);
        try (FileInputStream fis = new FileInputStream(node)) {
            copyStream(fis, zos);
        }
        zos.closeEntry();
    }

    private ZipEntry createZipEntry(String name, byte[] data) {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0L);
        // Manifest can be safely compressed
        e.setMethod(ZipEntry.DEFLATED);
        return e;
    }

    private ZipEntry createZipEntry(String name, File file) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0L);
        String lower = name.toLowerCase(Locale.US);

        // Store resources.arsc and native libs uncompressed; everything else deflated.
        boolean store = lower.endsWith(".arsc") ||
                        (lower.startsWith("lib/") && lower.endsWith(".so"));

        if (store) {
            e.setMethod(ZipEntry.STORED);
            long size = file.length();
            e.setSize(size);
            e.setCompressedSize(size);

            CRC32 crc = new CRC32();
            try (InputStream in = new FileInputStream(file)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) {
                    crc.update(buf, 0, r);
                }
            }
            e.setCrc(crc.getValue());
        } else {
            e.setMethod(ZipEntry.DEFLATED);
        }
        return e;
    }

    // --- Signing ---

    private void signApk(File in, File out) throws Exception {
        Log.d(TAG, "Loading signer config...");
        ApkSigner.SignerConfig signer = loadSignerConfig();
        Log.d(TAG, "Signer config loaded. Building ApkSigner...");

        ApkSigner apkSigner = new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(in)
                .setOutputApk(out)
                .setV1SigningEnabled(true)    // V1 for Android < 7.0 compatibility
                .setV2SigningEnabled(true)    // V2 for Android 7.0+
                .setV3SigningEnabled(true)    // V3 for Android 9.0+ (required for newer devices)
                .build();

        Log.d(TAG, "Calling apkSigner.sign()...");
        apkSigner.sign();
        Log.d(TAG, "APK signed successfully.");

        // Best-effort verification (for logging only)
        try {
            Log.d(TAG, "Attempting APK verification...");
            Class<?> builderClass = Class.forName("com.android.apksig.ApkVerifier$Builder");
            Object builderInstance = builderClass.getConstructor(File.class).newInstance(out);
            Object apkVerifierInstance = builderClass.getMethod("build").invoke(builderInstance);
            Object vRes = apkVerifierInstance.getClass().getMethod("verify").invoke(apkVerifierInstance);
            Boolean isVerified = (Boolean) vRes.getClass().getMethod("isVerified").invoke(vRes);
            if (isVerified != null && !isVerified) {
                Log.w(TAG, "APK verification result: NOT VERIFIED (continuing)");
            } else {
                Log.d(TAG, "APK signature verification passed (or skipped).");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Verification skipped due to error", t);
        }
        Log.d(TAG, "Signing process completed.");
    }

    private ApkSigner.SignerConfig loadSignerConfig() throws Exception {
        Log.d(TAG, "Loading keystore: " + KEYSTORE_ASSET + " with alias: " + ALIAS);
        try (InputStream ksStream = ctx.getAssets().open(KEYSTORE_ASSET)) {
            KeyStore ks = KeyStore.getInstance("BKS");
            ks.load(ksStream, STORE_PWD.toCharArray());

            PrivateKey key = (PrivateKey) ks.getKey(ALIAS, KEY_PWD.toCharArray());
            if (key == null) {
                throw new IllegalStateException("Private key is null for alias: " + ALIAS);
            }
            X509Certificate cert = (X509Certificate) ks.getCertificate(ALIAS);
            if (cert == null) {
                throw new IllegalStateException("Certificate is null for alias: " + ALIAS);
            }

            return new ApkSigner.SignerConfig.Builder(ALIAS, key, Collections.singletonList(cert)).build();
        }
    }

    // --- Utils ---

    private static File safeResolve(File root, String basePath, String entryName) throws IOException {
        File out = new File(root, entryName);
        String outPath = out.getCanonicalPath();
        if (!outPath.startsWith(basePath)) {
            throw new IOException("Blocked zip path traversal: " + entryName);
        }
        return out;
    }

    private static boolean isProbablyZip(File f) {
        if (!f.isFile() || f.length() < 4) return false;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            int b0 = raf.read();
            int b1 = raf.read();
            int b2 = raf.read();
            int b3 = raf.read();
            return b0 == 0x50 && b1 == 0x4b &&
                   (b2 == 0x03 || b2 == 0x05 || b2 == 0x07) &&
                   (b3 == 0x04 || b3 == 0x06 || b3 == 0x08);
        } catch (IOException e) {
            return false;
        }
    }

    private static void copyFile(File source, File dest) throws IOException {
        if (source == null || dest == null) {
            throw new IllegalArgumentException("Source and destination files cannot be null");
        }
        if (!source.exists() || !source.isFile()) {
            throw new IOException("Source file does not exist: " + source);
        }

        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory: " + parent);
        }

        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            copyStream(in, out);
        }
    }

    private static void copyFileToStream(File source, OutputStream dest) throws IOException {
        if (source == null || dest == null) {
            throw new IllegalArgumentException("Source file and destination stream cannot be null");
        }
        if (!source.exists() || !source.isFile()) {
            throw new IOException("Source file does not exist: " + source);
        }
        try (InputStream in = new FileInputStream(source)) {
            copyStream(in, dest);
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
    }

    private static void deleteRec(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteRec(k);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private void removeAttribute(ResXmlElement element, String name) {
        if (element == null || name == null) return;
        ResXmlAttribute attr = element.searchAttributeByName(name);
        if (attr != null) {
            attr.removeSelf();
        }
    }

    /**
     * Remove test instrumentation components from the manifest.
     * Test components like InstrumentationActivityInvoker$EmptyFloatingActivity can cause
     * "package appears to be invalid" errors on newer Android versions.
     */
    private void removeTestInstrumentation(ResXmlElement root) {
        if (root == null) return;
        
        try {
            // Remove <instrumentation> elements that reference test classes
            List<ResXmlElement> instrumentations = new ArrayList<>();
            for (ResXmlElement elem : root.listElements("instrumentation")) {
                instrumentations.add(elem);
            }
            for (ResXmlElement instr : instrumentations) {
                ResXmlAttribute nameAttr = instr.searchAttributeByResourceId(ID_ANDROID_NAME);
                if (nameAttr == null) {
                    nameAttr = instr.searchAttributeByName("android:name");
                }
                if (nameAttr != null) {
                    String name = nameAttr.getValueAsString();
                    if (name != null && (name.contains("test") || name.contains("Test") ||
                            name.contains("InstrumentationActivityInvoker"))) {
                        Log.d(TAG, "Removing test instrumentation: " + name);
                        instr.removeSelf();
                    }
                }
            }
            
            // Also remove test activities from the application element
            ResXmlElement app = root.getElement(E_APPLICATION);
            if (app != null) {
                List<ResXmlElement> activitiesToRemove = new ArrayList<>();
                for (ResXmlElement activity : app.listElements(E_ACTIVITY)) {
                    ResXmlAttribute nameAttr = activity.searchAttributeByResourceId(ID_ANDROID_NAME);
                    if (nameAttr == null) {
                        nameAttr = activity.searchAttributeByName("android:name");
                    }
                    if (nameAttr != null) {
                        String name = nameAttr.getValueAsString();
                        if (name != null && (name.contains("InstrumentationActivityInvoker") ||
                                name.contains("EmptyFloatingActivity") ||
                                name.contains("androidx.test"))) {
                            activitiesToRemove.add(activity);
                            Log.d(TAG, "Marking test activity for removal: " + name);
                        }
                    }
                }
                for (ResXmlElement activity : activitiesToRemove) {
                    Log.d(TAG, "Removing test activity");
                    activity.removeSelf();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error removing test instrumentation, continuing", e);
        }
    }

    private void fixManifestCommonIssues(ResXmlElement root) {
        if (root == null) return;

        ResXmlElement app = root.getElement(E_APPLICATION);
        if (app == null) return;

        // 1. Move <meta-data> from <manifest> to <application>
        List<ResXmlElement> metaDataToRemove = new ArrayList<>();
        for (ResXmlElement elem : root.listElements("meta-data")) {
            metaDataToRemove.add(elem);
        }
        for (ResXmlElement meta : metaDataToRemove) {
            root.remove(meta);
            app.add(meta);
            Log.d(TAG, "Moved <meta-data> from manifest to application");
        }

        // 2. Remove invalid permission definitions (starting with android.permission.)
        List<ResXmlElement> permissionsToRemove = new ArrayList<>();
        for (ResXmlElement perm : root.listElements("permission")) {
            ResXmlAttribute nameAttr = perm.searchAttributeByResourceId(ID_ANDROID_NAME);
            if (nameAttr == null) nameAttr = perm.searchAttributeByName("android:name");

            if (nameAttr != null) {
                String name = nameAttr.getValueAsString();
                if (name != null && name.startsWith("android.permission.")) {
                    permissionsToRemove.add(perm);
                    Log.d(TAG, "Removing invalid permission definition: " + name);
                }
            }
        }
        for (ResXmlElement p : permissionsToRemove) {
            p.removeSelf();
        }

        // 3. Force targetSdkVersion to 27
        ResXmlElement usesSdk = root.getElement("uses-sdk");
        if (usesSdk == null) {
            usesSdk = root.newElement("uses-sdk");
        }
        ResXmlAttribute targetSdkAttr = usesSdk.searchAttributeByResourceId(ID_ANDROID_TARGET_SDK_VERSION);
        if (targetSdkAttr == null) {
            targetSdkAttr = usesSdk.searchAttributeByName("android:targetSdkVersion");
        }
        if (targetSdkAttr == null) {
            targetSdkAttr = usesSdk.getOrCreateAndroidAttribute("targetSdkVersion", ID_ANDROID_TARGET_SDK_VERSION);
        }
        // Force to 27
        targetSdkAttr.setValueType(ValueType.DEC);
        targetSdkAttr.setData(27);
        Log.d(TAG, "Forced targetSdkVersion to 27");

        // 4. Application attributes
        // Remove android:debuggable
        ResXmlAttribute debuggable = app.searchAttributeByResourceId(ID_ANDROID_DEBUGGABLE);
        if (debuggable == null) debuggable = app.searchAttributeByName("android:debuggable");
        if (debuggable != null) {
            debuggable.removeSelf();
            Log.d(TAG, "Removed android:debuggable");
        }

        // Set android:testOnly="false"
        ResXmlAttribute testOnly = app.getOrCreateAndroidAttribute("testOnly", ID_ANDROID_TEST_ONLY);
        testOnly.setValueAsBoolean(false);
        Log.d(TAG, "Set android:testOnly=false");

        // 5. Disable Analytics
        addAnalyticsMetaData(app, "firebase_analytics_collection_enabled", "false");
        addAnalyticsMetaData(app, "google_analytics_adid_collection_enabled", "false");
        addAnalyticsMetaData(app, "google_analytics_automatic_screen_reporting_enabled", "false");
    }

    private void addAnalyticsMetaData(ResXmlElement app, String name, String value) {
        // Check if exists
        for (ResXmlElement meta : app.listElements("meta-data")) {
            ResXmlAttribute nameAttr = meta.searchAttributeByResourceId(ID_ANDROID_NAME);
            if (nameAttr == null) nameAttr = meta.searchAttributeByName("android:name");
            if (nameAttr != null && name.equals(nameAttr.getValueAsString())) {
                // Update existing
                ResXmlAttribute valAttr = meta.searchAttributeByResourceId(ID_ANDROID_VALUE);
                if (valAttr == null) valAttr = meta.getOrCreateAndroidAttribute("value", ID_ANDROID_VALUE);
                valAttr.setValueAsString(value);
                Log.d(TAG, "Updated meta-data: " + name + "=" + value);
                return;
            }
        }
        // Create new
        ResXmlElement meta = app.newElement("meta-data");
        meta.getOrCreateAndroidAttribute("name", ID_ANDROID_NAME).setValueAsString(name);
        meta.getOrCreateAndroidAttribute("value", ID_ANDROID_VALUE).setValueAsString(value);
        Log.d(TAG, "Added meta-data: " + name + "=" + value);
    }

    private static class ManifestPatchResult {
        byte[] manifestBytes;
    }
}
