# R8 rules for the release build (on top of proguard-android-optimize.txt and the rules the
# AndroidX/Material libraries ship themselves). Manifest components (KeyboardService, activities,
# ApiReceiver), the settings activity named in res/xml/method.xml and everything referenced from
# resources are kept by the build automatically.

# Readable stack traces: keep file and line numbers, hide the real file name behind "SourceFile".
# Crash reports are translated back with the mapping file that every release contains.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Configuration is parsed with org.json by hand and enums are looked up by their own key fields:
# nothing in the app relies on class, method or field names at run time, so no further keep rules.

# Free offline dictation (issue #24): Vosk binds its native library through JNA. JNA finds the
# native methods of org.vosk.LibVosk by name (Native.register) and reads fields and structures by
# reflection, and its own native part calls back into com.sun.jna classes by name. Neither may be
# renamed or removed.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
# JNA refers to desktop Java (AWT) that Android does not have; those paths are never used here.
-dontwarn java.awt.**
# Google ML Kit (text recognition) creates its components by reflection from the manifest
# metadata ("ComponentDiscovery"). R8 full mode removes the unused no-arg constructors, and in a
# release build OCR then fails at start-up (NoSuchMethodException ...Registrar.<init>, seen on a
# Zebra HC20 with the R8 build). Keep them; the class names may stay obfuscated.
-keep,allowobfuscation class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keepclassmembers class com.google.mlkit.**.*Registrar { <init>(); }
