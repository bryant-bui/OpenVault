# Preserve Kotlinx Serialization classes & serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.openvault.app.**$$serializer { *; }
-keepclassmembers class io.openvault.app.** {
    *** Companion;
}
-keepclasseswithmembers class io.openvault.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Preserve BouncyCastle Argon2 classes
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters** { *; }
