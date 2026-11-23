# Lefiru obfuscation rules
-keep class com.lefiru.music.data.** { *; }
-keep class com.lefiru.music.playback.** { *; }

# Media3 / ExoPlayer (uses reflection)
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# DataStore (user preferences)
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Coil (album artwork loading)
-keep class io.coil-kt.** { *; }
-dontwarn io.coil-kt.**

# Kotlin Serialization
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# AndroidX lifecycle / compose
-dontwarn androidx.lifecycle.**
-dontwarn androidx.compose.**
