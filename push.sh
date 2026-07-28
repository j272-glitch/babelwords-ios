#!/bin/bash
set -e

# Automatic push script for the BabelWords iOS project.
# Usage: ./push.sh "Your commit message"
# If no message is provided, it uses a default message with a timestamp.
# Run this from the repository root (or any directory); the script will cd to the repo root automatically.

cd "$(dirname "$0")"

COMMIT_MESSAGE="${1:-Update BabelWords iOS project $(date +%Y-%m-%d-%H:%M)}"

if [ "$(git branch --show-current)" != "main" ]; then
  echo "Error: push.sh only pushes the main branch."
  exit 1
fi

if [ -z "${GITHUB_BW_TOKEN:-}" ]; then
  echo "Error: GITHUB_BW_TOKEN environment variable is not set."
  echo "Add it to your Replit Secrets or export it before running this script."
  exit 1
fi

echo "==> Adding all changes"
git add -A

if git diff --cached --name-only | grep -Eiq '(^|/)(.*(AuthKey|service-account|credentials|secret|key).*(p8|pem|json|plist|txt)|.*\.(p8|pem|key))$'; then
  echo "Error: refusing to commit a credential-looking file."
  git diff --cached --name-only
  git reset
  exit 1
fi

echo "==> Committing with message: $COMMIT_MESSAGE"
if git diff --cached --quiet; then
  echo "No new changes to commit."
else
  git commit -m "$COMMIT_MESSAGE"
fi

echo "==> Pushing to origin main"
# Use a credential helper so the token is not exposed in the remote URL.
git config credential.helper '!/bin/sh -c "echo username=j272-glitch; echo password=$GITHUB_BW_TOKEN"'
cleanup_credentials() {
  git config --unset credential.helper >/dev/null 2>&1 || true
}
trap cleanup_credentials EXIT

git fetch origin main --quiet
if ! git merge-base --is-ancestor origin/main HEAD; then
  echo "Error: local main is behind or diverged from origin/main."
  exit 1
fi

git push origin main

echo "==> Push complete"
