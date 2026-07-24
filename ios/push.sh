#!/bin/bash
set -e

# Automatic push script for the BabelWords iOS project.
# Usage: ./push.sh "Your commit message"
# If no message is provided, it uses a default message with a timestamp.

cd "$(dirname "$0")"

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
# Use a credential helper so the token is not exposed in the remote URL.
git config credential.helper '!/bin/sh -c "echo username=j272-glitch; echo password=$GITHUB_BW_TOKEN"'
git push origin main
git config --unset credential.helper

echo "==> Push complete"
