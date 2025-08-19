# LinguaLink Android Build

## Overview

LinguaLink is an Android translation application that integrates a web-based translator interface with native Android capabilities. The project is structured as a hybrid app using WebView to load a web application while providing native Android features through the TesterMobLib.aar library. The repository serves as a clean build environment specifically designed to overcome GitHub Actions restrictions and build reliable Android APK/AAB packages for distribution.

## User Preferences

Preferred communication style: Simple, everyday language.

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
- **aapt2**: Android Asset Packaging Tool for resource compilation
- **ProGuard**: Code shrinking and obfuscation for release builds

### Development Tools
- **Semgrep**: Static analysis security scanning with custom Bicep rules
- **Gradle Caching**: Build acceleration through dependency and distribution caching
- **JDK 17**: Required for Android Gradle Plugin 8.x compatibility