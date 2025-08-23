# LinguaLink Android Build

## Overview

LinguaLink is an Android translation application that integrates a web-based translator interface with native Android capabilities. The project is structured as a hybrid app using WebView to load a web application while providing native Android features through the TesterMobLib.aar library. The repository serves as a clean build environment specifically designed to overcome GitHub Actions restrictions and build reliable Android APK/AAB packages for distribution.

## User Preferences

Preferred communication style: Simple, everyday language.

## Recent Changes (August 22, 2025)

- **Privacy Policy Integration**: Added Google Play Store compliant privacy policy system
  - Privacy policy URL: https://linguagt.com/policy (corrected URL)
  - First-run privacy acceptance dialog with direct link to full policy
  - Web app privacy banner injection via JavaScript
  - SharedPreferences storage for user consent
  - Required consent before app functionality activation
- **Enhanced Security**: Updated keystore with 68+ year validity (linguagt-release-key)
- **Version Management**: Updated workflow to version 2.0.0 for Google Play Console compatibility
- **Release APK**: Successfully extracted LinguaGT-Release-v2.0.0.apk (4.3 MB) ready for deployment
- **Release AAB**: Successfully extracted LinguaGT-Release-v2.0.0.aab (4.0 MB) optimized for Play Store
- **Dual Release Format**: Both APK (direct install) and AAB (Play Store) available for deployment
- **Enhanced Workflow v2**: Saved linguagt-v2.yml with advanced features including dynamic versioning, build caching, and automated GitHub releases

## System Architecture

### Frontend Architecture
- **Hybrid Web/Native Approach**: Uses Android WebView to load the main LinguaLink web application
- **MainActivity.kt**: Single activity pattern hosting the WebView with native Android integrations
- **WebChromeClient Integration**: Handles web permissions and user media access for speech translation features
- **Native Library Integration**: TesterMobLib.aar provides additional native functionality through JNI

### Backend Architecture
- **RESTful API Integration**: Retrofit 2.9.0 configured for HTTP communication with translation services
- **JSON Processing**: Gson library handles serialization/deserialization of API responses
- **Network Layer**: OkHttp provides robust HTTP/2 client capabilities with connection pooling
- **Async Processing**: Coroutines ready for handling translation requests without blocking UI

### Build System Architecture
- **Gradle-based Build**: Android Gradle Plugin 8.1.4 with Gradle 8.3 wrapper
- **Multi-format Output**: Generates both APK (direct installation) and AAB (Play Store) packages
- **ProGuard Configuration**: Code obfuscation and optimization with TesterMobLib preservation rules
- **GitHub Actions CI/CD**: Automated build pipeline with comprehensive error recovery strategies

### Error Recovery Architecture
- **Network Dependency Management**: Gradle distribution caching and progressive retry logic
- **Container Path Resolution**: Java environment path conflict detection and automatic override
- **Classpath Corruption Recovery**: Daemon-less builds with cache cleanup strategies
- **Build Failure Prevention**: Multiple fallback strategies including offline mode and simplified configurations

## Android Signing Configuration

### Release Keystore (Production - August 22, 2025)
- **Keystore File**: `release.keystore` (production-grade with enhanced security)
- **Validity**: 68+ years (25,000 days) - long-term stability
- **Alias**: `linguagt-release-key` (official LinguaGT release key)
- **Organization**: GTLingua Development, San Francisco, California, US
- **Passwords**: Store and key both use `gtlingua2025secure` (enhanced security)
- **Base64 File**: `keystore.base64.txt` (3.8 KB) ready for GitHub secrets
- **Required Secrets**: ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD
- **SHA-256 Fingerprint**: `38:5B:F1:BD:AF:F9:57:B9:62:77:C8:19:3F:02:80:F8:B0:3A:16:DA:7B:98:10:BC:90:2F:CC:45:92:D3:95:D6`

## External Dependencies

### Core Android Dependencies
- **Android SDK**: Target SDK 34, minimum SDK 24 for broad device compatibility
- **Android Gradle Plugin**: Version 8.1.4 for modern Android development features
- **Gradle**: Version 8.3 wrapper for consistent build environments

### Networking Stack
- **Retrofit**: Version 2.9.0 for type-safe HTTP client functionality
- **Gson**: Version 2.10.1 for JSON parsing and API response handling
- **OkHttp**: HTTP client with connection pooling and request/response interceptors

### Native Libraries
- **TesterMobLib.aar**: Custom AAR library providing specialized native functionality
- **Android WebView**: System WebView for rendering web application content

### Build and Deployment
- **GitHub Actions**: Ubuntu-based build environment with Android SDK
- **bundletool**: Google's tool for AAB generation and APK extraction
- **aapt2**: Android Asset Packaging Tool for resource compilation with multi-version support (8.1.4 and 7.4.2)
- **ProGuard**: Code shrinking and obfuscation for release builds
- **Testrigor Manifest Fix v69**: Advanced workflow for resolving "chunk type:16188" APK manifest corruption errors
- **TestRigor Build v71 Fixed**: Updated workflow addressing AGP 8.1+ compatibility issues and deprecated properties
- **TestRigor Build v72**: Advanced workflow with AGP 8.0.2 downgrade and comprehensive TestRigor compatibility settings
- **TestRigor Build Wrapper Fixed v73**: Critical fix for Gradle wrapper corruption preventing ClassNotFoundException: GradleWrapperMain
- **TestRigor Build Complete Translator**: Comprehensive workflow creating full Android app with WebView integration for https://gtlingua.com
- **TestRigor Build Complete Translator v2**: Enhanced version with improved WebView permissions and GTLingua-specific optimizations
- **TestRigor Build GTLingua-Optimized**: Specialized workflow optimized specifically for GTLingua.com with Deepgram Nova-2 integration
- **TestRigor Build GTLingua-Fixed**: Corrected workflow fixing Android layout dimension errors that caused build failures
- **TestRigor Build GTLingua-Optimized-2**: Second iteration of GTLingua-optimized workflow with enhanced features
- **TestRigor Build GTLingua-Optimized-3**: Third iteration of GTLingua workflow with further refinements
- **TestRigor Build GTLingua-Optimized-4**: Fourth iteration with enhanced Android SDK setup and improved Gradle configuration
- **TestRigor Build GTLingua-Enhanced**: Comprehensive WebView + Deepgram integration with advanced speech recognition capabilities
- **TestRigor Build GTLingua-Working**: Proven working workflow with successful APK generation and simplified architecture
- **TestRigor Build GTLingua-Working-AAB**: Android App Bundle variant optimized for Play Store distribution with universal APK extraction
- **TestRigor Build GTLingua-Release-Signed**: Production-ready workflow with automatic release keystore signing, generating signed APKs and AABs for distribution
- **GTLingua Android Build - Fixed**: Enhanced workflow with improved keystore validation, error handling, and comprehensive AAB support with bundletool integration

### Development Tools
- **Semgrep**: Static analysis security scanning with custom Bicep rules
- **Gradle Caching**: Build acceleration through dependency and distribution caching
- **JDK 17**: Required for Android Gradle Plugin 8.x compatibility