-keep class com.framescope.app.shizuku.** { *; }
-keep interface com.framescope.app.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class com.framescope.app.bridge.ShellBridgeMain { *; }
-keep class com.framescope.app.bridge.FrameScopeBridgeClient { *; }

-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# Strip verbose, debug, and info logs from release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
