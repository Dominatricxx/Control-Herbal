# Reglas ProGuard/R8 del módulo Wear OS.
# Las librerías de Firebase y Play Services publican sus propias reglas "consumer";
# no se conservan paquetes enteros (-keep com.google.firebase.**) para no anular la ofuscación.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Elimina logs de nivel d/v/i en release (los w/e se conservan para diagnóstico).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
