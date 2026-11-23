# Zyn Chat — R8 rules for the release build.
#
# Provider and conversation state are stored as JSON using Moshi's *reflective* adapter
# (KotlinJsonAdapterFactory), which reads Kotlin metadata through kotlin-reflect at runtime. With no
# rules, R8 obfuscates the models and strips the metadata they are read from, and that failure lands
# on the startup path — inside Application.onCreate — where it kills the app before any screen
# appears. These rules are what keep serialisation working in a minified build.

# Kotlin metadata and the reflection entry points kotlin-reflect resolves at runtime.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, MethodParameters
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn kotlin.reflect.**

# Serialised models: their property names are the JSON keys, so they must survive obfuscation.
# Without this, already-stored state becomes unreadable as soon as the mapping changes.
-keep class com.zyn.chat.app.data.providers.** { *; }
-keep class com.zyn.chat.app.data.chat.** { *; }
-keep class com.zyn.chat.app.domain.chat.** { *; }

# Moshi annotations carry the exact field names the providers expect on the wire.
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
}
-keepclasseswithmembers class ** {
    @com.squareup.moshi.Json <fields>;
}

# Retrofit and OkHttp read annotations off their interfaces and payload classes at runtime.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Keep the line numbers the crash screen reports, so a trace points at real code.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
