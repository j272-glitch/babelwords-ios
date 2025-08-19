#!/bin/bash

# Simple script to push Gradle files to lingualink-android2
# Usage: ./push-gradle-files-simple.sh [github-username]

GITHUB_USERNAME=${1:-"your-username"}
REPO_NAME="lingualink-android2"

echo "Pushing Gradle files to $GITHUB_USERNAME/$REPO_NAME..."

# Quick clone and push
git clone "https://github.com/$GITHUB_USERNAME/$REPO_NAME.git" temp-repo || exit 1
cd temp-repo

# Create directories
mkdir -p gradle/wrapper app

# Copy files
cp ../android/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
cp ../android/build.gradle .
cp ../android/app/build.gradle app/
cp ../android/settings.gradle .
cp ../android/gradle.properties .

# Git operations
git add .
git commit -m "Update Gradle files for Testrigor manifest fix v69"
git push origin main

cd ..
rm -rf temp-repo

echo "✅ Done! Files pushed to GitHub."