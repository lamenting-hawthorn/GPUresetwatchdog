# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers and source file names for crash reporting.
# Critical for diagnosing issues from minified release builds.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, hide the original source
# file name for additional obfuscation.
-renamesourcefileattribute SourceFile

# Keep custom exception classes (if any)
-keep public class * extends java.lang.Exception

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}