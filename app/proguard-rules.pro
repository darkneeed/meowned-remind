# Proguard rules for meowned remind
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
