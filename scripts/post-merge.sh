#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

required_files=(
  ".github/workflows/ios-build.yml"
  "ios/project.yml"
  "ios/Podfile"
  "ios/Gemfile"
  "ios/BabelWords/Resources/Info.plist"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "Post-merge validation failed: missing $file" >&2
    exit 1
  fi
done

if ! grep -q 'bundleIdPrefix: com.babelwords' ios/project.yml; then
  echo "Post-merge validation failed: unexpected iOS bundle configuration" >&2
  exit 1
fi

if ! grep -q "platform :ios, '16.0'" ios/Podfile; then
  echo "Post-merge validation failed: unexpected iOS deployment target" >&2
  exit 1
fi

if ! grep -q 'xcode-version:' .github/workflows/ios-build.yml; then
  echo "Post-merge validation failed: workflow does not select Xcode explicitly" >&2
  exit 1
fi

if ! git diff --check; then
  echo "Post-merge validation failed: whitespace errors detected" >&2
  exit 1
fi

echo "Post-merge validation passed."
echo "Native iOS build and CocoaPods installation remain delegated to macOS CI."