# Add project specific ProGuard rules here.
# Native OCR libraries use JNI class/field lookups. Keep their Java boundary intact;
# R8 remains free to remove unused app, UI and network implementation code.
-keep class ai.onnxruntime.** { *; }
-keep class org.opencv.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepattributes SourceFile,LineNumberTable
# Ktor's desktop debugger probe catches Throwable and returns false on Android.
# These two optional JVM management types do not exist in the Android platform.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
