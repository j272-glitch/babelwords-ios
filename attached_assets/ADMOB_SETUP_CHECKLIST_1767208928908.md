# AdMob Android Setup Checklist

## Overview
This checklist guides you through setting up AdMob ads in the LinguaVibe Android app. The web app is fully configured and ready - all remaining steps are Android-side.

---

## Web App Status: ✅ COMPLETE

| Component | Status | Details |
|-----------|--------|---------|
| Publisher ID in ads.txt | ✅ | `pub-9277938970928959` |
| Meta tag verification | ✅ | `ca-pub-9277938970928959` |
| Ad unit IDs documented | ✅ | See table below |
| JavaScript bridge interface | ✅ | `window.adBridge` ready |
| Ad service with callbacks | ✅ | `ad-service.ts` complete |
| Android bridge wrapper | ✅ | `androidBridge.js` complete |
| Server ad proxy (fallback) | ✅ | VAST proxy for web-only mode |
| Ad diagnostics API | ✅ | `/api/ads/diagnostics/*` |

---

## Your AdMob Account Details

| Item | Value |
|------|-------|
| **App ID** | `ca-app-pub-9277938970928959~7782802034` |
| **Interstitial Unit** | `ca-app-pub-9277938970928959/1473642031` |
| **Rewarded Unit** | `ca-app-pub-9277938970928959/8777416980` |
| **Rewarded Interstitial** | `ca-app-pub-9277938970928959/6843749135` |
| **Publisher ID** | `pub-9277938970928959` |

---

## Android Setup Steps

### Phase 1: Project Configuration

- [ ] **1.1 Open Android Project**
  - Open your Android project in Android Studio
  - Package name should be: `com.lingualink.linguagt`

- [ ] **1.2 Add Google Mobile Ads SDK**
  - Open `app/build.gradle`
  - Add dependency:
    ```gradle
    dependencies {
        implementation 'com.google.android.gms:play-services-ads:22.6.0'
    }
    ```

- [ ] **1.3 Add App ID to AndroidManifest.xml**
  - Inside `<application>` tag, add:
    ```xml
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-9277938970928959~7782802034"/>
    ```

- [ ] **1.4 Add Required Permissions**
  - In `AndroidManifest.xml`:
    ```xml
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    ```

- [ ] **1.5 Sync Gradle**
  - Click "Sync Now" in Android Studio

---

### Phase 2: Create AdBridge.kt

- [ ] **2.1 Create AdBridge File**
  - Create file: `app/src/main/java/com/lingualink/linguagt/AdBridge.kt`
  - Copy the complete AdBridge code from `docs/ADMOB_ANDROID_INTEGRATION.md`

- [ ] **2.2 Key AdBridge Methods**
  The AdBridge must expose these methods to JavaScript:
  ```kotlin
  @JavascriptInterface
  fun isAdMobAvailable(): Boolean
  
  @JavascriptInterface
  fun showInterstitial(placement: String)
  
  @JavascriptInterface
  fun showRewarded(placement: String)
  
  @JavascriptInterface
  fun showRewardedInterstitial(placement: String)
  ```

- [ ] **2.3 Implement Callbacks to JavaScript**
  AdBridge must call back to web app:
  ```kotlin
  webView.evaluateJavascript("window.onRewardEarned(30)", null)
  webView.evaluateJavascript("window.onAdBridgeEvent('complete')", null)
  ```

---

### Phase 3: Wire WebView to AdBridge

- [ ] **3.1 Update MainActivity.kt**
  ```kotlin
  import com.google.android.gms.ads.MobileAds
  
  class MainActivity : AppCompatActivity() {
      private lateinit var webView: WebView
      private lateinit var adBridge: AdBridge
      
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          
          // Initialize Mobile Ads SDK
          MobileAds.initialize(this) { initStatus ->
              Log.d("AdMob", "MobileAds initialized: $initStatus")
          }
          
          // Setup WebView
          webView = findViewById(R.id.webView)
          
          // Create and inject AdBridge
          adBridge = AdBridge(this, webView)
          webView.addJavascriptInterface(adBridge, "adBridge")
          
          // Load web app
          webView.loadUrl("https://your-app-url.replit.app")
      }
  }
  ```

- [ ] **3.2 Configure WebView Settings**
  ```kotlin
  webView.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      mediaPlaybackRequiresUserGesture = false
      allowFileAccess = true
  }
  ```

---

### Phase 4: Privacy & Consent (REQUIRED)

- [ ] **4.1 Add User Messaging Platform (UMP) SDK**
  ```gradle
  implementation 'com.google.android.ump:user-messaging-platform:2.1.0'
  ```

- [ ] **4.2 Implement Consent Flow**
  - Required for GDPR compliance
  - Must show before loading ads
  - See Google's UMP documentation

- [ ] **4.3 Update Privacy Policy**
  - Include AdMob in your privacy policy
  - Link to Google's ad privacy policies

---

### Phase 5: Testing

- [ ] **5.1 Use Test Ad Unit IDs First**
  During development, use Google's test IDs:
  ```kotlin
  // TEST IDs (use during development)
  const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
  const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
  const val TEST_REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379"
  ```

- [ ] **5.2 Verify window.adBridge in WebView**
  - Open Chrome DevTools remote debugging
  - Check console: `typeof window.adBridge` should be `"object"`

- [ ] **5.3 Test Interstitial Ad**
  - Trigger from app: should show fullscreen ad
  - Verify callback fires on close

- [ ] **5.4 Test Rewarded Ad**
  - Trigger from app: should show video ad
  - Verify `onRewardEarned` callback fires with 30 minutes

- [ ] **5.5 Switch to Production IDs**
  - Only after test ads work
  - Replace test IDs with your production IDs

---

### Phase 6: ProGuard Configuration

- [ ] **6.1 Add ProGuard Rules**
  In `proguard-rules.pro`:
  ```proguard
  # Keep JavaScript interface methods
  -keepclassmembers class com.lingualink.linguagt.AdBridge {
      @android.webkit.JavascriptInterface <methods>;
  }
  
  # Keep AdMob classes
  -keep class com.google.android.gms.ads.** { *; }
  ```

---

### Phase 7: Play Console & AdMob Console

- [ ] **7.1 Link App in AdMob Console**
  - Go to AdMob > Apps
  - Add your app or link existing
  - Verify app store listing

- [ ] **7.2 Verify app-ads.txt**
  - In AdMob Console, verify your app-ads.txt
  - Must be accessible at: `https://linguagt.com/ads.txt`
  - Contains: `google.com, pub-9277938970928959, DIRECT, f08c47fec0942fa0`

- [ ] **7.3 Play Console App Signing**
  - Configure release signing
  - Upload AAB to Play Console

- [ ] **7.4 Content Rating**
  - Complete content rating questionnaire
  - Include ads disclosure

---

### Phase 8: Production Release

- [ ] **8.1 Final Code Review**
  - Remove all test ad unit IDs
  - Verify production IDs are correct
  - Check all error handling

- [ ] **8.2 Build Release APK/AAB**
  ```bash
  ./gradlew bundleRelease
  ```

- [ ] **8.3 Test on Physical Device**
  - Install release build
  - Verify ads load correctly
  - Check ad frequency capping works

- [ ] **8.4 Submit to Play Store**
  - Upload AAB
  - Add ads disclosure in store listing
  - Submit for review

---

## Troubleshooting

### Ads Not Showing
1. Check AdMob console for app approval status
2. Verify app-ads.txt is accessible
3. Ensure MobileAds.initialize() is called
4. Check for network connectivity

### window.adBridge is undefined
1. Verify `addJavascriptInterface` is called before page load
2. Check package name matches ProGuard keep rules
3. Ensure WebView JavaScript is enabled

### Reward Not Granted
1. Check `onRewardEarned` callback implementation
2. Verify webView.evaluateJavascript is called on UI thread
3. Check for JavaScript errors in console

### Crash on Ad Load
1. Initialize MobileAds on main thread
2. Don't load ads in background threads
3. Check for activity lifecycle issues

---

## Files Reference

| File | Purpose |
|------|---------|
| `docs/ADMOB_ANDROID_INTEGRATION.md` | Full Kotlin implementation |
| `docs/ANDROID_CRASH_PREVENTION_GUIDE.md` | 44 crash prevention safeguards |
| `docs/ANDROID_ANR_PREVENTION_GUIDE.md` | ANR prevention patterns |
| `client/src/utils/androidBridge.js` | JavaScript bridge wrapper |
| `client/src/services/ad-service.ts` | Ad service implementation |
| `client/public/ads.txt` | App-ads.txt for verification |

---

## Summary

**Web App**: 100% complete, no changes needed
**Android App**: Follow phases 1-8 above

Estimated time: 2-4 hours for experienced Android developer

Questions? Check the detailed implementation in `docs/ADMOB_ANDROID_INTEGRATION.md`
