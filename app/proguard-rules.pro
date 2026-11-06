# WebRTC rules
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Room rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# WorkManager rules
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker

# Security crypto rules
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
