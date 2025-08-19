#!/bin/bash

# Script to push specific Gradle files to lingualink-android2 repository
# Files to push: gradle-wrapper.properties, build.gradle (root), app/build.gradle, settings.gradle, gradle.properties

set -e  # Exit on any error

REPO_NAME="lingualink-android2"
BRANCH="main"
COMMIT_MESSAGE="Update Gradle configuration files for Testrigor manifest fix v69"

echo "=== GRADLE FILES PUSH SCRIPT ==="
echo "Target Repository: $REPO_NAME"
echo "Target Branch: $BRANCH"
echo ""

# Check if we're in the right directory and have the android folder
if [ ! -d "android" ]; then
    echo "❌ Error: android/ directory not found. Make sure you're in the project root."
    exit 1
fi

# List files to be pushed
echo "📋 Files to be pushed:"
echo "1. android/gradle/wrapper/gradle-wrapper.properties"
echo "2. android/build.gradle (root build.gradle)"
echo "3. android/app/build.gradle (app build.gradle)"
echo "4. android/settings.gradle"
echo "5. android/gradle.properties"
echo ""

# Verify all files exist
missing_files=0
for file in "android/gradle/wrapper/gradle-wrapper.properties" "android/build.gradle" "android/app/build.gradle" "android/settings.gradle" "android/gradle.properties"; do
    if [ ! -f "$file" ]; then
        echo "❌ Missing file: $file"
        missing_files=$((missing_files + 1))
    else
        echo "✅ Found: $file"
    fi
done

if [ $missing_files -gt 0 ]; then
    echo ""
    echo "❌ Error: $missing_files file(s) missing. Cannot proceed."
    exit 1
fi

echo ""
echo "🚀 Starting git operations..."

# Clone the repository if it doesn't exist, or navigate to it if it does
if [ ! -d "$REPO_NAME" ]; then
    echo "📦 Cloning repository..."
    
    # Prompt for GitHub username if not set
    if [ -z "$GITHUB_USERNAME" ]; then
        read -p "Enter your GitHub username: " GITHUB_USERNAME
    fi
    
    git clone "https://github.com/$GITHUB_USERNAME/$REPO_NAME.git"
    if [ $? -ne 0 ]; then
        echo "❌ Failed to clone repository. Check repository name and access permissions."
        echo "Make sure the repository exists: https://github.com/$GITHUB_USERNAME/$REPO_NAME"
        exit 1
    fi
else
    echo "📂 Repository directory already exists"
fi

# Navigate to repository directory
cd "$REPO_NAME"

# Ensure we're on the correct branch
echo "🔄 Checking out $BRANCH branch..."
git checkout "$BRANCH" 2>/dev/null || git checkout -b "$BRANCH"

# Pull latest changes
echo "⬇️ Pulling latest changes..."
git pull origin "$BRANCH" 2>/dev/null || echo "Branch might not exist on remote yet"

# Go back to project root to copy files
cd ..

# Create necessary directory structure in the target repo
echo "📁 Creating directory structure..."
mkdir -p "$REPO_NAME/gradle/wrapper"
mkdir -p "$REPO_NAME/app"

# Copy files to the repository
echo "📋 Copying files..."

cp "android/gradle/wrapper/gradle-wrapper.properties" "$REPO_NAME/gradle/wrapper/"
echo "✅ Copied gradle-wrapper.properties"

cp "android/build.gradle" "$REPO_NAME/"
echo "✅ Copied build.gradle (root)"

cp "android/app/build.gradle" "$REPO_NAME/app/"
echo "✅ Copied app/build.gradle"

cp "android/settings.gradle" "$REPO_NAME/"
echo "✅ Copied settings.gradle"

cp "android/gradle.properties" "$REPO_NAME/"
echo "✅ Copied gradle.properties"

# Navigate back to repository for git operations
cd "$REPO_NAME"

# Check git status
echo ""
echo "📊 Git status:"
git status --short

# Add files to git
echo ""
echo "➕ Adding files to git..."
git add gradle/wrapper/gradle-wrapper.properties
git add build.gradle
git add app/build.gradle
git add settings.gradle
git add gradle.properties

# Check if there are any changes to commit
if git diff --staged --quiet; then
    echo "ℹ️ No changes detected. Files are already up to date."
    cd ..
    exit 0
fi

# Show what will be committed
echo ""
echo "📝 Changes to be committed:"
git diff --staged --name-only

# Commit changes
echo ""
echo "💾 Committing changes..."
git commit -m "$COMMIT_MESSAGE"

# Push to repository
echo ""
echo "⬆️ Pushing to GitHub..."
git push origin "$BRANCH"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ SUCCESS: All Gradle files pushed to $REPO_NAME!"
    echo "🔗 Repository: https://github.com/$GITHUB_USERNAME/$REPO_NAME"
    echo "📂 Branch: $BRANCH"
    echo ""
    echo "📋 Pushed files:"
    echo "- gradle/wrapper/gradle-wrapper.properties"
    echo "- build.gradle"
    echo "- app/build.gradle"
    echo "- settings.gradle"
    echo "- gradle.properties"
else
    echo ""
    echo "❌ Error: Failed to push to GitHub. Check your authentication and permissions."
    exit 1
fi

# Return to original directory
cd ..

echo ""
echo "🎉 Script completed successfully!"