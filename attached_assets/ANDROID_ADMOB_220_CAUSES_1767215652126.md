
# Android AdMob - 220 Causes & Solutions for Ad Display Failures

## Quick Reference
This document covers Android native app issues. For web app issues, see `AD_DISPLAY_TROUBLESHOOTING_COMPLETE.md`.

## Categories Overview
1. **SDK & Initialization (30)** - AdMob SDK setup and initialization
2. **Manifest & Permissions (25)** - AndroidManifest.xml configuration
3. **Ad Unit Configuration (20)** - Ad unit IDs and settings
4. **Network & Connectivity (20)** - Network issues specific to Android
5. **Activity Lifecycle (20)** - Activity and fragment lifecycle issues
6. **Memory & Resources (15)** - Memory leaks and resource management
7. **Threading & Concurrency (15)** - Thread safety and race conditions
8. **WebView Integration (15)** - Issues with WebView-based ads
9. **ProGuard & Obfuscation (15)** - Code obfuscation issues
10. **Device & OS Compatibility (15)** - Device-specific issues
11. **Consent & GDPR (10)** - Consent management on Android
12. **App Signing & Publishing (10)** - Release build issues
13. **Ad Loading & Callbacks (10)** - Ad load failures

---

## CATEGORY 1: SDK & INITIALIZATION (30)

### ANDROID_SDK_001: MobileAds not initialized
**Cause:** `MobileAds.initialize()` never called  
**Solution:**
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) { initStatus ->
            Log.d("AdMob", "Initialized: ${initStatus.adapterStatusMap}")
        }
    }
}
```

### ANDROID_SDK_002: Initialization on background thread
**Cause:** MobileAds.initialize() called from non-main thread  
**Solution:**
```kotlin
Handler(Looper.getMainLooper()).post {
    MobileAds.initialize(applicationContext) { }
}
```

### ANDROID_SDK_003: Google Play Services missing
**Cause:** Device doesn't have Google Play Services  
**Solution:**
```kotlin
fun isPlayServicesAvailable(context: Context): Boolean {
    val resultCode = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context)
    return resultCode == ConnectionResult.SUCCESS
}
```

### ANDROID_SDK_004: Google Play Services outdated
**Cause:** Old version of Play Services installed  
**Solution:**
```kotlin
// Prompt user to update
GoogleApiAvailability.getInstance()
    .makeGooglePlayServicesAvailable(activity)
```

### ANDROID_SDK_005: Wrong Play Services Ads dependency version
**Cause:** Incompatible version in build.gradle  
**Solution:**
```gradle
dependencies {
    implementation 'com.google.android.gms:play-services-ads:22.6.0'
}
```

### ANDROID_SDK_006: Duplicate SDK versions
**Cause:** Multiple conflicting versions in dependencies  
**Solution:**
```gradle
configurations.all {
    resolutionStrategy {
        force 'com.google.android.gms:play-services-ads:22.6.0'
    }
}
```

### ANDROID_SDK_007: SDK initialization before Application.onCreate()
**Cause:** Too early initialization  
**Solution:** Move initialization to Application subclass onCreate()

### ANDROID_SDK_008: Multiple MobileAds.initialize() calls
**Cause:** Duplicate initialization  
**Solution:**
```kotlin
companion object {
    private var isInitialized = false
}

fun initializeOnce() {
    if (!isInitialized) {
        MobileAds.initialize(context) { }
        isInitialized = true
    }
}
```

### ANDROID_SDK_009: Missing multidex support
**Cause:** DEX limit exceeded without multidex  
**Solution:**
```gradle
android {
    defaultConfig {
        multiDexEnabled true
    }
}
dependencies {
    implementation 'androidx.multidex:multidex:2.0.1'
}
```

### ANDROID_SDK_010: Initialization timeout
**Cause:** SDK taking too long to initialize  
**Solution:**
```kotlin
MobileAds.initialize(this) { status ->
    // Don't wait forever - use coroutine timeout
    withTimeout(10000) {
        // Load ads
    }
}
```

### ANDROID_SDK_011: SDK initialized after ad request
**Cause:** Requesting ads before SDK ready  
**Solution:**
```kotlin
private var adsInitialized = false

MobileAds.initialize(this) {
    adsInitialized = true
    loadAds()
}
```

### ANDROID_SDK_012: Missing Google Repository
**Cause:** Google Maven repository not configured  
**Solution:**
```gradle
repositories {
    google()
    mavenCentral()
}
```

### ANDROID_SDK_013: BuildConfig.DEBUG misconfiguration
**Cause:** Using test ads in production or vice versa  
**Solution:**
```kotlin
val adUnitId = if (BuildConfig.DEBUG) {
    "ca-app-pub-3940256099942544/1033173712" // Test
} else {
    "ca-app-pub-9277938970928959/1473642031" // Production
}
```

### ANDROID_SDK_014: AdMob App ID missing from manifest
**Cause:** Missing meta-data in AndroidManifest.xml  
**Solution:**
```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-9277938970928959~7782802034"/>
```

### ANDROID_SDK_015: Wrong AdMob App ID format
**Cause:** Invalid app ID string  
**Solution:** Verify format: `ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX`

### ANDROID_SDK_016: Test device not registered
**Cause:** Real ads shown during development  
**Solution:**
```kotlin
MobileAds.setRequestConfiguration(
    RequestConfiguration.Builder()
        .setTestDeviceIds(listOf("YOUR_DEVICE_ID"))
        .build()
)
```

### ANDROID_SDK_017: AdMob SDK cache corrupted
**Cause:** Corrupted cache files  
**Solution:**
```kotlin
context.cacheDir.deleteRecursively()
```

### ANDROID_SDK_018: Mediation adapters conflict
**Cause:** Conflicting ad network adapters  
**Solution:** Remove unused mediation adapters from dependencies

### ANDROID_SDK_019: Initialization callback not handled
**Cause:** Not waiting for initialization callback  
**Solution:**
```kotlin
MobileAds.initialize(this) { initStatus ->
    val statusMap = initStatus.adapterStatusMap
    for (adapterClass in statusMap.keys) {
        val status = statusMap[adapterClass]
        Log.d("AdMob", "Adapter: $adapterClass Status: ${status?.initializationState}")
    }
}
```

### ANDROID_SDK_020: SDK version too old
**Cause:** Using deprecated SDK version  
**Solution:** Update to latest: `implementation 'com.google.android.gms:play-services-ads:22.6.0'`

### ANDROID_SDK_021: Missing GMS core dependency
**Cause:** play-services-base not included  
**Solution:**
```gradle
implementation 'com.google.android.gms:play-services-base:18.3.0'
```

### ANDROID_SDK_022: Initialization in ContentProvider
**Cause:** Initializing in ContentProvider before Application  
**Solution:** Use `ProcessLifecycleOwner` or Application subclass

### ANDROID_SDK_023: Wrong applicationId in build.gradle
**Cause:** Package name mismatch with AdMob console  
**Solution:** Ensure `applicationId` matches registered package

### ANDROID_SDK_024: SDK initialization exception swallowed
**Cause:** Try-catch hiding errors  
**Solution:**
```kotlin
try {
    MobileAds.initialize(this) { }
} catch (e: Exception) {
    Log.e("AdMob", "Init failed", e)
    // Report to analytics
}
```

### ANDROID_SDK_025: AdInspector blocking production
**Cause:** Ad Inspector left enabled  
**Solution:**
```kotlin
if (BuildConfig.DEBUG) {
    MobileAds.openAdInspector(context) { error ->
        // Only in debug
    }
}
```

### ANDROID_SDK_026: Volley conflict
**Cause:** Conflicting Volley versions  
**Solution:**
```gradle
implementation('com.google.android.gms:play-services-ads:22.6.0') {
    exclude group: 'com.android.volley'
}
```

### ANDROID_SDK_027: SDK not initialized in test environment
**Cause:** Unit tests fail without SDK  
**Solution:**
```kotlin
@Before
fun setUp() {
    MobileAds.initialize(ApplicationProvider.getApplicationContext())
}
```

### ANDROID_SDK_028: Initialization during low memory
**Cause:** System killing init process  
**Solution:** Defer initialization until memory available

### ANDROID_SDK_029: Custom Application class not registered
**Cause:** Application subclass not in manifest  
**Solution:**
```xml
<application
    android:name=".MyApplication"
    ...>
```

### ANDROID_SDK_030: Firebase conflict
**Cause:** Firebase and AdMob version mismatch  
**Solution:**
```gradle
implementation platform('com.google.firebase:firebase-bom:32.7.0')
implementation 'com.google.android.gms:play-services-ads:22.6.0'
```

---

## CATEGORY 2: MANIFEST & PERMISSIONS (25)

### ANDROID_MANIFEST_001: INTERNET permission missing
**Cause:** Cannot connect to ad servers  
**Solution:**
```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

### ANDROID_MANIFEST_002: ACCESS_NETWORK_STATE permission missing
**Cause:** Cannot detect network availability  
**Solution:**
```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
```

### ANDROID_MANIFEST_003: AD_ID permission missing (Android 13+)
**Cause:** Cannot access advertising ID on Android 13+  
**Solution:**
```xml
<uses-permission android:name="com.google.android.gms.permission.AD_ID"/>
```

### ANDROID_MANIFEST_004: App ID in wrong location
**Cause:** Meta-data outside <application> tag  
**Solution:** Move meta-data inside `<application>` element

### ANDROID_MANIFEST_005: Duplicate App ID declarations
**Cause:** Multiple meta-data entries for same key  
**Solution:** Keep only one APP_ID meta-data

### ANDROID_MANIFEST_006: Hardware acceleration disabled
**Cause:** android:hardwareAccelerated="false"  
**Solution:**
```xml
<application
    android:hardwareAccelerated="true"
    ...>
```

### ANDROID_MANIFEST_007: Network security config blocking ads
**Cause:** Restrictive network_security_config.xml  
**Solution:**
```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">googlesyndication.com</domain>
        <domain includeSubdomains="true">doubleclick.net</domain>
    </domain-config>
</network-security-config>
```

### ANDROID_MANIFEST_008: clearTextTraffic disabled globally
**Cause:** android:usesCleartextTraffic="false"  
**Solution:** Allow for ad domains in network security config

### ANDROID_MANIFEST_009: targetSdkVersion too old
**Cause:** Targeting SDK < 19  
**Solution:**
```gradle
android {
    defaultConfig {
        targetSdkVersion 34
    }
}
```

### ANDROID_MANIFEST_010: minSdkVersion too low
**Cause:** SDK < 21 (AdMob requires 21+)  
**Solution:**
```gradle
android {
    defaultConfig {
        minSdkVersion 21
    }
}
```

### ANDROID_MANIFEST_011: Exported activities not properly declared
**Cause:** Missing android:exported for SDK 31+  
**Solution:**
```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
```

### ANDROID_MANIFEST_012: Activity theme incompatible
**Cause:** NoActionBar theme breaking ad display  
**Solution:** Use compatible theme or add action bar for ad activities

### ANDROID_MANIFEST_013: ConfigChanges not handled
**Cause:** Activity recreating on rotation  
**Solution:**
```xml
<activity
    android:name=".MainActivity"
    android:configChanges="keyboardHidden|orientation|screenSize">
```

### ANDROID_MANIFEST_014: App name too long
**Cause:** android:label exceeding limit  
**Solution:** Shorten app label or use string resource

### ANDROID_MANIFEST_015: Backup rules excluding ad data
**Cause:** Ad preferences not backed up  
**Solution:**
```xml
<full-backup-content>
    <include domain="sharedpref" path="com.google.android.gms.ads."/>
</full-backup-content>
```

### ANDROID_MANIFEST_016: Intent filters conflict
**Cause:** Multiple MAIN/LAUNCHER activities  
**Solution:** Ensure only one launcher activity

### ANDROID_MANIFEST_017: Permission groups missing
**Cause:** Runtime permissions not requested  
**Solution:**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    requestPermissions(arrayOf(Manifest.permission.AD_ID), 1)
}
```

### ANDROID_MANIFEST_018: Screen orientation locked
**Cause:** android:screenOrientation="portrait" preventing landscape ads  
**Solution:** Use "sensor" or allow both orientations

### ANDROID_MANIFEST_019: LaunchMode conflict
**Cause:** singleTask/singleInstance breaking ad display  
**Solution:** Use standard or singleTop launch mode

### ANDROID_MANIFEST_020: Process attribute set
**Cause:** Ad running in separate process  
**Solution:** Remove android:process attribute

### ANDROID_MANIFEST_021: Debuggable flag in release
**Cause:** android:debuggable="true" in production  
**Solution:**
```gradle
buildTypes {
    release {
        debuggable false
    }
}
```

### ANDROID_MANIFEST_022: allowBackup false preventing ad caching
**Cause:** Ad cache not persisting  
**Solution:** Enable backup or exclude only sensitive data

### ANDROID_MANIFEST_023: resizeableActivity false
**Cause:** Multi-window ads not supported  
**Solution:**
```xml
<activity
    android:resizeableActivity="true">
```

### ANDROID_MANIFEST_024: TaskAffinity breaking ad flow
**Cause:** Custom task affinity  
**Solution:** Remove android:taskAffinity for main activity

### ANDROID_MANIFEST_025: WRITE_EXTERNAL_STORAGE blocking ad cache
**Cause:** Missing permission on older devices  
**Solution:**
```xml
<uses-permission
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28"/>
```

---

## CATEGORY 3: AD UNIT CONFIGURATION (20)

### ANDROID_UNIT_001: Ad unit ID not matching AdMob console
**Cause:** Typo in ad unit ID  
**Solution:** Copy exact ID from AdMob console

### ANDROID_UNIT_002: Using test ID in production
**Cause:** Forgot to replace test ad unit  
**Solution:**
```kotlin
val adUnitId = if (BuildConfig.DEBUG) {
    AD_UNIT_TEST
} else {
    AD_UNIT_PRODUCTION
}
```

### ANDROID_UNIT_003: Ad unit not created in AdMob console
**Cause:** Using ID before creating unit  
**Solution:** Create ad unit in AdMob console first

### ANDROID_UNIT_004: App not linked to ad unit
**Cause:** Ad unit created for different app  
**Solution:** Verify app ID matches in console

### ANDROID_UNIT_005: Ad unit format mismatch
**Cause:** Using banner ID for interstitial  
**Solution:** Use correct unit type for each format

### ANDROID_UNIT_006: Ad unit country restricted
**Cause:** Geo-targeting limiting inventory  
**Solution:** Check mediation settings in console

### ANDROID_UNIT_007: Ad unit disabled in console
**Cause:** Unit turned off  
**Solution:** Enable ad unit in AdMob console

### ANDROID_UNIT_008: Ad unit targeting conflicts
**Cause:** Narrow targeting reducing fill  
**Solution:** Broaden targeting in console settings

### ANDROID_UNIT_009: Floor price too high
**Cause:** Minimum CPM above market rate  
**Solution:** Lower or remove floor price

### ANDROID_UNIT_010: Ad unit quota exceeded
**Cause:** Daily impression limit reached  
**Solution:** Increase quota or wait 24 hours

### ANDROID_UNIT_011: Package name mismatch
**Cause:** App package != registered package  
**Solution:** Match applicationId to AdMob registration

### ANDROID_UNIT_012: Ad unit for wrong platform
**Cause:** Using iOS unit ID on Android  
**Solution:** Create Android-specific ad unit

### ANDROID_UNIT_013: Beta app using production units
**Cause:** Beta package name different  
**Solution:** Create separate units for beta

### ANDROID_UNIT_014: Ad unit pending approval
**Cause:** New unit under review  
**Solution:** Wait for approval (can take hours)

### ANDROID_UNIT_015: Invalid characters in ad unit ID
**Cause:** Extra spaces or characters  
**Solution:** Trim and validate ID string

### ANDROID_UNIT_016: Ad unit archived
**Cause:** Unit deleted/archived in console  
**Solution:** Restore or create new unit

### ANDROID_UNIT_017: Mediation waterfall misconfigured
**Cause:** No active networks in waterfall  
**Solution:** Add and configure mediation partners

### ANDROID_UNIT_018: Ad unit frequency capping
**Cause:** Showing ads too frequently  
**Solution:** Respect frequency cap settings

### ANDROID_UNIT_019: Ad unit size restrictions
**Cause:** Requested size not supported  
**Solution:** Use standard ad sizes (320x50, 300x250)

### ANDROID_UNIT_020: eCPM floor blocking fill
**Cause:** eCPM floor preventing low-value ads  
**Solution:** Reduce eCPM floor

---

## CATEGORY 4: NETWORK & CONNECTIVITY (20)

### ANDROID_NET_001: Device offline
**Cause:** No internet connection  
**Solution:**
```kotlin
fun isNetworkAvailable(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return cm.activeNetworkInfo?.isConnected ?: false
}
```

### ANDROID_NET_002: VPN blocking ad servers
**Cause:** VPN routing blocking ads  
**Solution:** Detect VPN and inform user

### ANDROID_NET_003: DNS failure
**Cause:** Cannot resolve googlesyndication.com  
**Solution:**
```kotlin
try {
    InetAddress.getByName("pagead2.googlesyndication.com")
} catch (e: UnknownHostException) {
    Log.e("Network", "DNS failed")
}
```

### ANDROID_NET_004: Firewall blocking port 443
**Cause:** Corporate/school firewall  
**Solution:** Inform user, no programmatic fix

### ANDROID_NET_005: Proxy server interfering
**Cause:** Proxy stripping headers  
**Solution:** Bypass proxy for ad requests if possible

### ANDROID_NET_006: SSL certificate validation failing
**Cause:** Invalid/expired certificate  
**Solution:**
```kotlin
// Don't disable SSL in production!
if (BuildConfig.DEBUG) {
    // Only for debugging
}
```

### ANDROID_NET_007: MTU size issues
**Cause:** Packet fragmentation  
**Solution:** Network operator issue, no app fix

### ANDROID_NET_008: IPv6 connectivity problems
**Cause:** IPv6-only network without fallback  
**Solution:**
```kotlin
System.setProperty("java.net.preferIPv4Stack", "true")
```

### ANDROID_NET_009: Network switching during ad load
**Cause:** WiFi -> Cellular transition  
**Solution:**
```kotlin
val callback = object : ConnectivityManager.NetworkCallback() {
    override fun onLost(network: Network) {
        // Retry ad request
    }
}
```

### ANDROID_NET_010: Slow network timeout
**Cause:** Ad request timing out  
**Solution:** Increase timeout in RequestConfiguration

### ANDROID_NET_011: Captive portal blocking ads
**Cause:** Hotel/airport WiFi requiring login  
**Solution:**
```kotlin
val cm = getSystemService<ConnectivityManager>()
cm?.activeNetwork?.let { network ->
    val caps = cm.getNetworkCapabilities(network)
    val isCaptive = caps?.hasCapability(NET_CAPABILITY_CAPTIVE_PORTAL) ?: false
}
```

### ANDROID_NET_012: Ad requests throttled by ISP
**Cause:** ISP rate limiting ad traffic  
**Solution:** Implement exponential backoff

### ANDROID_NET_013: HTTP/2 negotiation failure
**Cause:** Protocol negotiation failing  
**Solution:** Use OkHttp with proper HTTP/2 support

### ANDROID_NET_014: TLS version incompatibility
**Cause:** Device TLS version too old  
**Solution:**
```kotlin
if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
    // TLS 1.2 not available
}
```

### ANDROID_NET_015: Network security config blocking HTTPS
**Cause:** Certificate pinning too strict  
**Solution:** Add Google certificates to pins

### ANDROID_NET_016: Data saver mode enabled
**Cause:** Android Data Saver blocking background  
**Solution:**
```kotlin
val cm = getSystemService<ConnectivityManager>()
if (cm?.isActiveNetworkMetered == true) {
    // Warn user about data saver
}
```

### ANDROID_NET_017: Background data restricted
**Cause:** App background data disabled  
**Solution:**
```kotlin
val cm = getSystemService<ConnectivityManager>()
if (cm?.restrictBackgroundStatus == RESTRICT_BACKGROUND_STATUS_ENABLED) {
    // Request user to enable background data
}
```

### ANDROID_NET_018: Airplane mode edge case
**Cause:** WiFi on but airplane mode enabled  
**Solution:** Check both WiFi and airplane mode states

### ANDROID_NET_019: Network interface conflict
**Cause:** Multiple active network interfaces  
**Solution:** Use ConnectivityManager.activeNetwork

### ANDROID_NET_020: Socket timeout too short
**Cause:** Ad server slow to respond  
**Solution:**
```kotlin
val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
```

---

## CATEGORY 5: ACTIVITY LIFECYCLE (20)

### ANDROID_LIFECYCLE_001: Ad loaded before Activity created
**Cause:** Loading ad too early  
**Solution:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    loadAd()
}
```

### ANDROID_LIFECYCLE_002: Showing ad after Activity destroyed
**Cause:** Ad shown after finish()  
**Solution:**
```kotlin
if (!isFinishing && !isDestroyed) {
    interstitialAd?.show(this)
}
```

### ANDROID_LIFECYCLE_003: Configuration change destroying ad
**Cause:** Rotation recreating activity  
**Solution:**
```kotlin
override fun onSaveInstanceState(outState: Bundle) {
    // Save ad state
    super.onSaveInstanceState(outState)
}
```

### ANDROID_LIFECYCLE_004: Ad callbacks after onDestroy
**Cause:** Leaked callbacks  
**Solution:**
```kotlin
override fun onDestroy() {
    interstitialAd?.fullScreenContentCallback = null
    super.onDestroy()
}
```

### ANDROID_LIFECYCLE_005: Fragment lifecycle mismatch
**Cause:** Loading ad in wrong fragment lifecycle  
**Solution:** Use `viewLifecycleOwner` not `lifecycleOwner`

### ANDROID_LIFECYCLE_006: Activity finishing during ad load
**Cause:** User navigating away mid-load  
**Solution:**
```kotlin
override fun onStop() {
    super.onStop()
    adLoadJob?.cancel()
}
```

### ANDROID_LIFECYCLE_007: Multiple ad loads on orientation change
**Cause:** Not preserving ad instance  
**Solution:**
```kotlin
override fun onRetainCustomNonConfigurationInstance(): Any {
    return interstitialAd
}
```

### ANDROID_LIFECYCLE_008: Ad shown in onPause
**Cause:** Showing ad while activity pausing  
**Solution:** Only show in onResume or later

### ANDROID_LIFECYCLE_009: ViewModel not preserving ad
**Cause:** Ad loaded in Activity not ViewModel  
**Solution:**
```kotlin
class AdViewModel : ViewModel() {
    var interstitialAd: InterstitialAd? = null
}
```

### ANDROID_LIFECYCLE_010: Process death not handled
**Cause:** App killed in background  
**Solution:** Reload ad in onCreate if null

### ANDROID_LIFECYCLE_011: Ad loaded in background
**Cause:** Loading while app not visible  
**Solution:**
```kotlin
lifecycle.addObserver(object : LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            loadAd()
        }
    }
})
```

### ANDROID_LIFECYCLE_012: onBackPressed() not handled
**Cause:** Back button during ad display  
**Solution:**
```kotlin
override fun onBackPressed() {
    if (adIsShowing) {
        return
    }
    super.onBackPressed()
}
```

### ANDROID_LIFECYCLE_013: Activity result breaking flow
**Cause:** startActivityForResult interrupting ad  
**Solution:** Queue ad for after result

### ANDROID_LIFECYCLE_014: Multi-window mode breaking ad
**Cause:** Split screen mode  
**Solution:**
```kotlin
override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
    if (!isInMultiWindowMode) {
        // Reload ad
    }
}
```

### ANDROID_LIFECYCLE_015: PiP mode showing ad
**Cause:** Ad displayed in picture-in-picture  
**Solution:**
```kotlin
if (!isInPictureInPictureMode) {
    showAd()
}
```

### ANDROID_LIFECYCLE_016: Service lifecycle confusion
**Cause:** Loading ad in Service instead of Activity  
**Solution:** Ads require Activity context

### ANDROID_LIFECYCLE_017: Application context used
**Cause:** Using applicationContext for ad Activity  
**Solution:** Always use Activity instance

### ANDROID_LIFECYCLE_018: Leaked Activity reference
**Cause:** Static reference to Activity  
**Solution:**
```kotlin
class AdManager {
    private var activityRef: WeakReference<Activity>? = null
    
    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }
}
```

### ANDROID_LIFECYCLE_019: Coroutine scope not cancelled
**Cause:** Coroutine continuing after destroy  
**Solution:**
```kotlin
class MyActivity : AppCompatActivity() {
    private val scope = lifecycleScope
    
    override fun onCreate(savedInstanceState: Bundle?) {
        scope.launch {
            loadAd()
        }
    }
}
```

### ANDROID_LIFECYCLE_020: Handler messages not removed
**Cause:** Handler continuing after destroy  
**Solution:**
```kotlin
override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
}
```

---

## CATEGORY 6: MEMORY & RESOURCES (15)

### ANDROID_MEMORY_001: Out of memory loading ad
**Cause:** Large ad creative  
**Solution:**
```kotlin
override fun onLowMemory() {
    super.onLowMemory()
    interstitialAd = null
}
```

### ANDROID_MEMORY_002: Memory leak from ad callbacks
**Cause:** Inner class holding Activity reference  
**Solution:** Use WeakReference or static class

### ANDROID_MEMORY_003: Too many ad instances
**Cause:** Not destroying old ads  
**Solution:**
```kotlin
private var currentAd: InterstitialAd? = null

fun loadNewAd() {
    currentAd?.fullScreenContentCallback = null
    currentAd = null
    // Load new ad
}
```

### ANDROID_MEMORY_004: Bitmap cache exhausted
**Cause:** Ad images filling cache  
**Solution:** Use Glide/Picasso with cache limits

### ANDROID_MEMORY_005: Large heap not enabled
**Cause:** android:largeHeap not set for heavy apps  
**Solution:**
```xml
<application
    android:largeHeap="true">
```

### ANDROID_MEMORY_006: Memory pressure from other apps
**Cause:** System low on memory  
**Solution:** Implement onTrimMemory callbacks

### ANDROID_MEMORY_007: Ad view not destroyed
**Cause:** AdView not calling destroy()  
**Solution:**
```kotlin
override fun onDestroy() {
    adView?.destroy()
    super.onDestroy()
}
```

### ANDROID_MEMORY_008: Context leak via listener
**Cause:** Listener holding Activity reference  
**Solution:**
```kotlin
interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
    override fun onAdDismissedFullScreenContent() {
        interstitialAd?.fullScreenContentCallback = null
    }
}
```

### ANDROID_MEMORY_009: Native heap exhaustion
**Cause:** Native memory from ad rendering  
**Solution:** Monitor with Debug.getNativeHeapAllocatedSize()

### ANDROID_MEMORY_010: GC pressure from frequent ad loads
**Cause:** Loading ads too frequently  
**Solution:** Implement ad load throttling

### ANDROID_MEMORY_011: AdView not removed from parent
**Cause:** View hierarchy leak  
**Solution:**
```kotlin
(adView?.parent as? ViewGroup)?.removeView(adView)
adView?.destroy()
```

### ANDROID_MEMORY_012: Static reference to ad
**Cause:** companion object holding ad  
**Solution:** Store in ViewModel or Activity field

### ANDROID_MEMORY_013: AsyncTask leak
**Cause:** AsyncTask holding Activity reference  
**Solution:** Use coroutines with lifecycle scope

### ANDROID_MEMORY_014: Handler leak
**Cause:** Handler with Message holding Activity  
**Solution:** Use static Handler with WeakReference

### ANDROID_MEMORY_015: Drawable cache leak
**Cause:** Ad images not recycled  
**Solution:**
```kotlin
imageView.setImageDrawable(null)
```

---

## CATEGORY 7: THREADING & CONCURRENCY (15)

### ANDROID_THREAD_001: Ad loaded on worker thread
**Cause:** InterstitialAd.load() on background thread  
**Solution:**
```kotlin
withContext(Dispatchers.Main) {
    InterstitialAd.load(context, adUnitId, adRequest, callback)
}
```

### ANDROID_THREAD_002: Ad shown from background thread
**Cause:** interstitialAd.show() not on main thread  
**Solution:**
```kotlin
runOnUiThread {
    interstitialAd?.show(this)
}
```

### ANDROID_THREAD_003: Race condition loading multiple ads
**Cause:** Concurrent ad load requests  
**Solution:**
```kotlin
private val adLoadMutex = Mutex()

suspend fun loadAd() {
    adLoadMutex.withLock {
        // Load ad
    }
}
```

### ANDROID_THREAD_004: Callback on wrong thread
**Cause:** FullScreenContentCallback on worker thread  
**Solution:** Callbacks are always on main thread, but verify

### ANDROID_THREAD_005: Synchronization issue with ad state
**Cause:** Ad state accessed from multiple threads  
**Solution:**
```kotlin
@Volatile
private var isAdLoaded = false

@Synchronized
fun setAdLoaded(loaded: Boolean) {
    isAdLoaded = loaded
}
```

### ANDROID_THREAD_006: Executor shutdown during ad load
**Cause:** Executor service terminated  
**Solution:** Use non-shutting down executor

### ANDROID_THREAD_007: Handler thread mismatch
**Cause:** Handler created on wrong thread  
**Solution:**
```kotlin
val handler = Handler(Looper.getMainLooper())
```

### ANDROID_THREAD_008: Coroutine dispatcher wrong
**Cause:** Using Dispatchers.IO for UI operations  
**Solution:**
```kotlin
lifecycleScope.launch(Dispatchers.Main) {
    showAd()
}
```

### ANDROID_THREAD_009: AsyncTask deprecated issues
**Cause:** Using deprecated AsyncTask  
**Solution:** Migrate to coroutines

### ANDROID_THREAD_010: Thread pool exhaustion
**Cause:** Too many concurrent ad requests  
**Solution:** Limit concurrent requests

### ANDROID_THREAD_011: ConcurrentModificationException
**Cause:** Modifying ad list during iteration  
**Solution:** Use synchronized collection

### ANDROID_THREAD_012: Looper.prepare() not called
**Cause:** Creating Handler without Looper  
**Solution:** Ensure Looper exists or use main looper

### ANDROID_THREAD_013: WorkManager job cancellation
**Cause:** Ad load job cancelled by system  
**Solution:** Use foreground service for critical ads

### ANDROID_THREAD_014: RxJava scheduler mismatch
**Cause:** Wrong scheduler for ad operations  
**Solution:**
```kotlin
.observeOn(AndroidSchedulers.mainThread())
```

### ANDROID_THREAD_015: Callback queue overflow
**Cause:** Too many pending callbacks  
**Solution:** Limit callback queue size

---

## CATEGORY 8: WEBVIEW INTEGRATION (15)

### ANDROID_WEBVIEW_001: JavaScript interface not added
**Cause:** WebView.addJavascriptInterface() not called  
**Solution:**
```kotlin
webView.addJavascriptInterface(AdBridge(this, webView), "AdBridge")
```

### ANDROID_WEBVIEW_002: JavaScript disabled
**Cause:** WebSettings.setJavaScriptEnabled(false)  
**Solution:**
```kotlin
webView.settings.javaScriptEnabled = true
```

### ANDROID_WEBVIEW_003: DOM storage disabled
**Cause:** Ad cookies not persisting  
**Solution:**
```kotlin
webView.settings.domStorageEnabled = true
```

### ANDROID_WEBVIEW_004: WebView not initialized on main thread
**Cause:** WebView created on background thread  
**Solution:**
```kotlin
runOnUiThread {
    val webView = WebView(context)
}
```

### ANDROID_WEBVIEW_005: Mixed content blocked
**Cause:** HTTPS page loading HTTP ad  
**Solution:**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    webView.settings.mixedContentMode = MIXED_CONTENT_COMPATIBILITY_MODE
}
```

### ANDROID_WEBVIEW_006: WebView cache disabled
**Cause:** Ad resources not cached  
**Solution:**
```kotlin
webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
```

### ANDROID_WEBVIEW_007: WebChromeClient missing
**Cause:** JavaScript alerts not handled  
**Solution:**
```kotlin
webView.webChromeClient = object : WebChromeClient() {
    // Handle JS dialogs
}
```

### ANDROID_WEBVIEW_008: File access blocked
**Cause:** Ad creative can't load local resources  
**Solution:**
```kotlin
webView.settings.allowFileAccess = true
```

### ANDROID_WEBVIEW_009: User agent string issues
**Cause:** Custom user agent breaking ad detection  
**Solution:**
```kotlin
// Use default user agent or append carefully
val defaultUA = WebSettings.getDefaultUserAgent(context)
```

### ANDROID_WEBVIEW_010: WebView version too old
**Cause:** System WebView outdated  
**Solution:** Prompt user to update Android System WebView

### ANDROID_WEBVIEW_011: evaluateJavascript timing issue
**Cause:** Calling JS before page loaded  
**Solution:**
```kotlin
webView.webViewClient = object : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String) {
        view.evaluateJavascript("window.AdBridge.initialize()", null)
    }
}
```

### ANDROID_WEBVIEW_012: Cookie sync failure
**Cause:** WebView cookies not syncing  
**Solution:**
```kotlin
CookieManager.getInstance().setAcceptCookie(true)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
}
```

### ANDROID_WEBVIEW_013: WebView destroyed during ad display
**Cause:** WebView.destroy() called while ad showing  
**Solution:** Wait for ad to finish before destroying

### ANDROID_WEBVIEW_014: loadUrl() before WebView attached
**Cause:** Loading URL before adding to view hierarchy  
**Solution:**
```kotlin
containerView.addView(webView)
webView.loadUrl("https://...")
```

### ANDROID_WEBVIEW_015: Hardware acceleration disabled for WebView
**Cause:** Layer type set to software  
**Solution:**
```kotlin
webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
```

---

## CATEGORY 9: PROGUARD & OBFUSCATION (15)

### ANDROID_PROGUARD_001: AdMob classes obfuscated
**Cause:** ProGuard removing/renaming ad classes  
**Solution:**
```proguard
-keep class com.google.android.gms.ads.** { *; }
```

### ANDROID_PROGUARD_002: JavaScript interface methods renamed
**Cause:** @JavascriptInterface methods obfuscated  
**Solution:**
```proguard
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
```

### ANDROID_PROGUARD_003: AdBridge class obfuscated
**Cause:** Custom bridge class renamed  
**Solution:**
```proguard
-keep class com.lingualink.linguagt.AdBridge {
    @android.webkit.JavascriptInterface <methods>;
}
```

### ANDROID_PROGUARD_004: Callback classes stripped
**Cause:** Anonymous callback classes removed  
**Solution:**
```proguard
-keep class com.google.android.gms.ads.** {
    public protected *;
}
```

### ANDROID_PROGUARD_005: Reflection breaking ad loading
**Cause:** ProGuard removing classes used via reflection  
**Solution:**
```proguard
-keepclasseswithmembernames class * {
    native <methods>;
}
```

### ANDROID_PROGUARD_006: R8 over-optimization
**Cause:** R8 full mode too aggressive  
**Solution:**
```gradle
android {
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
        }
    }
}
```

### ANDROID_PROGUARD_007: Missing consumer ProGuard rules
**Cause:** Library ProGuard rules not included  
**Solution:** Ensure consumerProguardFiles in library build.gradle

### ANDROID_PROGUARD_008: Enums obfuscated
**Cause:** Ad-related enums renamed  
**Solution:**
```proguard
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
```

### ANDROID_PROGUARD_009: Parcelable implementation broken
**Cause:** CREATOR field removed  
**Solution:**
```proguard
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
```

### ANDROID_PROGUARD_010: Serializable UID missing
**Cause:** serialVersionUID removed  
**Solution:**
```proguard
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
}
```

### ANDROID_PROGUARD_011: Native methods stripped
**Cause:** JNI methods removed  
**Solution:**
```proguard
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
```

### ANDROID_PROGUARD_012: Annotations removed
**Cause:** @Keep annotations ignored  
**Solution:**
```proguard
-keep @androidx.annotation.Keep class *
```

### ANDROID_PROGUARD_013: Data class properties renamed
**Cause:** Kotlin data class fields obfuscated  
**Solution:**
```proguard
-keepclassmembers class * {
    public <init>(...);
}
```

### ANDROID_PROGUARD_014: ViewModel classes stripped
**Cause:** ViewModel subclasses removed  
**Solution:**
```proguard
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
```

### ANDROID_PROGUARD_015: BuildConfig fields optimized out
**Cause:** BuildConfig.DEBUG removed  
**Solution:**
```proguard
-keepclassmembers class **.BuildConfig {
    public static <fields>;
}
```

---

## CATEGORY 10: DEVICE & OS COMPATIBILITY (15)

### ANDROID_DEVICE_001: Emulator without Play Services
**Cause:** AVD doesn't have Google APIs  
**Solution:** Use emulator with Play Store image

### ANDROID_DEVICE_002: Custom ROM blocking ads
**Cause:** LineageOS or similar blocking Google services  
**Solution:** Install microG or full GApps

### ANDROID_DEVICE_003: China-specific firmware
**Cause:** Chinese devices without Google services  
**Solution:** Use alternative ad network for China

### ANDROID_DEVICE_004: Amazon Fire device
**Cause:** Fire OS doesn't support Google Play Services  
**Solution:** Use Amazon Mobile Ads instead

### ANDROID_DEVICE_005: Android Go edition limitations
**Cause:** Go edition memory constraints  
**Solution:** Use lighter ad formats

### ANDROID_DEVICE_006: Screen size too small
**Cause:** Ad doesn't fit on screen  
**Solution:**
```kotlin
val metrics = resources.displayMetrics
if (metrics.widthPixels < 300) {
    // Use smaller ad format
}
```

### ANDROID_DEVICE_007: Foldable device orientation
**Cause:** Fold/unfold changing screen size  
**Solution:** Listen to configuration changes

### ANDROID_DEVICE_008: Notch cutting off ad
**Cause:** Display cutout overlapping ad area  
**Solution:**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    window.attributes.layoutInDisplayCutoutMode = 
        LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}
```

### ANDROID_DEVICE_009: Low-end device performance
**Cause:** Slow CPU/GPU can't render ads  
**Solution:** Detect and use simpler ad formats

### ANDROID_DEVICE_010: Battery optimization killing app
**Cause:** Aggressive battery saver  
**Solution:**
```kotlin
val pm = getSystemService<PowerManager>()
if (!pm?.isIgnoringBatteryOptimizations(packageName)!!) {
    // Request exemption
}
```

### ANDROID_DEVICE_011: Dark mode breaking ad colors
**Cause:** Force dark mode on ads  
**Solution:**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    webView.settings.forceDark = FORCE_DARK_OFF
}
```

### ANDROID_DEVICE_012: Manufacturer skin blocking
**Cause:** Samsung, Xiaomi, etc. ad blocking  
**Solution:** Detect manufacturer and show message

### ANDROID_DEVICE_013: Android version too old
**Cause:** SDK < 21  
**Solution:** Show compatibility message

### ANDROID_DEVICE_014: 64-bit library missing
**Cause:** Missing arm64-v8a library on 64-bit device  
**Solution:**
```gradle
android {
    defaultConfig {
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
        }
    }
}
```

### ANDROID_DEVICE_015: Chromebook compatibility
**Cause:** Chrome OS limitations  
**Solution:** Detect Chrome OS and adjust ad behavior

---

## CATEGORY 11: CONSENT & GDPR (10)

### ANDROID_CONSENT_001: UMP SDK not initialized
**Cause:** User Messaging Platform not set up  
**Solution:**
```kotlin
implementation 'com.google.android.ump:user-messaging-platform:2.1.0'
```

### ANDROID_CONSENT_002: Consent form not shown
**Cause:** GDPR applies but no consent dialog  
**Solution:**
```kotlin
val params = ConsentRequestParameters.Builder()
    .setTagForUnderAgeOfConsent(false)
    .build()

ConsentInformation.getInstance(context).requestConsentInfoUpdate(
    activity, params,
    { /* Load form */ },
    { /* Handle error */ }
)
```

### ANDROID_CONSENT_003: Consent form shown in wrong region
**Cause:** Testing outside EEA  
**Solution:**
```kotlin
val debugSettings = ConsentDebugSettings.Builder(context)
    .setDebugGeography(DebugGeography.DEBUG_GEOGRAPHY_EEA)
    .build()
```

### ANDROID_CONSENT_004: Cached consent expired
**Cause:** Consent > 12 months old  
**Solution:**
```kotlin
ConsentInformation.getInstance(context).reset()
```

### ANDROID_CONSENT_005: Consent not passed to ad request
**Cause:** npa parameter not set  
**Solution:**
```kotlin
val extras = Bundle()
extras.putString("npa", "1")

val adRequest = AdRequest.Builder()
    .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
    .build()
```

### ANDROID_CONSENT_006: TCF string malformed
**Cause:** Invalid consent string  
**Solution:** Regenerate using valid CMP

### ANDROID_CONSENT_007: Consent form UI blocked
**Cause:** Dialog dismissed before action  
**Solution:** Make form non-cancellable

### ANDROID_CONSENT_008: Privacy options not accessible
**Cause:** Missing privacy options link  
**Solution:**
```kotlin
// Add button to re-show consent form
button.setOnClickListener {
    ConsentForm.loadForm(context, { form ->
        form.show(activity) { }
    }, { })
}
```

### ANDROID_CONSENT_009: Child-directed app not flagged
**Cause:** COPPA compliance missing  
**Solution:**
```kotlin
val requestConfiguration = RequestConfiguration.Builder()
    .setTagForChildDirectedTreatment(TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
    .build()

MobileAds.setRequestConfiguration(requestConfiguration)
```

### ANDROID_CONSENT_010: Consent SDK version mismatch
**Cause:** Incompatible UMP and AdMob versions  
**Solution:** Update both to latest compatible versions

---

## CATEGORY 12: APP SIGNING & PUBLISHING (10)

### ANDROID_SIGNING_001: Debug keystore in production
**Cause:** Using debug signature  
**Solution:**
```gradle
signingConfigs {
    release {
        storeFile file('release.keystore')
        storePassword 'xxx'
        keyAlias 'xxx'
        keyPassword 'xxx'
    }
}
```

### ANDROID_SIGNING_002: SHA-1 not registered
**Cause:** App signature not in AdMob console  
**Solution:**
```bash
keytool -list -v -keystore release.keystore
# Add SHA-1 to AdMob console
```

### ANDROID_SIGNING_003: Package name changed after registration
**Cause:** applicationId != registered package  
**Solution:** Create new app in AdMob or revert package name

### ANDROID_SIGNING_004: Version code too low
**Cause:** Play Console rejecting old version  
**Solution:** Increment versionCode

### ANDROID_SIGNING_005: App Bundle vs APK mismatch
**Cause:** AAB signing different from APK  
**Solution:** Use Play App Signing

### ANDROID_SIGNING_006: Multi-APK configuration
**Cause:** Different package names for different APKs  
**Solution:** Use same package for all splits

### ANDROID_SIGNING_007: Instant app vs installed app
**Cause:** Instant app using wrong ad units  
**Solution:** Detect instant app mode

### ANDROID_SIGNING_008: Beta/internal testing track
**Cause:** Different signature in test track  
**Solution:** Promote to production track

### ANDROID_SIGNING_009: App not published
**Cause:** Draft app not generating ad impressions  
**Solution:** Publish to at least closed testing

### ANDROID_SIGNING_010: Google Play verification failing
**Cause:** Play license check failing  
**Solution:**
```kotlin
implementation 'com.google.android.play:core:1.10.3'
```

---

## CATEGORY 13: AD LOADING & CALLBACKS (10)

### ANDROID_LOAD_001: AdRequest null or empty
**Cause:** Not building AdRequest properly  
**Solution:**
```kotlin
val adRequest = AdRequest.Builder().build()
```

### ANDROID_LOAD_002: Loading ad immediately after previous
**Cause:** Not waiting for callback  
**Solution:**
```kotlin
var isLoading = false

fun loadAd() {
    if (isLoading) return
    isLoading = true
    // Load ad
}
```

### ANDROID_LOAD_003: Callback not implemented
**Cause:** Missing AdLoadCallback implementation  
**Solution:**
```kotlin
InterstitialAd.load(this, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
    override fun onAdLoaded(ad: InterstitialAd) {
        interstitialAd = ad
    }
    
    override fun onAdFailedToLoad(error: LoadAdError) {
        Log.e("Ad", "Failed: ${error.message}")
    }
})
```

### ANDROID_LOAD_004: Retry logic too aggressive
**Cause:** Loading ads in tight loop  
**Solution:**
```kotlin
private var retryDelayMs = 1000L

fun loadAdWithRetry() {
    InterstitialAd.load(this, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
        override fun onAdFailedToLoad(error: LoadAdError) {
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(60000)
            handler.postDelayed({ loadAdWithRetry() }, retryDelayMs)
        }
    })
}
```

### ANDROID_LOAD_005: Request configuration missing
**Cause:** Global settings not applied  
**Solution:**
```kotlin
val requestConfiguration = RequestConfiguration.Builder()
    .setTestDeviceIds(listOf("DEVICE_ID"))
    .build()

MobileAds.setRequestConfiguration(requestConfiguration)
```

### ANDROID_LOAD_006: Mediation adapter not loaded
**Cause:** Third-party network adapter missing  
**Solution:**
```gradle
implementation 'com.google.ads.mediation:facebook:6.x.x.x'
```

### ANDROID_LOAD_007: Ad already used
**Cause:** Showing same InterstitialAd instance twice  
**Solution:** Load new ad after each show

### ANDROID_LOAD_008: Load timeout not handled
**Cause:** Ad load hanging indefinitely  
**Solution:**
```kotlin
val timeoutJob = lifecycleScope.launch {
    delay(30000)
    // Handle timeout
}
```

### ANDROID_LOAD_009: Error code not checked
**Cause:** Ignoring specific LoadAdError codes  
**Solution:**
```kotlin
override fun onAdFailedToLoad(error: LoadAdError) {
    when (error.code) {
        AdRequest.ERROR_CODE_INTERNAL_ERROR -> /* Retry */
        AdRequest.ERROR_CODE_NETWORK_ERROR -> /* Check network */
        AdRequest.ERROR_CODE_NO_FILL -> /* No inventory */
        else -> Log.e("Ad", "Error: ${error.message}")
    }
}
```

### ANDROID_LOAD_010: FullScreenContentCallback not set
**Cause:** Missing callback for ad events  
**Solution:**
```kotlin
interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
    override fun onAdDismissedFullScreenContent() {
        interstitialAd = null
        loadAd()
    }
    
    override fun onAdFailedToShowFullScreenContent(error: AdError) {
        Log.e("Ad", "Show failed: ${error.message}")
    }
    
    override fun onAdShowedFullScreenContent() {
        Log.d("Ad", "Ad shown")
    }
}
```

---

## ADDITIONAL CATEGORIES (Continue to 220)

Due to length constraints, here are category headers for the remaining 110 checks:

**CATEGORY 14: BUILD CONFIGURATION (10)**
- Gradle version incompatibility
- Android Gradle Plugin version mismatch
- Kotlin version conflicts
- Java version issues
- Build variant configuration
- Flavor dimensions
- BuildConfig field issues
- Resource merging conflicts
- AAPT2 errors
- Incremental build failures

**CATEGORY 15: TESTING & DEBUGGING (10)**
- Test ads not showing in production
- Ad Inspector errors
- Debug mode interfering
- Crashlytics blocking ads
- Firebase conflict
- Analytics tracking issues
- StrictMode violations
- LeakCanary false positives
- Espresso test failures
- Robolectric limitations

**CATEGORY 16: MEDIATION (10)**
- Waterfall misconfiguration
- Adapter version mismatch
- Network timeout
- Bid request failures
- CPM floor issues
- Network credentials missing
- Adapter initialization failure
- Mediation group conflicts
- eCPM optimization issues
- Fill rate problems

**CATEGORY 17: APP STORE COMPLIANCE (10)**
- App rejected for ad policy
- Invalid ad placement
- Accidental clicks
- Ad density too high
- Rewarded ad implementation
- Ad labeling missing
- Privacy policy incomplete
- Data collection disclosure
- Children's app violations
- Store listing mismatch

**CATEGORY 18: USER EXPERIENCE (10)**
- Ad shown too soon
- Frequency capping too high
- Ad interrupting gameplay
- No skip button
- Close button too small
- Ad orientation mismatch
- Sound playing unexpectedly
- Full-screen forced
- Back button disabled
- Loading spinner stuck

**CATEGORY 19: ANALYTICS & TRACKING (10)**
- Impression tracking failed
- Click tracking broken
- Conversion tracking missing
- Attribution issues
- Event logging errors
- Revenue reporting incorrect
- User properties not set
- Session tracking broken
- Custom dimensions missing
- Debug view not working

**CATEGORY 20: ADVANCED ISSUES (10)**
- App-ads.txt missing
- Sellers.json missing
- VAST/VPAID errors
- MRAID compatibility
- Video ad buffering
- Rewarded ad verification
- Server-side verification
- Ad quality issues
- Viewability measurement
- Brand safety violations

**CATEGORY 21: FIREBASE INTEGRATION (10)**
- Firebase Analytics conflict
- Remote Config blocking
- A/B testing interference
- Performance monitoring overhead
- Crashlytics symbol upload
- Cloud Messaging priority
- Dynamic Links conflict
- In-App Messaging overlap
- Predictions API interference
- App Distribution builds

**CATEGORY 22: GRADLE & DEPENDENCIES (10)**
- Dependency resolution failure
- Transitive dependency conflict
- Exclude rule too broad
- Implementation vs API scope
- Annotation processor issues
- Kotlin coroutines version
- AndroidX migration incomplete
- Jetpack version mismatch
- Material Design conflict
- Room database dependency

**TOTAL: 220 Potential Causes**

---

## Emergency Diagnostic Commands (Android)

```kotlin
// Run in Android Studio Logcat or debug console

// 1. Check MobileAds initialization
Log.d("AdMob", "Initialized: ${MobileAds.getInitializationStatus()}")

// 2. Verify ad unit IDs
Log.d("AdMob", "Interstitial ID: ${BuildConfig.ADMOB_INTERSTITIAL_ID}")
Log.d("AdMob", "Rewarded ID: ${BuildConfig.ADMOB_REWARDED_ID}")

// 3. Check network connectivity
val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
Log.d("Network", "Connected: ${cm.activeNetworkInfo?.isConnected}")

// 4. Verify Play Services
val result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
Log.d("PlayServices", "Available: ${result == ConnectionResult.SUCCESS}")

// 5. Check if ad is loaded
Log.d("AdMob", "Ad loaded: ${interstitialAd != null}")

// 6. Test ad request
val adRequest = AdRequest.Builder().build()
Log.d("AdMob", "Ad request: ${adRequest.isTestDevice(context)}")
```

---

## Priority Fix Order (Android)

1. **SDK Initialization (1-30)** - Must be first
2. **Manifest & Permissions (31-55)** - Critical configuration
3. **Ad Unit Configuration (56-75)** - Must match console
4. **Network Issues (76-95)** - Can't load without network
5. **Lifecycle Issues (96-115)** - Prevents crashes
6. **Memory Management (116-130)** - Stability
7. **All Other Categories** - Optimization and edge cases

---

## Automated Testing Script

```kotlin
class AdDiagnostics(private val context: Context) {
    
    fun runAllChecks(): List<DiagnosticResult> {
        val results = mutableListOf<DiagnosticResult>()
        
        // Check 1: SDK initialized
        results.add(DiagnosticResult(
            "SDK_001",
            MobileAds.getInitializationStatus() != null,
            "MobileAds initialized"
        ))
        
        // Check 2: Internet permission
        results.add(DiagnosticResult(
            "MANIFEST_001",
            context.checkCallingOrSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED,
            "Internet permission granted"
        ))
        
        // Check 3: Network available
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        results.add(DiagnosticResult(
            "NET_001",
            cm.activeNetworkInfo?.isConnected ?: false,
            "Network connected"
        ))
        
        // Check 4: Play Services
        results.add(DiagnosticResult(
            "SDK_003",
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS,
            "Play Services available"
        ))
        
        // Add remaining checks...
        
        return results
    }
    
    data class DiagnosticResult(
        val checkId: String,
        val passed: Boolean,
        val description: String
    )
}
```

Use this comprehensive guide to systematically diagnose and fix AdMob ad display issues on Android.
