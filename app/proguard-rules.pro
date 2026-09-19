# NOVA Proguard/R8 rules

# Keep Room entities and DAOs -- annotation processing already generates
# safe code, but keep names for easier crash-report debugging.
-keep class com.nova.assistant.data.db.** { *; }

# Keep model/POJO classes used for JSON (skills payloads, AI provider responses)
-keepclassmembers class com.nova.assistant.core.ai.** { *; }
-keepclassmembers class com.nova.assistant.core.skills.** { *; }

# OkHttp / okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*

# org.json
-keep class org.json.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
