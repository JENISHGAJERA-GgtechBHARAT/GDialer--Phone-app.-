# ProGuard Rules for GDialer
# Optimizes app footprint and removes unused methods, classes, and resources.

# Keep all classes containing JNI native methods so they don't break during NDK linking
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep our native RNNoise wrapper class and JNI callbacks
-keep class com.gg_tech_bharat.gdialer.ai.RNNoiseNative { *; }

# Keep WebRTC classes to prevent internal native callbacks from breaking
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep Database entities and DAOs for Room ORM
-keep class com.gg_tech_bharat.gdialer.** {
    @androidx.room.Entity *;
    @androidx.room.Dao *;
    @androidx.room.Database *;
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-dontwarn androidx.room.**

# Keep Glide image loading library components
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**
