#!/bin/bash

echo "📊 GitHub Actions Build Status Monitor"
echo "====================================="

if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ GITHUB_TOKEN not found"
    exit 1
fi

REPO_OWNER="j272-glitch"
REPO_NAME="lingualink-android"

echo "🔍 Checking latest workflow runs..."
echo "Repository: $REPO_OWNER/$REPO_NAME"
echo ""

# Get recent workflow runs
RESPONSE=$(curl -s \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github.v3+json" \
    "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/runs?per_page=5")

echo "📋 Recent workflow runs:"
echo "$RESPONSE" | grep -E '"(id|status|conclusion|created_at|html_url)"' | sed 's/^[[:space:]]*//' | while read line; do
    echo "  $line"
done

echo ""
echo "🔗 Direct links:"
echo "Actions: https://github.com/$REPO_OWNER/$REPO_NAME/actions"
echo "Releases: https://github.com/$REPO_OWNER/$REPO_NAME/releases"

echo ""
echo "📱 Expected output files:"
echo "- lingualink-native-android-v1.0.61.aab"
echo "- lingualink-native-android-v1.0.61.apk"

echo ""
echo "🔄 To trigger new build manually:"
echo "1. Go to: https://github.com/$REPO_OWNER/$REPO_NAME/actions"
echo "2. Select 'Build AAB with Native Android Environment'"
echo "3. Click 'Run workflow' → 'Run workflow'"