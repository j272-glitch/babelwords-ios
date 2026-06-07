# Android Integration with TesterMobLib.aar

This directory contains the Android project structure for LinguaLink with TesterMobLib.aar integration.

## Project Structure

```
android/
├── app/
│   ├── libs/
│   │   └── TesterMobLib.aar         # Your AAR library file
│   ├── src/main/
│   │   ├── AndroidManifest.xml      # App manifest with permissions
│   │   ├── java/com/lingualink/translator/
│   │   │   └── MainActivity.kt      # Main activity with WebView and TesterMobLib integration
│   │   └── res/                     # Android resources
│   ├── build.gradle                 # App-level build configuration
│   └── proguard-rules.pro          # ProGuard rules for TesterMobLib
├── gradle/wrapper/                  # Gradle wrapper files
├── build.gradle                     # Project-level build configuration
├── settings.gradle                  # Project settings
└── gradle.properties               # Gradle properties
```

## Key Features

### 1. TesterMobLib.aar Integration
- **Location**: `app/libs/TesterMobLib.aar`
- **Build Configuration**: Added to `app/build.gradle` with `implementation files('libs/TesterMobLib.aar')`
- **ProGuard**: Configured to preserve TesterMobLib classes

### 2. Networking Dependencies
- **Retrofit 2.9.0**: Modern HTTP client for Android
- **Gson Converter**: JSON serialization/deserialization for Retrofit
- **OkHttp**: HTTP & HTTP/2 client
- **Gson**: JSON parsing library

### 3. MainActivity.kt Features
- WebView integration to load your LinguaLink web app
- Permission handling for microphone and network access
- TesterMobLib initialization (placeholder - adjust based on actual API)
- WebChromeClient for handling web permissions
- Ready for Retrofit API integrations

### 4. Permissions
- `RECORD_AUDIO` - For speech translation functionality
- `INTERNET` and `ACCESS_NETWORK_STATE` - For web connectivity
- `MODIFY_AUDIO_SETTINGS` - For audio processing
- `WAKE_LOCK` - For keeping device awake during translation

## Building the Android App

### Prerequisites
1. Android Studio or Android SDK
2. Java 8+ or Kotlin support
3. Gradle 8.2+

### Build Commands
```bash
cd android
./gradlew assembleDebug    # Build debug APK
./gradlew assembleRelease  # Build release APK
```

### Install on Device
```bash
./gradlew installDebug     # Install debug version
```

## TesterMobLib Integration

The TesterMobLib.aar is integrated in `MainActivity.kt` in the `initializeTesterMobLib()` method. You'll need to:

1. **Check TesterMobLib Documentation**: Review the actual API methods provided by TesterMobLib
2. **Update Initialization Code**: Replace placeholder initialization with actual TesterMobLib API calls
3. **Add Required Dependencies**: If TesterMobLib requires additional dependencies, add them to `app/build.gradle`

### Example Usage (adjust based on actual API):
```kotlin
private fun initializeTesterMobLib() {
    try {
        // Replace with actual TesterMobLib API
        TesterMobLib.initialize(this)
        TesterMobLib.setConfiguration(configObject)
        // Add your specific TesterMobLib functionality here
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to initialize TesterMobLib", e)
    }
}
```

## Retrofit API Integration

I've created `ApiService.kt` with complete Retrofit setup for connecting to your LinguaLink backend:

### Features:
- **Translation API**: Direct integration with your `/api/translate` endpoint
- **User Authentication**: Connect to your user authentication system
- **Subscription Management**: Check user subscription status
- **Type-Safe**: Kotlin data classes for all API responses

### Usage Example:
```kotlin
// In your Activity or Fragment
lifecycleScope.launch {
    val translationManager = TranslationManager()
    val result = translationManager.translateText("Hello", "en", "es")
    result?.let { translation ->
        // Use the translated text
        println("Translation: ${translation.translatedText}")
    }
}
```

## Activity Lifecycle Management

All activities now extend `BaseActivity` which automatically handles TesterMobLib tracking:

### BaseActivity Features:
- **Automatic tracking** in onStart()/onStop() lifecycle methods
- **Activity-specific logging** with class name identification
- **Resume/Pause tracking** for detailed user behavior analytics
- **Centralized tracker management** through MainActivity.tracker

### Creating New Activities:
```kotlin
class YourActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Your activity code here
        // Tracking is automatically handled
    }
    
    // Optional: Override lifecycle methods for activity-specific behavior
    override fun onStart() {
        super.onStart() // Always call super first for tracking
        // Your custom onStart code here
    }
}
```

## WebView Configuration

The app uses a WebView to display your LinguaLink web application. Update the `webAppUrl` in `MainActivity.kt` to point to your actual deployment URL:

```kotlin
val webAppUrl = "https://linguagt.com"
webView.loadUrl(webAppUrl)
```

## Next Steps

1. **Test the AAR Integration**: Build the app and verify TesterMobLib loads correctly
2. **Implement Specific Features**: Use TesterMobLib APIs for your intended functionality
3. **Update Web App URL**: Point to your actual LinguaLink deployment
4. **Test on Device**: Install and test the hybrid app functionality
5. **Add App Icons**: Replace default icons in `res/mipmap/` directories