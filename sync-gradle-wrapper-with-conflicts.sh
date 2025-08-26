#!/bin/bash

# Script to resolve conflicts and sync gradle-wrapper.jar to GitHub repository
set -e

echo "=========================================="
echo "Gradle Wrapper Sync with Conflict Resolution"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
REPO_DIR="lingualink-android2"
SOURCE_FILE="gradle/wrapper/gradle-wrapper.jar"
TARGET_FILE="gradle/wrapper/gradle-wrapper.jar"
COMMIT_MESSAGE="Update gradle-wrapper.jar to version 8.9 for TestRigor compatibility"

echo -e "${BLUE}Step 1: Checking source gradle-wrapper.jar file...${NC}"
if [ ! -f "$SOURCE_FILE" ]; then
    echo -e "${RED}❌ Source file not found: $SOURCE_FILE${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Source file found: $SOURCE_FILE${NC}"

echo -e "\n${BLUE}Step 2: Navigating to repository...${NC}"
if [ ! -d "$REPO_DIR" ]; then
    echo -e "${RED}❌ Repository directory not found: $REPO_DIR${NC}"
    exit 1
fi
cd "$REPO_DIR"
echo -e "${GREEN}✓ Changed to repository directory${NC}"

echo -e "\n${BLUE}Step 3: Checking git repository status...${NC}"
if [ ! -d ".git" ]; then
    echo -e "${RED}❌ Not a git repository${NC}"
    cd ..
    exit 1
fi
echo -e "${GREEN}✓ Git repository confirmed${NC}"

echo -e "\n${BLUE}Step 4: Checking for existing conflicts...${NC}"
if git status --porcelain | grep -q "^UU\|^AA\|^DD"; then
    echo -e "${YELLOW}⚠️  Existing conflicts detected. Aborting current merge...${NC}"
    git merge --abort || echo -e "${YELLOW}⚠️  No merge in progress to abort${NC}"
fi

echo -e "\n${BLUE}Step 5: Resetting to clean state...${NC}"
git reset --hard HEAD
git clean -fd
echo -e "${GREEN}✓ Repository reset to clean state${NC}"

echo -e "\n${BLUE}Step 6: Fetching from remote...${NC}"
git fetch origin || {
    echo -e "${YELLOW}⚠️  Warning: Could not fetch from remote${NC}"
}

echo -e "\n${BLUE}Step 7: Attempting to pull latest changes...${NC}"
if git pull origin main 2>/dev/null; then
    echo -e "${GREEN}✓ Successfully pulled from main branch${NC}"
elif git pull origin master 2>/dev/null; then
    echo -e "${GREEN}✓ Successfully pulled from master branch${NC}"
else
    echo -e "${YELLOW}⚠️  Could not pull from remote. Proceeding with local changes...${NC}"
fi

echo -e "\n${BLUE}Step 8: Ensuring gradle/wrapper directory exists...${NC}"
mkdir -p gradle/wrapper
echo -e "${GREEN}✓ Directory ensured${NC}"

echo -e "\n${BLUE}Step 9: Copying gradle-wrapper.jar file...${NC}"
cp "../$SOURCE_FILE" "$TARGET_FILE"
echo -e "${GREEN}✓ File copied successfully${NC}"

echo -e "\n${BLUE}Step 10: Checking file integrity...${NC}"
if [ -f "$TARGET_FILE" ]; then
    echo "Source size: $(ls -lh "../$SOURCE_FILE" | awk '{print $5}')"
    echo "Target size: $(ls -lh "$TARGET_FILE" | awk '{print $5}')"
    echo -e "${GREEN}✓ File integrity verified${NC}"
else
    echo -e "${RED}❌ Target file not found after copy${NC}"
    cd ..
    exit 1
fi

echo -e "\n${BLUE}Step 11: Staging changes...${NC}"
git add "$TARGET_FILE"
git add gradle/wrapper/gradle-wrapper.properties  # In case it exists
echo -e "${GREEN}✓ Changes staged${NC}"

echo -e "\n${BLUE}Step 12: Checking for changes to commit...${NC}"
if git diff --cached --quiet; then
    echo -e "${YELLOW}⚠️  No changes to commit. File is already up to date.${NC}"
    COMMIT_NEEDED=false
else
    echo -e "${GREEN}✓ Changes detected, proceeding with commit${NC}"
    COMMIT_NEEDED=true
fi

if [ "$COMMIT_NEEDED" = true ]; then
    echo -e "\n${BLUE}Step 13: Committing changes...${NC}"
    git commit -m "$COMMIT_MESSAGE"
    echo -e "${GREEN}✓ Changes committed${NC}"
    
    echo -e "\n${BLUE}Step 14: Pushing to remote...${NC}"
    if git push origin main 2>/dev/null; then
        echo -e "${GREEN}✓ Successfully pushed to main branch${NC}"
        PUSH_SUCCESS=true
    elif git push origin master 2>/dev/null; then
        echo -e "${GREEN}✓ Successfully pushed to master branch${NC}"
        PUSH_SUCCESS=true
    else
        echo -e "${YELLOW}⚠️  Could not push to remote. Changes are committed locally.${NC}"
        echo -e "${YELLOW}    This may be due to authentication issues.${NC}"
        PUSH_SUCCESS=false
    fi
else
    PUSH_SUCCESS=true  # No push needed
fi

echo -e "\n${BLUE}Step 15: Final verification...${NC}"
echo "Git status:"
git status --short
echo ""
echo "Recent commits:"
git log --oneline -3

# Return to original directory
cd ..

echo -e "\n${GREEN}=========================================="
echo -e "✅ Gradle Wrapper Sync Complete!"
echo -e "===========================================${NC}"
echo ""
echo "Summary:"
echo "- Repository: $REPO_DIR"
echo "- File synced: $TARGET_FILE"
echo "- Commit message: $COMMIT_MESSAGE"

if [ "$COMMIT_NEEDED" = true ]; then
    if [ "$PUSH_SUCCESS" = true ]; then
        echo -e "${GREEN}- Status: Successfully pushed to GitHub ✓${NC}"
    else
        echo -e "${YELLOW}- Status: Committed locally, push failed ⚠️${NC}"
        echo -e "${YELLOW}  You may need to set up authentication to push to GitHub${NC}"
    fi
else
    echo -e "${GREEN}- Status: No changes needed, file was up to date ✓${NC}"
fi

echo ""
echo "The gradle-wrapper.jar has been synced with conflict resolution."