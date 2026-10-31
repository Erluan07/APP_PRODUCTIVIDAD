# Kronos Focus: R8 con las reglas por defecto + las de cada librería (Compose, CameraX, ML Kit las traen incluidas).
-dontwarn org.json.**
-keep class com.kronos.focus.services.** { *; }
-keep class com.kronos.focus.receivers.** { *; }
-keep class com.kronos.focus.admin.** { *; }
# Motor de IA local (JNI): sus clases no se pueden renombrar ni quitar.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.kronos.focus.vision.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
# Trazas legibles (útil mientras la app esté en pruebas).
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
