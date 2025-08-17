#!/bin/bash

# Comprehensive Gradlew Permissions Diagnosis and Fix
echo "🔍 COMPREHENSIVE GRADLEW PERMISSIONS DIAGNOSIS"
echo "=============================================="

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_section() {
    echo -e "\n${BLUE}=== $1 ===${NC}"
}

print_ok() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# Section 1: File System Analysis
print_section "FILE SYSTEM ANALYSIS"

echo "Checking gradlew file existence and permissions..."
if [ -f "gradlew" ]; then
    print_ok "gradlew file exists"
    ls -la gradlew
    
    # Check if executable
    if [ -x "gradlew" ]; then
        print_ok "gradlew is executable locally"
    else
        print_error "gradlew is NOT executable locally"
    fi
    
    # File details
    echo "File size: $(wc -c < gradlew) bytes"
    echo "Line count: $(wc -l < gradlew) lines"
    echo "First line: $(head -1 gradlew)"
    
else
    print_error "gradlew file does not exist"
fi

# Check android/gradlew for comparison
echo -e "\nChecking android/gradlew for comparison..."
if [ -f "android/gradlew" ]; then
    print_ok "android/gradlew exists"
    ls -la android/gradlew
    
    if [ -f "gradlew" ]; then
        if diff -q gradlew android/gradlew > /dev/null; then
            print_ok "gradlew and android/gradlew are identical"
        else
            print_warning "gradlew and android/gradlew differ"
            echo "Differences:"
            diff gradlew android/gradlew | head -10
        fi
    fi
else
    print_error "android/gradlew does not exist"
fi

# Section 2: Git Index Analysis
print_section "GIT INDEX ANALYSIS"

echo "Checking git file mode..."
if git ls-files gradlew > /dev/null 2>&1; then
    git_mode=$(git ls-files -s gradlew | cut -d' ' -f1)
    echo "Git file mode: $git_mode"
    
    if [ "$git_mode" = "100755" ]; then
        print_ok "Git recognizes gradlew as executable"
    elif [ "$git_mode" = "100644" ]; then
        print_error "Git thinks gradlew is NOT executable (mode: 100644)"
    else
        print_warning "Unexpected git file mode: $git_mode"
    fi
else
    print_error "gradlew is not in git index"
fi

# Section 3: Git Configuration Analysis
print_section "GIT CONFIGURATION ANALYSIS"

echo "Checking relevant git settings..."
filemode=$(git config core.filemode)
autocrlf=$(git config core.autocrlf)
safecrlf=$(git config core.safecrlf)

echo "core.filemode: ${filemode:-not set}"
echo "core.autocrlf: ${autocrlf:-not set}"  
echo "core.safecrlf: ${safecrlf:-not set}"

if [ "$filemode" = "false" ]; then
    print_warning "File mode tracking is disabled (core.filemode=false)"
fi

# Section 4: Repository State Analysis
print_section "REPOSITORY STATE ANALYSIS"

echo "Checking git status..."
git status --porcelain

echo -e "\nChecking remote sync status..."
git fetch origin main 2>/dev/null
local_commit=$(git rev-parse HEAD)
remote_commit=$(git rev-parse origin/main)

echo "Local commit:  $local_commit"
echo "Remote commit: $remote_commit"

if [ "$local_commit" = "$remote_commit" ]; then
    print_ok "Local and remote are in sync"
else
    print_warning "Local and remote are out of sync"
fi

# Section 5: File Content Analysis
print_section "FILE CONTENT ANALYSIS"

if [ -f "gradlew" ]; then
    echo "Analyzing gradlew content..."
    
    # Check shebang
    first_line=$(head -1 gradlew)
    if [ "$first_line" = "#!/bin/sh" ]; then
        print_ok "Correct shebang: $first_line"
    else
        print_error "Incorrect shebang: $first_line"
    fi
    
    # Check for Windows line endings
    if grep -q $'\r' gradlew; then
        print_error "File contains Windows line endings (CRLF)"
    else
        print_ok "File has Unix line endings (LF)"
    fi
    
    # Check file encoding
    if command -v file > /dev/null; then
        file_type=$(file gradlew)
        echo "File type: $file_type"
    fi
fi

# Section 6: Solution Recommendations
print_section "SOLUTION RECOMMENDATIONS"

echo "Based on the analysis above, here are recommended fixes:"
echo ""

# Determine primary issue
needs_permission_fix=false
needs_git_add=false
needs_sync=false

if [ -f "gradlew" ] && [ ! -x "gradlew" ]; then
    needs_permission_fix=true
    echo "1. 🔧 Fix local file permissions:"
    echo "   chmod +x gradlew"
fi

git_mode=$(git ls-files -s gradlew 2>/dev/null | cut -d' ' -f1)
if [ "$git_mode" != "100755" ]; then
    needs_git_add=true
    echo "2. 🔄 Fix git index permissions:"
    echo "   git update-index --chmod=+x gradlew"
    echo "   git add gradlew"
fi

if [ "$local_commit" != "$remote_commit" ]; then
    needs_sync=true
    echo "3. 📡 Sync with remote:"
    echo "   git pull origin main"
    echo "   git push origin main"
fi

# Section 7: Automated Fix
print_section "AUTOMATED FIX"

echo "Do you want to apply the recommended fixes automatically? (y/n)"
read -r response

if [ "$response" = "y" ] || [ "$response" = "Y" ]; then
    echo "Applying fixes..."
    
    # Fix 1: File permissions
    if [ "$needs_permission_fix" = true ]; then
        chmod +x gradlew
        print_ok "Fixed local file permissions"
    fi
    
    # Fix 2: Git index
    if [ "$needs_git_add" = true ]; then
        git update-index --chmod=+x gradlew
        git add gradlew
        print_ok "Fixed git index permissions"
    fi
    
    # Fix 3: Commit if needed
    if ! git diff --cached --quiet; then
        git commit -m "Fix gradlew executable permissions

Auto-fix applied by comprehensive-gradlew-diagnosis.sh
- Set file mode to 755 (executable)
- Updated git index with correct permissions
- This should resolve GitHub Actions verification failure"
        print_ok "Committed permission fixes"
    fi
    
    # Fix 4: Sync with remote
    if [ "$needs_sync" = true ]; then
        echo "Syncing with remote..."
        if git push origin main; then
            print_ok "Successfully pushed to remote"
        else
            print_error "Push failed - may need manual intervention"
        fi
    fi
    
    echo ""
    print_section "FINAL VERIFICATION"
    
    # Final checks
    if [ -f "gradlew" ] && [ -x "gradlew" ]; then
        print_ok "gradlew is now executable locally"
    fi
    
    final_git_mode=$(git ls-files -s gradlew | cut -d' ' -f1)
    if [ "$final_git_mode" = "100755" ]; then
        print_ok "Git index shows correct executable mode"
    fi
    
    echo ""
    echo "🎯 Next steps:"
    echo "1. Go to GitHub Actions: https://github.com/j272-glitch/lingualink-android2/actions"
    echo "2. Run 'Android CI - Repository Ready' workflow"
    echo "3. The gradlew verification should now pass"
    
else
    echo "Skipped automated fix. Apply recommended changes manually."
fi

print_section "DIAGNOSIS COMPLETE"
echo "See gradlew-permissions-analysis.md for detailed explanation of all potential causes and solutions."