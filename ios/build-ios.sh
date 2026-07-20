#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "==> Generating Xcode project (if xcodegen is installed)"
if command -v xcodegen >/dev/null 2>&1; then
  xcodegen generate
else
  echo "xcodegen not installed; using committed .xcodeproj if present."
fi

echo "==> Installing CocoaPods dependencies"
pod install --repo-update

echo "==> Building"
xcodebuild build \
  -workspace LinguaVibe.xcworkspace \
  -scheme LinguaVibe \
  -destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGNING_REQUIRED=NO

echo "==> Running tests"
xcodebuild test \
  -workspace LinguaVibe.xcworkspace \
  -scheme LinguaVibe \
  -destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGNING_REQUIRED=NO
