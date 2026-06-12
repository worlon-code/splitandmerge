# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Repos\splitandmerge/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and analysis/customization in this file.

# Hilt rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging**
