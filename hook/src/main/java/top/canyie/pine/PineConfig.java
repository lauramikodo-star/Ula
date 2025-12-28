package top.canyie.pine;

/**
 * Minimal Pine configuration stub for compatibility with hooks.
 */
public final class PineConfig {
    private PineConfig() {
        // Utility class
    }

    /** Enable verbose Pine logging. */
    public static boolean debug = false;

    /** Indicate whether the host app is debuggable. */
    public static boolean debuggable = false;

    /**
     * Skip common detection checks when hooking on newer Android versions.
     */
    public static boolean antiChecks = false;

    /** Disable hidden API policy where possible on Android P+. */
    public static boolean disableHiddenApiPolicy = false;

    /** Also disable hidden API policy for platform domain on Android P+. */
    public static boolean disableHiddenApiPolicyForPlatformDomain = false;
}
