# ProGuard/R8 - ControlHerbal (app)
#
# Criterio: NO se conservan paquetes enteros de terceros (com.google.firebase.**, ai.**, etc.).
# Firebase, Room, WorkManager, TensorFlow Lite y Glance publican sus propias reglas "consumer";
# un -keep global anula la ofuscación/reducción y facilita la ingeniería inversa del APK.

# Entidades y DAOs de Room (acceso por nombre desde código generado)
-keep class com.example.controlherbal.data.database.** { *; }

# MPAndroidChart usa reflexión interna
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

-dontwarn io.ktor.**

# Elimina por completo las llamadas de log de bajo nivel en release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Números de línea para trazas de fallos (sin exponer nombres de archivo originales)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
