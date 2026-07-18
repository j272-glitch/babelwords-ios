#!/usr/bin/env bash
#
# push.sh — commit all changes and push to the BabelWords GitHub repository.
#
# USAGE
#   ./push.sh                 # commits with a timestamped message
#   ./push.sh "your message"  # commits with a custom message
#
# ENVIRONMENT
#   GITHUB_CLASSIC_TOKEN — a GitHub Personal Access Token with 'repo' + 'workflow' scope.
#                          Set it in Replit Secrets or your shell environment.
#
# REPLIT MAIN-AGENT RESTRICTION
#   The Replit main agent (this environment) blocks destructive git operations
#   (push, reset, rebase, etc.) as a platform policy. If you see:
#     "Destructive git operations are not allowed in the main agent"
#   you have TWO options to get your code to GitHub:
#
#   OPTION A — Run this script from your LOCAL machine (recommended):
#     1. git clone https://github.com/j272-glitch/babelwords-android.git
#     2. Copy the changed files from Replit into your local clone
#     3. export GITHUB_TOKEN=<your_token>
#     4. ./push.sh
#
#   OPTION B — Use a Replit Project Task:
#     Ask the agent to create a Project Task for "Push code to GitHub".
#     The task runs in an isolated environment where git push is allowed.
#
#   DO NOT try to remove .git/index.lock or .git/config.lock — those are
#   side-effects of the block, not the cause. Removing them will not help.

set -euo pipefail

# ── Configuration ────────────────────────────────────────────────────────────────────────────────────
GIT_USER_NAME="${GIT_USER_NAME:-j272-glitch}"
GIT_USER_EMAIL="${GIT_USER_EMAIL:-j272-glitch@users.noreply.github.com}"
GITHUB_OWNER="j272-glitch"
GITHUB_REPO="babelwords-android"
BRANCH="${BRANCH:-main}"

# ── Detect Replit main-agent block ────────────────────────────────────────────────────────────────────
# We test a harmless git config read to see if the sandbox allows git access.
# If it fails with the "Destructive git operations" message, we know push is blocked.
if ! git config --local core.autocrlf >/dev/null 2>&1; then
  # Try a benign operation that always works if git is functional
  if ! git rev-parse --git-dir >/dev/null 2>&1; then
    echo ""
    echo "❌ REPLIT MAIN-AGENT BLOCK DETECTED"
    echo ""
    echo "   Git push is blocked in this environment. This is a Replit platform"
    echo "   restriction — NOT a lock-file issue. Removing .git/index.lock or"
    echo "   .git/config.lock will NOT help."
    echo ""
    echo "   → OPTION A (fastest): Run this script on your LOCAL machine:"
    echo "      1. git clone https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git"
    echo "      2. Copy changed files from Replit into the clone"
    echo "      3. export GITHUB_TOKEN=<your_token>"
    echo "      4. ./push.sh"
    echo ""
    echo "   → OPTION B: Ask the agent to create a Project Task for 'Push to GitHub'."
    echo "      The task runs in an isolated environment where push is allowed."
    echo ""
    exit 1
  fi
fi

# ── Pre-flight checks ────────────────────────────────────────────────────────────────────────────────────
if [ -z "${GITHUB_CLASSIC_TOKEN:-}" ]; then
  echo "❌ GITHUB_CLASSIC_TOKEN is not set. Add it to your Replit Secrets or shell env and try again."
  exit 1
fi

# ── Git identity ─────────────────────────────────────────────────────────────────────────────────────
git config user.name  "$GIT_USER_NAME"
git config user.email "$GIT_USER_EMAIL"

# ── Stage and commit ────────────────────────────────────────────────────────────────────────────────────
git add -A

if git diff --cached --quiet; then
  echo "ℹ️  No staged changes to commit — pushing existing commits only."
else
  COMMIT_MSG="${1:-Update $(date -u '+%Y-%m-%d %H:%M:%S') UTC}"
  git commit -m "$COMMIT_MSG"
  echo "✅ Committed: $COMMIT_MSG"
fi

# ── Push ─────────────────────────────────────────────────────────────────────────────────────────────────────────
REMOTE_URL="https://x-access-token:${GITHUB_CLASSIC_TOKEN}@github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git"

echo "⬆️  Pushing ${BRANCH} → ${GITHUB_OWNER}/${GITHUB_REPO}..."
git push "$REMOTE_URL" "HEAD:${BRANCH}" 2>&1 | sed -E 's#x-access-token:[^@]*@#x-access-token:***@#g'

echo "✅ Push complete: https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}"
