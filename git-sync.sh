
#!/bin/bash

# Git Sync Script - Pull, Commit, and Push with Conflict Handling
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

# Step 2: Check for unmerged files (conflicts)
print_info "Step 2: Checking for merge conflicts..."
if git status --porcelain | grep -q "^UU\|^AA\|^DD\|^AU\|^UA\|^DU\|^UD"; then
    print_error "Unmerged files detected! Attempting to resolve..."
    
    # Abort any ongoing merge
    print_warning "Aborting current merge operation..."
    git merge --abort 2>/dev/null || git rebase --abort 2>/dev/null || true
    
    # Reset to clean state
    print_warning "Resetting to clean state..."
    git reset --hard HEAD
    git clean -fd
    
    print_status "Conflicts cleared, repository reset"
else
    print_status "No merge conflicts detected"
fi

# Step 3: Stash uncommitted changes (if any)
print_info "Step 3: Checking for uncommitted changes..."
if ! git diff --quiet || ! git diff --cached --quiet; then
    print_warning "Stashing uncommitted changes..."
    git stash push -m "Auto-stash before sync $(date '+%Y-%m-%d %H:%M:%S')" --include-untracked
    STASHED=true
    print_status "Changes stashed"
else
    STASHED=false
    print_status "No uncommitted changes"
fi

# Step 4: Pull latest changes
print_info "Step 4: Pulling latest changes from origin/$BRANCH..."
if git pull origin "$BRANCH" --rebase 2>/dev/null; then
    print_status "Pull successful (rebase)"
elif git pull origin "$BRANCH" 2>/dev/null; then
    print_status "Pull successful (merge)"
else
    print_warning "Pull failed, trying fetch and reset..."
    git fetch origin "$BRANCH"
    
    # Check if we're behind
    LOCAL=$(git rev-parse @)
    REMOTE=$(git rev-parse "origin/$BRANCH" 2>/dev/null || echo "")
    
    if [ -n "$REMOTE" ] && [ "$LOCAL" != "$REMOTE" ]; then
        print_warning "Local branch differs from remote. Resetting to remote..."
        git reset --hard "origin/$BRANCH"
        print_status "Reset to remote branch successful"
    else
        print_status "Already up to date"
    fi
fi

# Step 5: Restore stashed changes (if any)
if [ "$STASHED" = true ]; then
    print_info "Step 5: Restoring stashed changes..."
    if git stash pop 2>/dev/null; then
        print_status "Stashed changes restored"
    else
        print_warning "Stash pop had conflicts - applying with theirs strategy"
        git checkout --theirs . 2>/dev/null || true
        git stash drop 2>/dev/null || true
    fi
fi

# Step 6: Show current status
print_info "Step 6: Current repository status..."
git status --short

# Step 7: Add all changes
print_info "Step 7: Staging all changes..."
git add -A
print_status "All changes staged"

# Step 8: Check if there are changes to commit
if git diff --cached --quiet; then
    print_warning "No changes to commit"
    echo ""
    echo "=========================================="
    echo "   Sync Complete (No Changes)"
    echo "=========================================="
    exit 0
fi

# Step 9: Show what will be committed
print_info "Files to be committed:"
git diff --cached --name-status

# Step 10: Commit changes
print_info "Step 8: Committing changes..."
echo "Commit message: $COMMIT_MESSAGE"
git commit -m "$COMMIT_MESSAGE"
print_status "Changes committed"

# Step 11: Push to remote
print_info "Step 9: Pushing to origin/$BRANCH..."
if git push origin "$BRANCH"; then
    print_status "Push successful!"
else
    print_warning "Push failed, trying with force-with-lease (safer than force)..."
    if git push origin "$BRANCH" --force-with-lease; then
        print_warning "Force push completed"
    else
        print_error "Push failed even with force-with-lease"
        print_info "You may need to manually resolve this"
        exit 1
    fi
fi

# Step 12: Final summary
echo ""
echo "=========================================="
echo "   Sync Complete!"
echo "=========================================="
echo ""
echo "Branch: $BRANCH"
echo "Commit: $(git log -1 --oneline)"
echo ""
print_status "Repository synchronized successfully"
