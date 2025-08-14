#!/bin/bash

echo "🏗️ Building AAB in Replit Environment"
echo "====================================="

# Set environment variables
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0

echo "📋 Environment Setup:"
echo "JAVA_HOME: $JAVA_HOME"
echo "ANDROID_HOME: $ANDROID_HOME"

# Check if Android SDK exists, if not download minimal tools
if [ ! -d "$ANDROID_HOME" ]; then
    echo "📥 Installing Android SDK tools..."
    mkdir -p $ANDROID_HOME
    cd $ANDROID_HOME
    
    # Download command line tools
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
    unzip -q commandlinetools-linux-9477386_latest.zip
    mkdir -p cmdline-tools/latest
    mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true
    
    export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
    
    # Accept licenses and install required packages
    yes | sdkmanager --licenses
    sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
    
    cd - > /dev/null
fi

# Navigate to android directory
cd android || {
    echo "❌ Android directory not found. Creating basic structure..."
    mkdir -p android
    cd android
}

echo "🔍 Checking Gradle wrapper..."
if [ ! -f "gradlew" ]; then
    echo "📥 Installing Gradle wrapper..."
    gradle wrapper --gradle-version 8.3
fi

# Make gradlew executable
chmod +x gradlew

echo "🧪 Testing Gradle setup..."
./gradlew --version

echo "🧹 Cleaning previous builds..."
./gradlew clean

echo "📦 Building release APK..."
./gradlew assembleRelease || {
    echo "⚠️ APK build failed, trying debug build..."
    ./gradlew assembleDebug
}

echo "📦 Building AAB bundle..."
./gradlew bundleRelease || {
    echo "⚠️ AAB build failed, trying debug bundle..."
    ./gradlew bundleDebug
}

echo "📁 Listing generated files..."
find . -name "*.aab" -o -name "*.apk" | while read file; do
    echo "Found: $file ($(stat -c%s "$file") bytes)"
done

# Copy outputs to root directory
echo "📋 Copying outputs..."
AAB_FILE=$(find . -name "*.aab" | head -1)
APK_FILE=$(find . -name "*.apk" | head -1)

if [ -f "$AAB_FILE" ]; then
    cp "$AAB_FILE" ../lingualink-replit-v1.0.61.aab
    echo "✅ AAB copied to: lingualink-replit-v1.0.61.aab"
    ls -la ../lingualink-replit-v1.0.61.aab
fi

if [ -f "$APK_FILE" ]; then
    cp "$APK_FILE" ../lingualink-replit-v1.0.61.apk
    echo "✅ APK copied to: lingualink-replit-v1.0.61.apk"
    ls -la ../lingualink-replit-v1.0.61.apk
    
    # Show APK contents
    echo "📋 APK contents preview:"
    unzip -l ../lingualink-replit-v1.0.61.apk | head -15
fi

cd - > /dev/null

echo ""
echo "🎉 Build completed!"
echo "📱 Generated files:"
ls -la lingualink-replit-v1.0.61.* 2>/dev/null || echo "No output files found"

echo ""
echo "🔧 If build failed, try:"
echo "   ./fix-android-gradle-ndk.sh"
echo "   ./build-aab-replit.sh"