#!/bin/bash
set -e

# Automatic push script for the BabelWords iOS project.
# Usage: ./push.sh "Your commit message"
# If no message is provided, it uses a default message with a timestamp.

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

COMMIT_MESSAGE="${1:-Update BabelWords iOS project $(date +%Y-%m-%d-%H:%M)}"

if [ -z "${GITHUB_BW_TOKEN:-}" ]; then
  echo "Error: GITHUB_BW_TOKEN environment variable is not set."
  echo "Add it to your Replit Secrets or export it before running this script."
  exit 1
fi

echo "==> Adding all changes"
git add -A

echo "==> Committing with message: $COMMIT_MESSAGE"
if git diff --cached --quiet; then
  echo "No changes to commit."
  exit 0
fi

git commit -m "$COMMIT_MESSAGE"

echo "==> Pushing to origin main"
git push https://j272-glitch:${GITHUB_BW_TOKEN}@github.com/j272-glitch/babelwords-ios.git main

echo "==> Push complete"
