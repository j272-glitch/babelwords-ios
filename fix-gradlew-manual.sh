#!/bin/bash

# Manual Gradlew Fix (for when git operations are restricted)
echo "🔧 Manual gradlew fix"

# Copy gradlew if missing
if [ ! -f "gradlew" ] && [ -f "android/gradlew" ]; then
    cp android/gradlew .
    echo "✅ Copied gradlew"
fi

# Set permissions
chmod +x gradlew
echo "✅ Set executable permissions"

# Copy gradlew.bat if missing
if [ ! -f "gradlew.bat" ] && [ -f "android/gradlew.bat" ]; then
    cp android/gradlew.bat .
    echo "✅ Added gradlew.bat"
fi

# Verify
if [ -f "gradlew" ] && [ -x "gradlew" ]; then
    echo "✅ gradlew is executable"
    ls -la gradlew
else
    echo "❌ gradlew fix failed"
    exit 1
fi

echo ""
echo "🎯 Manual fix complete. You need to commit these changes:"
echo "   1. Add the files: git add gradlew gradlew.bat"
echo "   2. Commit: git commit -m 'Fix gradlew permissions'"
echo "   3. Push: git push origin main"
