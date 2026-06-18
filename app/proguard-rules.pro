# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Repos\splitandmerge/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and analysis/customization in this file.

# Hilt rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging**

# FFmpegKit (com.antonkarpenko:ffmpeg-kit-min, fork retains the
# com.arthenica package). Native libs register methods against
# these classes via JNI_OnLoad; R8 must NOT rename them.
-keep class com.arthenica.** { *; }
-keep class com.antonkarpenko.** { *; }
-dontwarn com.arthenica.**
-dontwarn com.antonkarpenko.**

# Generic safety net: any class with native methods must keep
# both the class name and the native method names so JNI
# bindings work after R8 minification.
-keepclasseswithmembernames class * {
    native <methods>;
}
