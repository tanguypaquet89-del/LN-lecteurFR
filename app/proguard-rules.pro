# ProGuard & R8 Configuration for LN lecteurFR

# Application data models & serialization
-keep class com.nahrahviing.lecteurnovel.data.model.** { *; }
-keep class com.nahrahviing.lecteurnovel.data.entity.** { *; }
-keepclassmembers class com.nahrahviing.lecteurnovel.data.model.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class androidx.room.RoomDatabase { *; }

# Jsoup
-keep public class org.jsoup.** { public *; }
-dontwarn org.jsoup.**

# Google API Client & Google Drive
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}

# Google Play Services & Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-dontwarn com.google.android.gms.**

# Hilt & Dependency Injection
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class androidx.hilt.work.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
