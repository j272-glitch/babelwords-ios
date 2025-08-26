#!/bin/bash

# Script to sync gradle-wrapper .jar file with lingualink-android2 git repository
# This script will pull latest changes, copy the wrapper, and push back to git

set -e  # Exit on any error

echo "=========================================="
echo "Gradle Wrapper Sync Script"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
REPO_DIR="lingualink-android2"
SOURCE_WRAPPER="gradle/wrapper/gradle-wrapper.jar"
TARGET_WRAPPER_DIR="${REPO_DIR}/gradle/wrapper"
COMMIT_MESSAGE="Update gradle-wrapper to version 8.9 for TestRigor compatibility"

echo -e "${BLUE}Step 1: Checking source gradle-wrapper file...${NC}"
if [ ! -f "$SOURCE_WRAPPER" ]; then
    echo -e "${RED}❌ Source file not found: $SOURCE_WRAPPER${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Source file found: $SOURCE_WRAPPER${NC}"

echo -e "\n${BLUE}Step 2: Checking lingualink-android2 repository...${NC}"
if [ ! -d "$REPO_DIR" ]; then
    echo -e "${RED}❌ Repository directory not found: $REPO_DIR${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Repository directory found: $REPO_DIR${NC}"

# Change to repository directory
cd "$REPO_DIR"

echo -e "\n${BLUE}Step 3: Checking git repository status...${NC}"
if [ ! -d ".git" ]; then
    echo -e "${RED}❌ Not a git repository: $REPO_DIR${NC}"
    cd ..
    exit 1
fi
echo -e "${GREEN}✓ Git repository confirmed${NC}"

echo -e "\n${BLUE}Step 4: Fetching latest changes from remote...${NC}"
git fetch origin || {
    echo -e "${YELLOW}⚠️  Warning: Could not fetch from remote. Continuing anyway...${NC}"
}

echo -e "\n${BLUE}Step 5: Pulling latest changes...${NC}"
git pull origin main || git pull origin master || {
    echo -e "${YELLOW}⚠️  Warning: Could not pull from remote. Continuing with local changes...${NC}"
}

echo -e "\n${BLUE}Step 6: Creating gradle/wrapper directory if needed...${NC}"
mkdir -p gradle/wrapper
echo -e "${GREEN}✓ Directory ensured: gradle/wrapper${NC}"

echo -e "\n${BLUE}Step 7: Copying gradle-wrapper file...${NC}"
cp "../$SOURCE_WRAPPER" gradle/wrapper/gradle-wrapper.jar
echo -e "${GREEN}✓ Copied: $SOURCE_WRAPPER → gradle/wrapper/gradle-wrapper.jar${NC}"

echo -e "\n${BLUE}Step 8: Checking file sizes...${NC}"
echo "Source file size: $(ls -lh "../$SOURCE_WRAPPER" | awk '{print $5}')"
echo "Target file size: $(ls -lh gradle/wrapper/gradle-wrapper.jar | awk '{print $5}')"

echo -e "\n${BLUE}Step 9: Checking git status...${NC}"
git status --porcelain

echo -e "\n${BLUE}Step 10: Adding files to git...${NC}"
git add gradle/wrapper/gradle-wrapper.jar
git add gradle/wrapper/gradle-wrapper.properties  # In case it was updated
echo -e "${GREEN}✓ Files added to git staging${NC}"

echo -e "\n${BLUE}Step 11: Checking for changes to commit...${NC}"
if git diff --cached --quiet; then
    echo -e "${YELLOW}⚠️  No changes to commit. Files are already up to date.${NC}"
else
    echo -e "${GREEN}✓ Changes detected, proceeding with commit...${NC}"
    
    echo -e "\n${BLUE}Step 12: Committing changes...${NC}"
    git commit -m "$COMMIT_MESSAGE"
    echo -e "${GREEN}✓ Changes committed successfully${NC}"
    
    echo -e "\n${BLUE}Step 13: Pushing to remote repository...${NC}"
    git push origin main || git push origin master || {
        echo -e "${YELLOW}⚠️  Warning: Could not push to remote. Changes are committed locally.${NC}"
        echo -e "${YELLOW}    You may need to push manually later or check remote settings.${NC}"
    }
    echo -e "${GREEN}✓ Push completed (or attempted)${NC}"
fi

echo -e "\n${BLUE}Step 14: Final verification...${NC}"
echo "Repository status:"
git status --porcelain
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
echo "- Source: $SOURCE_WRAPPER"
echo "- Target: $TARGET_WRAPPER_DIR"
echo "- Commit: $COMMIT_MESSAGE"
echo ""
echo "The gradle-wrapper.jar has been synced to the lingualink-android2 repository."