# MapLibre keeps its native interfaces; let R8 shrink the rest.
-keep class org.maplibre.android.** { *; }
-keep class com.mapbox.** { *; }

# Strip diagnostic logs in release. Log.w/e are kept (warnings/errors are
# rare and useful for crash triage).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
