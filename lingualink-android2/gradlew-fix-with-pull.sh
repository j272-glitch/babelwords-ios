#!/bin/bash

# Simple Gradlew Fix Script with Pull Before Push
echo "🔧 Fixing gradlew permissions with proper git sync"
echo "================================================="

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_ok() { echo -e "${GREEN}✅ $1${NC}"; }
print_error() { echo -e "${RED}❌ $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }

# Step 1: Pull latest changes first
echo "📥 Step 1: Pulling latest changes from remote..."
if git pull origin main --allow-unrelated-histories; then
    print_ok "Pull successful"
elif git pull origin main; then
    print_ok "Standard pull successful"
else
    print_warning "Pull failed, will proceed with local changes"
    # Try fetch and merge as fallback
    git fetch origin main
    git merge origin/main --allow-unrelated-histories -m "Merge before gradlew fix" || print_warning "Merge had conflicts"
fi

# Step 2: Ensure gradlew exists and copy if needed
echo "📁 Step 2: Ensuring gradlew file exists..."
if [ ! -f "gradlew" ] && [ -f "android/gradlew" ]; then
    cp android/gradlew .
    print_ok "Copied gradlew from android/ directory"
elif [ ! -f "gradlew" ]; then
    print_error "No gradlew file found in current or android/ directory"
    exit 1
fi

# Step 3: Fix file permissions
echo "🔧 Step 3: Setting correct permissions..."
chmod +x gradlew
if [ -x "gradlew" ]; then
    print_ok "gradlew is now executable"
    ls -la gradlew
else
    print_error "Failed to make gradlew executable"
    exit 1
fi

# Step 4: Fix git index permissions
echo "📋 Step 4: Updating git index..."
git update-index --chmod=+x gradlew
git add gradlew

# Verify git recognizes executable permissions
git_mode=$(git ls-files -s gradlew | cut -d' ' -f1)
if [ "$git_mode" = "100755" ]; then
    print_ok "Git index shows correct executable mode (100755)"
else
    print_warning "Git mode is $git_mode (expected 100755)"
fi

# Step 5: Add gradlew.bat if missing
if [ ! -f "gradlew.bat" ] && [ -f "android/gradlew.bat" ]; then
    cp android/gradlew.bat .
    git add gradlew.bat
    print_ok "Added gradlew.bat"
fi

# Step 6: Commit the changes
echo "💾 Step 5: Committing permission fix..."
if git diff --cached --quiet; then
    print_warning "No changes to commit"
else
    git commit -m "Fix gradlew executable permissions for GitHub Actions

- Set gradlew file mode to 755 (executable)
- Updated git index with correct permissions using update-index --chmod=+x
- This resolves the 'gradlew script missing or not executable' error
- File verified: $(wc -l < gradlew) lines, $(wc -c < gradlew) bytes
- Mode in git: $(git ls-files -s gradlew | cut -d' ' -f1)

Auto-fixed on $(date)"

    if [ $? -eq 0 ]; then
        print_ok "Commit successful"
    else
        print_error "Commit failed"
        exit 1
    fi
fi

# Step 7: Push to remote
echo "🚀 Step 6: Pushing to remote repository..."
if git push origin main; then
    print_ok "Push successful!"
else
    print_warning "Push failed, trying again after another pull..."
    
    # Try pull again in case remote changed
    git pull origin main --allow-unrelated-histories || git pull origin main
    
    if git push origin main; then
        print_ok "Push successful after second pull!"
    else
        print_error "Push still failed, you may need to resolve conflicts manually"
        exit 1
    fi
fi

# Step 8: Final verification
echo "🔍 Final verification:"
echo "Local file: $(ls -la gradlew)"
echo "Git index:  $(git ls-files -s gradlew)"
echo ""
print_ok "Gradlew permissions fix complete!"
echo ""
echo "🎯 Next steps:"
echo "1. Go to: https://github.com/j272-glitch/lingualink-android2/actions"
echo "2. Run the 'Android CI - Repository Ready' workflow"
echo "3. The gradlew verification step should now pass ✅"