# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.mpsbuilder.**$$serializer { *; }
-keepclassmembers class com.example.mpsbuilder.** { *** Companion; }
-keepclasseswithmembers class com.example.mpsbuilder.** { kotlinx.serialization.KSerializer serializer(...); }
