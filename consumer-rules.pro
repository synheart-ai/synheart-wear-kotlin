# Consumer ProGuard rules for library users
# These rules are applied to consumers of this library

# Keep public API classes
-keep class ai.synheart.wear.SynheartWear { *; }
-keep class ai.synheart.wear.SynheartWearException { *; }
-keep class ai.synheart.wear.config.SynheartWearConfig { *; }
-keep class ai.synheart.wear.models.** { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class ai.synheart.wear.models.** {
    *** Companion;
}

# Keep enums
-keepclassmembers enum ai.synheart.wear.models.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

