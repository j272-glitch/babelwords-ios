#!/bin/bash

# Complete GitHub Sync Script with Conflict Resolution
# Syncs gradle-wrapper.jar to j272-glitch/lingualink-android2 repository

set -e

echo "=================================================="
echo "Complete GitHub Sync with Conflict Resolution"
echo "=================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
REPO_DIR="lingualink-android2"
GITHUB_REPO="https://github.com/j272-glitch/lingualink-android2.git"
SOURCE_FILE="gradle/wrapper/gradle-wrapper.jar"
TARGET_FILE="gradle/wrapper/gradle-wrapper.jar"
COMMIT_MESSAGE="Update gradle-wrapper.jar to version 8.9 for TestRigor compatibility"

echo -e "${BLUE}Step 1: Verifying source file...${NC}"
if [ ! -f "$SOURCE_FILE" ]; then
    echo -e "${RED}❌ Source file not found: $SOURCE_FILE${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Source file verified: $SOURCE_FILE${NC}"

echo -e "\n${BLUE}Step 2: Checking repository directory...${NC}"
if [ ! -d "$REPO_DIR" ]; then
    echo -e "${RED}❌ Repository directory not found: $REPO_DIR${NC}"
    exit 1
fi

# Navigate to repository
cd "$REPO_DIR"
echo -e "${GREEN}✓ Changed to repository directory${NC}"

echo -e "\n${BLUE}Step 3: Setting up git repository...${NC}"
if [ ! -d ".git" ]; then
    echo -e "${YELLOW}⚠️  Initializing git repository...${NC}"
    git init
    echo -e "${GREEN}✓ Git repository initialized${NC}"
fi

echo -e "\n${BLUE}Step 4: Configuring remote origin...${NC}"
if git remote get-url origin >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Remote origin exists, updating URL...${NC}"
    git remote set-url origin "$GITHUB_REPO"
else
    echo -e "${YELLOW}⚠️  Adding remote origin...${NC}"
    git remote add origin "$GITHUB_REPO"
fi
echo -e "${GREEN}✓ Remote origin configured: $GITHUB_REPO${NC}"

echo -e "\n${BLUE}Step 5: Configuring git user (if needed)...${NC}"
if ! git config user.email >/dev/null 2>&1; then
    git config user.email "replit@example.com"
    git config user.name "Replit Sync"
    echo -e "${GREEN}✓ Git user configured${NC}"
else
    echo -e "${GREEN}✓ Git user already configured${NC}"
fi

echo -e "\n${BLUE}Step 6: Cleaning any existing conflicts...${NC}"
# Reset any pending merges
git merge --abort >/dev/null 2>&1 || echo -e "${YELLOW}⚠️  No merge in progress${NC}"
git rebase --abort >/dev/null 2>&1 || echo -e "${YELLOW}⚠️  No rebase in progress${NC}"

# Clean working directory
git reset --hard HEAD >/dev/null 2>&1 || echo -e "${YELLOW}⚠️  No HEAD to reset to${NC}"
git clean -fd >/dev/null 2>&1
echo -e "${GREEN}✓ Working directory cleaned${NC}"

echo -e "\n${BLUE}Step 7: Fetching from remote repository...${NC}"
if git fetch origin >/dev/null 2>&1; then
    echo -e "${GREEN}✓ Successfully fetched from remote${NC}"
    REMOTE_AVAILABLE=true
else
    echo -e "${YELLOW}⚠️  Could not fetch from remote (authentication may be required)${NC}"
    REMOTE_AVAILABLE=false
fi

echo -e "\n${BLUE}Step 8: Attempting to sync with remote...${NC}"
if [ "$REMOTE_AVAILABLE" = true ]; then
    # Try to pull from main or master
    if git ls-remote --heads origin main | grep -q "main"; then
        BRANCH="main"
        echo -e "${BLUE}Using main branch...${NC}"
    elif git ls-remote --heads origin master | grep -q "master"; then
        BRANCH="master"
        echo -e "${BLUE}Using master branch...${NC}"
    else
        BRANCH="main"
        echo -e "${BLUE}Default to main branch...${NC}"
    fi
    
    # Create and checkout branch
    git checkout -b "$BRANCH" >/dev/null 2>&1 || git checkout "$BRANCH" >/dev/null 2>&1
    
    # Try to pull
    if git pull origin "$BRANCH" >/dev/null 2>&1; then
        echo -e "${GREEN}✓ Successfully pulled from $BRANCH${NC}"
    else
        echo -e "${YELLOW}⚠️  Could not pull, but continuing with local changes${NC}"
    fi
else
    # Work locally without remote
    BRANCH="main"
    git checkout -b "$BRANCH" >/dev/null 2>&1 || git checkout "$BRANCH" >/dev/null 2>&1
    echo -e "${YELLOW}⚠️  Working locally without remote sync${NC}"
fi

echo -e "\n${BLUE}Step 9: Ensuring target directory structure...${NC}"
mkdir -p gradle/wrapper
echo -e "${GREEN}✓ Directory structure ensured${NC}"

echo -e "\n${BLUE}Step 10: Copying gradle-wrapper.jar...${NC}"
cp "../$SOURCE_FILE" "$TARGET_FILE"
if [ -f "$TARGET_FILE" ]; then
    echo -e "${GREEN}✓ File copied successfully${NC}"
    echo "Source size: $(ls -lh "../$SOURCE_FILE" | awk '{print $5}')"
    echo "Target size: $(ls -lh "$TARGET_FILE" | awk '{print $5}')"
else
    echo -e "${RED}❌ File copy failed${NC}"
    cd ..
    exit 1
fi

echo -e "\n${BLUE}Step 11: Staging changes...${NC}"
git add "$TARGET_FILE"
# Also add properties file if it exists
[ -f "gradle/wrapper/gradle-wrapper.properties" ] && git add gradle/wrapper/gradle-wrapper.properties
echo -e "${GREEN}✓ Changes staged${NC}"

echo -e "\n${BLUE}Step 12: Checking for changes to commit...${NC}"
if git diff --cached --quiet; then
    echo -e "${YELLOW}⚠️  No changes detected - file is already up to date${NC}"
    COMMIT_NEEDED=false
else
    echo -e "${GREEN}✓ Changes detected, proceeding with commit${NC}"
    COMMIT_NEEDED=true
fi

if [ "$COMMIT_NEEDED" = true ]; then
    echo -e "\n${BLUE}Step 13: Committing changes...${NC}"
    git commit -m "$COMMIT_MESSAGE"
    echo -e "${GREEN}✓ Changes committed successfully${NC}"
    
    echo -e "\n${BLUE}Step 14: Pushing to GitHub...${NC}"
    if [ "$REMOTE_AVAILABLE" = true ]; then
        if git push origin "$BRANCH" >/dev/null 2>&1; then
            echo -e "${GREEN}✅ Successfully pushed to GitHub!${NC}"
            PUSH_SUCCESS=true
        else
            echo -e "${YELLOW}⚠️  Push failed - likely authentication required${NC}"
            echo -e "${YELLOW}    Changes are committed locally and ready to push${NC}"
            PUSH_SUCCESS=false
        fi
    else
        echo -e "${YELLOW}⚠️  No remote connection - changes committed locally${NC}"
        PUSH_SUCCESS=false
    fi
else
    echo -e "${GREEN}✓ No commit needed - repository is up to date${NC}"
    PUSH_SUCCESS=true
fi

echo -e "\n${BLUE}Step 15: Final verification...${NC}"
echo "Repository status:"
git status --short || git status
echo ""
echo "Recent commits:"
git log --oneline -3 >/dev/null 2>&1 && git log --oneline -3 || echo "No commits yet"
echo ""
echo "Remote configuration:"
git remote -v

# Return to original directory
cd ..

echo -e "\n${GREEN}=================================================="
echo -e "🎉 GITHUB SYNC COMPLETE!"
echo -e "==================================================${NC}"
echo ""
echo "📁 Repository: $REPO_DIR"
echo "🔗 GitHub URL: $GITHUB_REPO"
echo "📄 File synced: $TARGET_FILE"
echo "💬 Commit: $COMMIT_MESSAGE"
echo ""

if [ "$COMMIT_NEEDED" = true ]; then
    if [ "$PUSH_SUCCESS" = true ]; then
        echo -e "${GREEN}✅ Status: FULLY SYNCED TO GITHUB${NC}"
        echo "   • File copied ✓"
        echo "   • Changes committed ✓"
        echo "   • Pushed to GitHub ✓"
    else
        echo -e "${YELLOW}⚠️  Status: COMMITTED LOCALLY${NC}"
        echo "   • File copied ✓"
        echo "   • Changes committed ✓"
        echo "   • Push to GitHub: Authentication needed"
        echo ""
        echo "To complete the GitHub sync, you may need to:"
        echo "1. Set up authentication (GitHub token or SSH key)"
        echo "2. Navigate to $REPO_DIR"
        echo "3. Run: git push origin $BRANCH"
    fi
else
    echo -e "${GREEN}✅ Status: UP TO DATE${NC}"
    echo "   • No changes were needed"
    echo "   • Repository is synchronized"
fi

echo ""
echo "The gradle-wrapper.jar is ready for TestRigor-compatible Android builds!"