# MusicDeck ProGuard / R8 Optimization & Obfuscation Rules

# Preserve Serialized fields for JSON parsing
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.wayne.musicdeck.update.** { *; }
-keep class com.wayne.musicdeck.data.model.** { *; }
-keep class com.wayne.musicdeck.data.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# MMKV Key-Value Storage
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# Retrofit, OkHttp, and Okio
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Koin Dependency Injection
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Media3 & ExoPlayer components
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil Image Loading
-keep class coil.** { *; }
-dontwarn coil.**
