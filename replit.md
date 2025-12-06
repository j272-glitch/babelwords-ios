# Overview

LinguaGT is an Android-based real-time speech translation application. The project wraps a web application (hosted at linguagt.com/gtlingua.com) in a native Android WebView container, providing speech translation capabilities across 36 languages with microphone access and modern web features.

The application is built using:
- **Native Android (Kotlin)** - WebView wrapper with permission handling
- **GitHub Actions** - Automated CI/CD for APK/AAB builds
- **Capacitor** - Cross-platform mobile framework
- **Node.js** - Development tooling and web serving

# User Preferences

Preferred communication style: Simple, everyday language.

# System Architecture

## Build System

**Android Gradle Plugin (AGP)**: The project has evolved through multiple AGP versions, with version 8.0.2 being the most stable for TestRigor compatibility and CI/CD builds. Higher versions (8.1.x, 8.5.x) have been tested but caused manifest corruption and build failures in containerized environments.

**Gradle Version**: Uses Gradle 8.3-8.9 with JDK 17. The build system is configured with legacy packaging mode and resource optimization disabled to prevent TestRigor manifest corruption issues.

**Build Configurations**: Supports both debug and release builds with signed APK/AAB generation. Release builds use a production keystore (linguagt-release-key) with SHA-256 fingerprint-based signing for Google Play Store deployment.

## CI/CD Pipeline

**GitHub Actions**: Multiple workflow iterations exist, with the most recent being "LinguaGT Android Build - AGP 8.5.1 + Gradle 8.9". The workflows handle:
- Android SDK installation and configuration
- Gradle wrapper setup and dependency caching
- Multi-variant builds (debug/release, APK/AAB)
- Automated versioning and GitHub releases
- Artifact upload for distribution

**Build Challenges**: The project documentation extensively covers GitHub Actions container limitations, particularly around Gradle wrapper network dependencies and Android build tool compatibility. Solutions implemented include pre-caching Gradle distributions and using stable AGP versions.

**Signing Configuration**: Uses base64-encoded keystore files stored in GitHub Secrets for secure APK signing during CI builds. The keystore has 68+ years validity (until 2093) using RSA 2048-bit encryption.

## Android Application Architecture

**WebView Container**: The MainActivity wraps the web application in a WebView with:
- JavaScript enabled and modern web APIs
- Microphone permission handling for speech recognition
- Deep link support for linguagt.com domain
- SSL error handling and custom Chrome client

**Permission Management**: Runtime permission requests for:
- RECORD_AUDIO (speech translation)
- INTERNET and ACCESS_NETWORK_STATE (connectivity)
- MODIFY_AUDIO_SETTINGS (audio processing)
- WAKE_LOCK (background operation)

**Deep Linking**: Configured with Android App Links for linguagt.com domain using assetlinks.json verification. Supports both HTTPS and custom scheme (linguagt://) deep links.

**Crash Prevention**: Implements audio resource cleanup, navigation guards, and state mutation protection to prevent WebView crashes during system dialog interactions.

## Third-Party Integrations

**Ad Integration**: Multiple ad SDK integrations explored:
- AppLovin SDK for interstitial/rewarded ads
- Unity LevelPlay for ad mediation
- Conversation-based ad triggers (after N translations)

**Analytics**: User activity tracking with conversation counting and session management.

**Security**: ConversationSecurity module for data protection (referenced but implementation details not fully visible).

# External Dependencies

## Core Android Dependencies
- **Android SDK**: Compile SDK 34, Min SDK 21, Target SDK 34
- **Kotlin**: Version 1.8.10 for AGP 8.0.2 compatibility
- **AndroidX Libraries**: Core, Activity, WebView components
- **Capacitor CLI**: Version 7.4.3 for cross-platform mobile support

## Node.js Dependencies
- **@anthropic-ai/sdk**: Version 0.60.0 (Claude AI integration)
- **Express**: Version 5.1.0 (web server)
- **Puppeteer**: Version 24.17.1 (browser automation)

## Build Tools
- **Gradle Wrapper**: Multiple versions tested (7.6, 8.3, 8.9)
- **Android Build Tools**: Version 34.0.0
- **JDK**: Version 17 (required for Gradle 8.x)

## Cloud Services
- **GitHub Actions**: Ubuntu 24.04 runners for CI/CD
- **Web Hosting**: linguagt.com and gtlingua.com domains
- **Deep Link Verification**: Google Digital Asset Links API

## Optional Integrations
- **Twilio**: SMS notifications (referenced in attached assets)
- **Facebook Login**: OAuth authentication (GDPR-compliant implementation documented)
- **Google/X (Twitter) Sign-In**: Social authentication options
- **TestRigor**: Mobile testing platform compatibility requirements

## Development Tools
- **Replit Database**: Key-value storage (@replit/database)
- **Semgrep**: Security scanning with Bicep rules
- **Docker**: Claude AI development environment with MCP servers