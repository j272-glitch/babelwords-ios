# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ========================================
# WebView JavaScript Interface
# ========================================
# Keep JavaScript interface for IMA/ads bridge
-keepclassmembers class com.lingualink.translator.ads.WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.lingualink.translator.ads.WebAppInterface { *; }

# Keep WebAppBridge JavaScript interface methods
-keepclassmembers class com.lingualink.linguagt.WebAppBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.lingualink.linguagt.WebAppBridge { *; }

# Keep AdBridge JavaScript interface methods for AdMob integration
-keepclassmembers class com.lingualink.linguagt.ads.AdBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.lingualink.linguagt.ads.AdBridge { *; }

# Keep AdMobBridge JavaScript interface methods
-keepclassmembers class com.lingualink.linguagt.ads.AdMobBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.lingualink.linguagt.ads.AdMobBridge { *; }

# Picasso (for image loading)
-keep class com.squareup.picasso.** { *; }
-dontwarn com.squareup.picasso.**

# Keep all JavaScript interface methods
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ========================================
# Google IMA SDK (Interactive Media Ads - Video Ads)
# ========================================
# Keep IMA SDK classes
-keep class com.google.ads.interactivemedia.v3.** { *; }
-keep interface com.google.ads.interactivemedia.v3.** { *; }
-dontwarn com.google.ads.interactivemedia.v3.**

# Keep IMA SDK internal classes
-keep class com.google.ads.interactivemedia.** { *; }
-dontwarn com.google.ads.interactivemedia.**

# ========================================
# Google AdMob (Legacy - kept for compatibility)
# ========================================
# Keep AdMob SDK classes
-keep public class com.google.android.gms.ads.** {
   public *;
}

-keep public class com.google.ads.** {
   public *;
}

-keep class com.google.android.gms.ads.** { *; }

-dontwarn com.google.android.gms.**

# Keep AdMob mediation classes
-keep class com.google.android.gms.ads.mediation.** { *; }

# Keep mediation adapters (alternate package path)
-keep class com.google.ads.mediation.** { *; }

# Keep AdMob adapter classes (for mediation networks)
-keep class com.google.android.gms.ads.adapter.** { *; }

# ========================================
# Google User Messaging Platform (UMP) - GDPR Consent
# ========================================
# Keep UMP SDK classes for consent management
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# Keep consent information
-keep class com.google.android.ump.ConsentInformation { *; }
-keep class com.google.android.ump.ConsentRequestParameters { *; }
-keep class com.google.android.ump.FormError { *; }
-keep class com.google.android.ump.ConsentForm { *; }

# ========================================
# Google Play Services (Required by AdMob)
# ========================================
-keep public class com.google.android.gms.** { *; }

# Keep Google Play Services common classes
-keep class com.google.android.gms.common.** { *; }

# Keep Ads Identifier (for ad targeting)
-keep class com.google.android.gms.ads.identifier.** { *; }

# Keep Play Services base
-keep class com.google.android.gms.base.** { *; }

# Keep Play Services tasks
-keep class com.google.android.gms.tasks.** { *; }

# ========================================
# Meta Audience Network (Facebook) - Optional Mediation
# ========================================
-keep class com.facebook.ads.** { *; }
-keep interface com.facebook.ads.** { *; }
-dontwarn com.facebook.ads.**

-keep class com.facebook.internal.** { *; }
-dontwarn com.facebook.internal.**

# Facebook SDK
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.facebook.** { *; }

# ========================================
# Unity Ads (Optional - Uncomment when approved for mediation)
# ========================================
# -keep class com.unity3d.ads.** { *; }
# -keep class com.unity3d.services.** { *; }
# -dontwarn com.unity3d.ads.**
# -dontwarn com.unity3d.services.**

# ========================================
# AppLovin (Optional - Uncomment if adding for mediation)
# ========================================
# -keep class com.applovin.** { *; }
# -keep class com.applovin.mediation.** { *; }
# -dontwarn com.applovin.**

# ========================================
# Vungle (Optional - Uncomment if adding for mediation)
# ========================================
# -keep class com.vungle.** { *; }
# -dontwarn com.vungle.**

# ========================================
# TesterMobLib (Testing Framework)
# ========================================
-keep class com.testermoblib.** { *; }
-dontwarn com.testermoblib.**

# ========================================
# Conversation Mode - WebView & WebSocket
# ========================================
# Keep WebView classes
-keep class android.webkit.** { *; }
-keepclassmembers class android.webkit.** { *; }

# WebSocket
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

# OkHttp (HTTP client)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

-keep class okio.** { *; }
-dontwarn okio.**

# Retrofit (REST client)
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# ========================================
# Gson (JSON parsing)
# ========================================
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Keep generic signature of Gson
-keepattributes Signature

# Keep Gson annotations
-keepattributes *Annotation*

# Keep data classes for Gson
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent stripping of generic type info
-keepattributes EnclosingMethod

# Keep fields of data classes
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ========================================
# Kotlin
# ========================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

-keepclassmembers class **$WhenMappings {
    <fields>;
}

-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin Reflect
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# ========================================
# AndroidX
# ========================================
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# AndroidX Core
-keep class androidx.core.app.CoreComponentFactory { *; }

# AndroidX Lifecycle
-keep class androidx.lifecycle.** { *; }

# AndroidX AppCompat
-keep class androidx.appcompat.** { *; }

# ========================================
# Native Methods
# ========================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ========================================
# Enums
# ========================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========================================
# Parcelable
# ========================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# ========================================
# Serializable
# ========================================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========================================
# Keep Annotations
# ========================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ========================================
# R8 Full Mode Compatibility
# ========================================
-keep class **.R
-keep class **.R$* { *; }

# Keep resource references
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ========================================
# Debugging (Stack traces for crash reports)
# ========================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep line numbers for better crash reports
-keep,allowshrinking,allowobfuscation class * {
    <methods>;
}

# ========================================
# Suppress Warnings
# ========================================
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.Unsafe
-dontwarn com.google.errorprone.annotations.**

# ========================================
# App-Specific Classes
# ========================================
# Keep your main application class
-keep class com.lingualink.translator.LinguaLinkApplication { *; }
-keep class com.lingualink.linguagt.LinguaLinkApplication { *; }

# Keep your activities
-keep class com.lingualink.translator.MainActivity { *; }
-keep class com.lingualink.translator.BaseActivity { *; }
-keep class com.lingualink.linguagt.MainActivity { *; }
-keep class com.lingualink.linguagt.BaseActivity { *; }

# Keep all linguagt package classes
-keep class com.lingualink.linguagt.** { *; }
-keepclassmembers class com.lingualink.linguagt.** { *; }

# Keep your services
-keep class com.lingualink.translator.TranslationService { *; }
-keep class com.lingualink.linguagt.TranslationService { *; }

# Keep your ad manager classes (IMPORTANT for AdMob)
-keep class com.lingualink.translator.ads.** { *; }

# Keep AdManager specifically
-keep class com.lingualink.translator.ads.AdManager {
    public *;
    public <methods>;
}

# Keep callback interfaces and lambda functions
-keepclassmembers class com.lingualink.translator.ads.AdManager {
    public void set*Callback(...);
    private ** *Callback;
}

# ========================================
# Media / Audio (for conversation mode)
# ========================================
-keep class androidx.media.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media.**
-dontwarn androidx.media3.**

# ExoPlayer (if using for audio playback)
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# ========================================
# Security
# ========================================
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Keep encryption key classes
-keepclassmembers class androidx.security.crypto.** { *; }

# ========================================
# Permissions
# ========================================
-keep class pub.devrel.easypermissions.** { *; }
-dontwarn pub.devrel.easypermissions.**

# ========================================
# Network Security
# ========================================
# Keep SSL/TLS classes
-keep class javax.net.ssl.** { *; }
-keep class org.apache.http.** { *; }
-dontwarn org.apache.http.**

# ========================================
# Data Models (Add your models here)
# ========================================
# Example: Keep your translation data models
# -keep class com.lingualink.translator.models.** { *; }

# Keep data classes that might be used with Gson/JSON
-keep class com.lingualink.translator.models.TranslationResult { *; }
-keep class com.lingualink.translator.models.Language { *; }

# ========================================
# View Binding / Data Binding
# ========================================
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * bind(android.view.View);
}

# ========================================
# Optimization Settings
# ========================================
# Balanced optimization (not too aggressive)
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Preserve some attributes for reflection
-keepattributes Signature,InnerClasses,EnclosingMethod

# ========================================
# Crash Reporting / Analytics
# ========================================
# If using Firebase Crashlytics
# -keep class com.google.firebase.crashlytics.** { *; }
# -keepattributes SourceFile,LineNumberTable

# If using Firebase Analytics
# -keep class com.google.firebase.analytics.** { *; }

# ========================================
# Custom Rules for IMA SDK Integration
# ========================================
# Ensure ad callbacks work properly
-keepclassmembers class * {
    void on*Ad*(***);
}

# Keep IMA SDK listener interfaces
-keep interface com.google.ads.interactivemedia.v3.api.** { *; }

# Keep IMA SDK player classes
-keep class com.google.ads.interactivemedia.v3.api.player.** { *; }

# Keep IMA SDK ad event listeners
-keep class com.google.ads.interactivemedia.v3.api.AdEvent { *; }
-keep class com.google.ads.interactivemedia.v3.api.AdEvent$AdEventListener { *; }
-keep class com.google.ads.interactivemedia.v3.api.AdErrorEvent { *; }
-keep class com.google.ads.interactivemedia.v3.api.AdErrorEvent$AdErrorListener { *; }

# Keep ad listener interfaces (legacy AdMob)
-keep interface com.google.android.gms.ads.** { *; }

# Keep FullScreenContentCallback (legacy)
-keep class com.google.android.gms.ads.FullScreenContentCallback { *; }

# Keep AdLoadCallback classes (legacy)
-keep class com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback { *; }
-keep class com.google.android.gms.ads.rewarded.RewardedAdLoadCallback { *; }

# Keep OnUserEarnedRewardListener (legacy)
-keep class com.google.android.gms.ads.OnUserEarnedRewardListener { *; }
-keep class com.google.android.gms.ads.rewarded.RewardItem { *; }

# Keep IMAManager class
-keep class com.lingualink.linguagt.ads.IMAManager { *; }
-keepclassmembers class com.lingualink.linguagt.ads.IMAManager { *; }

# ========================================
# AdMob Best Practices (from ANDROID_ADMOB_220 Guide)
# ========================================
# Keep AdMob callback classes (ANDROID_PROGUARD_004)
-keep class com.google.android.gms.ads.FullScreenContentCallback { *; }
-keep class com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback { *; }
-keep class com.google.android.gms.ads.rewarded.RewardedAdLoadCallback { *; }
-keep class com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback { *; }
-keep class com.google.android.gms.ads.OnUserEarnedRewardListener { *; }
-keep class com.google.android.gms.ads.rewarded.RewardItem { *; }
-keep class com.google.android.gms.ads.AdError { *; }
-keep class com.google.android.gms.ads.LoadAdError { *; }

# Keep Google Play Services connection classes
-keep class com.google.android.gms.common.ConnectionResult { *; }
-keep class com.google.android.gms.common.GoogleApiAvailability { *; }

# Keep BuildConfig for debug/release differentiation (ANDROID_PROGUARD_015)
-keepclassmembers class **.BuildConfig {
    public static <fields>;
}

# ========================================
# End of ProGuard Rules
# ========================================
