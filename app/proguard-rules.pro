# Room: entity fields are accessed from generated code (consumer rules cover
# most; keep enums stable for TypeConverters).
-keepclassmembers enum com.chantingscheduler.app.data.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Commons-suncalc references FindBugs annotations at compile time only.
-dontwarn edu.umd.cs.findbugs.annotations.**

# Commons-suncalc reflects over its parameter builders.
-keep class org.shredzone.commons.suncalc.** { *; }

# Keep the BackupCodec surface (JSON field names are already string literals).
-keep class com.chantingscheduler.app.data.BackupCodec { *; }

# Media3 session callbacks cross module boundaries via reflection-free AIDL,
# but its platform connector resolves methods by name on API 33+.
-keepclassmembers class * implements androidx.media3.session.MediaLibraryService$MediaLibrarySessionCallback { *; }
