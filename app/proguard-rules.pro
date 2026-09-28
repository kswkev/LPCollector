# kotlinx.serialization: keep generated serializers for our DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.lpcollector.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lpcollector.**$$serializer { *; }

# ML Kit code scanner: its components are wired up via reflection (MlKitContext /
# firebase-components). R8 class merging breaks that and the scanner crashes with an
# NPE (SharedPrefManager resolves to null) as soon as it's opened.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
