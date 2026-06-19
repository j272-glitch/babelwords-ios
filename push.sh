#!/usr/bin/env bash
#
# push.sh — commit all changes and push to the BabelWords GitHub repository.
#
# Usage:
#   ./push.sh                 # commits with a timestamped message
#   ./push.sh "your message"  # commits with a custom message
#
# Requires the GITHUB_TOKEN secret (a GitHub Personal Access Token with 'repo' scope)
# to be set in the environment. It is never written to disk or git config.

set -euo pipefail

# ── Configuration ────────────────────────────────────────────────────────────
GIT_USER_NAME="${GIT_USER_NAME:-j272-glitch}"
GIT_USER_EMAIL="${GIT_USER_EMAIL:-j272-glitch@users.noreply.github.com}"
GITHUB_OWNER="j272-glitch"
GITHUB_REPO="babelwords-android"
BRANCH="${BRANCH:-main}"

# ── Pre-flight checks ────────────────────────────────────────────────────────
if [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "❌ GITHUB_TOKEN is not set. Add it to your Replit Secrets and try again."
  exit 1
fi

# ── Git identity (prevents 'unable to auto detect email address') ────────────
git config user.name  "$GIT_USER_NAME"
git config user.email "$GIT_USER_EMAIL"

# ── Stage and commit ─────────────────────────────────────────────────────────
git add -A

if git diff --cached --quiet; then
  echo "ℹ️  No staged changes to commit — pushing existing commits only."
else
  COMMIT_MSG="${1:-Update $(date -u '+%Y-%m-%d %H:%M:%S') UTC}"
  git commit -m "$COMMIT_MSG"
  echo "✅ Committed: $COMMIT_MSG"
fi

# ── Push via a one-time authenticated URL (token not stored in .git/config) ──
REMOTE_URL="https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git"

echo "⬆️  Pushing ${BRANCH} → ${GITHUB_OWNER}/${GITHUB_REPO}..."
git push "$REMOTE_URL" "HEAD:${BRANCH}" 2>&1 | sed -E 's#x-access-token:[^@]*@#x-access-token:***@#g'

echo "✅ Push complete: https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}"
