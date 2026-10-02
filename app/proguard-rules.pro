# ProGuard Advanced Security & Optimization Rules for ControlHerbal

# Keep Room Database entities and DAOs
-keep class com.example.controlherbal.data.database.** { *; }

# Keep AI and Logic models
-keep class com.example.controlherbal.ai.** { *; }
-keep class com.example.controlherbal.domain.logic.** { *; }

# Keep MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# Keep TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Keep Firebase & Generative AI SDKs
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn io.ktor.**
-dontwarn com.google.ai.client.generativeai.**

# Strip all logging calls in release builds for advanced data privacy & security
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(java.lang.String, java.lang.String);
    public static int v(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int d(java.lang.String, java.lang.String);
    public static int d(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int i(java.lang.String, java.lang.String);
    public static int i(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int w(java.lang.String, java.lang.String);
    public static int w(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int e(java.lang.String, java.lang.String);
    public static int e(java.lang.String, java.lang.String, java.lang.Throwable);
}

# Preserve line numbers for crash reporting stack traces if needed
-keepattributes SourceFile,LineNumberTable
