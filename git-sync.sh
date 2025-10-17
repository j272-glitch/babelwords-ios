
#!/bin/bash

# Git Sync Script - Pull, Commit, and Push
# Usage: ./git-sync.sh [commit-message]

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() { echo -e "${GREEN}✅ $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }
print_error() { echo -e "${RED}❌ $1${NC}"; }
print_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }

COMMIT_MESSAGE="${1:-Update: $(date '+%Y-%m-%d %H:%M:%S')}"
BRANCH=$(git branch --show-current 2>/dev/null || echo "main")

echo "=========================================="
echo "   Git Sync Script"
echo "=========================================="
echo ""

# Step 1: Check Git status
print_info "Step 1: Checking Git status..."
if [ ! -d ".git" ]; then
    print_error "Not a Git repository!"
    exit 1
fi
print_status "Git repository confirmed"

# Step 2: Stash uncommitted changes (if any)
print_info "Step 2: Checking for uncommitted changes..."
if ! git diff --quiet || ! git diff --cached --quiet; then
    print_warning "Stashing uncommitted changes..."
    git stash push -m "Auto-stash before sync $(date '+%Y-%m-%d %H:%M:%S')"
    STASHED=true
    print_status "Changes stashed"
else
    STASHED=false
    print_status "No uncommitted changes"
fi

# Step 3: Pull latest changes
print_info "Step 3: Pulling latest changes from origin/$BRANCH..."
if git pull origin "$BRANCH" --allow-unrelated-histories; then
    print_status "Pull successful"
elif git pull origin "$BRANCH"; then
    print_status "Pull successful (standard)"
else
    print_warning "Pull failed, trying fetch and merge..."
    git fetch origin "$BRANCH"
    if git merge "origin/$BRANCH" --allow-unrelated-histories -m "Merge remote changes"; then
        print_status "Merge successful"
    else
        print_error "Merge failed - please resolve conflicts manually"
        exit 1
    fi
fi

# Step 4: Restore stashed changes (if any)
if [ "$STASHED" = true ]; then
    print_info "Step 4: Restoring stashed changes..."
    if git stash pop; then
        print_status "Stashed changes restored"
    else
        print_warning "Stash pop had conflicts - check 'git stash list'"
    fi
fi

# Step 5: Show current status
print_info "Step 5: Current repository status..."
git status --short

# Step 6: Add all changes
print_info "Step 6: Staging all changes..."
git add -A
print_status "All changes staged"

# Step 7: Check if there are changes to commit
if git diff --cached --quiet; then
    print_warning "No changes to commit"
    echo ""
    echo "=========================================="
    echo "   Sync Complete (No Changes)"
    echo "=========================================="
    exit 0
fi

# Step 8: Show what will be committed
print_info "Files to be committed:"
git diff --cached --name-status

# Step 9: Commit changes
print_info "Step 7: Committing changes..."
echo "Commit message: $COMMIT_MESSAGE"
git commit -m "$COMMIT_MESSAGE"
print_status "Changes committed"

# Step 10: Push to remote
print_info "Step 8: Pushing to origin/$BRANCH..."
if git push origin "$BRANCH"; then
    print_status "Push successful!"
else
    print_error "Push failed, trying with force..."
    read -p "Force push? This will overwrite remote. (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git push origin "$BRANCH" --force
        print_warning "Force push completed"
    else
        print_error "Push cancelled"
        exit 1
    fi
fi

# Step 11: Final summary
echo ""
echo "=========================================="
echo "   Sync Complete!"
echo "=========================================="
echo ""
echo "Branch: $BRANCH"
echo "Commit: $(git log -1 --oneline)"
echo ""
print_status "Repository synchronized successfully"
