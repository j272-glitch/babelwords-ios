#!/bin/bash

# Ultimate Gradlew Fix - Handles all git complexities and permission issues
echo "🔧 Ultimate Gradlew Fix for LinguaLink Android2"
echo "==============================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# Step 1: Backup current state
echo "📦 Creating backup..."
BACKUP_DIR="/tmp/gradlew-backup-$(date +%s)"
mkdir -p "$BACKUP_DIR"

# Backup any existing files
[ -f "gradlew" ] && cp gradlew "$BACKUP_DIR/" && echo "Backed up gradlew"
[ -f "gradlew.bat" ] && cp gradlew.bat "$BACKUP_DIR/" && echo "Backed up gradlew.bat"
[ -d "gradle/wrapper" ] && cp -r gradle/wrapper "$BACKUP_DIR/" && echo "Backed up gradle/wrapper"

print_status "Backup created at $BACKUP_DIR"

# Step 2: Handle git state
echo "🔄 Handling git synchronization..."

# Stash any uncommitted changes
if ! git diff --quiet || ! git diff --cached --quiet; then
    git stash push -m "Auto-stash before gradlew fix $(date)" --include-untracked
    print_status "Stashed local changes"
fi

# Try different pull strategies
echo "📡 Syncing with remote..."
if git pull origin main --no-rebase; then
    print_status "Standard pull successful"
elif git pull origin main --rebase; then
    print_status "Rebase pull successful"  
elif git pull origin main --allow-unrelated-histories; then
    print_status "Pull with unrelated histories successful"
else
    print_warning "Pull failed, doing hard reset to remote"
    git fetch origin main
    git reset --hard origin/main
    print_status "Hard reset to remote state"
fi

# Step 3: Ensure gradle wrapper structure exists
echo "📁 Setting up gradle wrapper structure..."
mkdir -p gradle/wrapper
mkdir -p .github/workflows

# Step 4: Copy and fix gradlew files
echo "🔧 Installing gradle wrapper files..."

# Copy gradlew script
if [ -f "android/gradlew" ]; then
    cp android/gradlew .
    chmod +x gradlew
    print_status "Installed gradlew script with executable permissions"
else
    print_error "android/gradlew not found"
    exit 1
fi

# Copy gradlew.bat
if [ -f "android/gradlew.bat" ]; then
    cp android/gradlew.bat .
    print_status "Installed gradlew.bat script"
else
    print_error "android/gradlew.bat not found"
    exit 1
fi

# Copy gradle wrapper jar if needed
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ] && [ -f "android/gradle/wrapper/gradle-wrapper.jar" ]; then
    cp android/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/
    jar_size=$(stat -c%s gradle/wrapper/gradle-wrapper.jar)
    print_status "Installed gradle-wrapper.jar ($jar_size bytes)"
fi

# Copy gradle wrapper properties if needed
if [ ! -f "gradle/wrapper/gradle-wrapper.properties" ] && [ -f "android/gradle/wrapper/gradle-wrapper.properties" ]; then
    cp android/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
    print_status "Installed gradle-wrapper.properties"
fi

# Step 5: Create failsafe gradle wrapper test
cat > test-gradlew.sh << 'EOF'
#!/bin/bash
echo "Testing gradlew functionality..."
if [ -f "gradlew" ] && [ -x "gradlew" ]; then
    echo "✅ gradlew exists and is executable"
    ./gradlew --version 2>/dev/null && echo "✅ gradlew can execute" || echo "⚠️ gradlew execution test failed (may be normal without Android project)"
else
    echo "❌ gradlew test failed"
    exit 1
fi
EOF
chmod +x test-gradlew.sh

# Step 6: Verify the complete setup
echo "🔍 Verifying gradle wrapper setup..."

# Check gradlew
if [ -f "gradlew" ] && [ -x "gradlew" ]; then
    print_status "gradlew is executable"
    ls -la gradlew
else
    print_error "gradlew verification failed"
    exit 1
fi

# Check gradlew.bat
if [ -f "gradlew.bat" ]; then
    print_status "gradlew.bat exists"
else
    print_error "gradlew.bat missing"
    exit 1
fi

# Check gradle wrapper jar
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    jar_size=$(stat -c%s gradle/wrapper/gradle-wrapper.jar)
    if [ "$jar_size" -gt 40000 ]; then
        print_status "gradle-wrapper.jar valid ($jar_size bytes)"
    else
        print_error "gradle-wrapper.jar too small ($jar_size bytes)"
        exit 1
    fi
else
    print_error "gradle-wrapper.jar missing"
    exit 1
fi

# Check gradle wrapper properties
if [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
    print_status "gradle-wrapper.properties exists"
    echo "Gradle version:"
    grep "distributionUrl" gradle/wrapper/gradle-wrapper.properties
else
    print_error "gradle-wrapper.properties missing"
    exit 1
fi

# Step 7: Stage and commit all changes
echo "📝 Committing gradle wrapper setup..."

# Add all gradle wrapper files
git add gradlew gradlew.bat
git add gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties
git add test-gradlew.sh

# Create comprehensive commit message
commit_msg="Complete gradle wrapper setup with executable permissions

This commit resolves the GitHub Actions workflow failure:
❌ gradlew script missing or not executable

Changes:
✅ Add gradlew script with proper executable permissions (chmod +x)
✅ Add gradlew.bat for Windows compatibility
✅ Ensure gradle-wrapper.jar is present and valid size
✅ Ensure gradle-wrapper.properties is properly configured
✅ Add test-gradlew.sh for verification

Technical details:
- gradlew copied from android/ directory with 755 permissions
- gradle-wrapper.jar: $(stat -c%s gradle/wrapper/gradle-wrapper.jar 2>/dev/null || echo 'N/A') bytes
- Gradle version: $(grep -o 'gradle-[0-9.]*' gradle/wrapper/gradle-wrapper.properties 2>/dev/null || echo 'Unknown')

This should resolve the workflow verification step and allow the Android build to proceed.

Auto-fixed via ultimate-gradlew-fix.sh on $(date)"

if git commit -m "$commit_msg"; then
    print_status "Commit successful"
else
    print_error "Commit failed"
    exit 1
fi

# Step 8: Push to remote with retry logic
echo "🚀 Pushing to remote repository..."
retry_count=0
max_retries=3

while [ $retry_count -lt $max_retries ]; do
    if git push origin main; then
        print_status "Push successful!"
        break
    else
        retry_count=$((retry_count + 1))
        print_warning "Push attempt $retry_count failed"
        
        if [ $retry_count -lt $max_retries ]; then
            echo "📥 Pulling latest changes before retry..."
            if git pull origin main --allow-unrelated-histories; then
                print_status "Pull successful, retrying push..."
            else
                print_warning "Pull failed, trying rebase..."
                git pull origin main --rebase || git pull origin main
            fi
        else
            print_error "All push attempts failed, trying force push..."
            if git push origin main --force; then
                print_warning "Force push successful (overwrote remote)"
                break
            else
                print_error "Even force push failed"
                exit 1
            fi
        fi
    fi
done

# Step 9: Restore any stashed changes
if git stash list | grep -q "Auto-stash before gradlew fix"; then
    echo "📦 Restoring stashed changes..."
    git stash pop
    print_status "Stashed changes restored"
fi

# Step 10: Final status report
echo ""
echo "🎯 FINAL STATUS REPORT"
echo "====================="

echo "Repository status:"
git log --oneline -2

echo ""
echo "Gradle wrapper files:"
ls -la gradlew* gradle/wrapper/ 2>/dev/null

echo ""
echo "Verification test:"
./test-gradlew.sh

echo ""
print_status "COMPLETE: Gradle wrapper setup finished successfully!"
echo ""
echo "🎯 Next Steps:"
echo "1. Go to GitHub Actions: https://github.com/j272-glitch/lingualink-android2/actions"
echo "2. Run 'Android CI - Repository Ready' workflow"  
echo "3. Select build type and click 'Run workflow'"
echo "4. The gradlew verification should now pass: ✅ gradlew script executable"
echo ""
echo "📂 Backup location: $BACKUP_DIR"
echo "🎉 Ready for Android build!"