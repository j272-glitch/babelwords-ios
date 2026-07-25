#!/bin/bash
set -e

# Saves MATCH_GIT_BASIC_AUTHORIZATION and MATCH_PASSWORD to GitHub Secrets
# for the BabelWords iOS repo using the GitHub CLI (gh).
#
# Usage:
#   GITHUB_PERSONAL_ACCESS_TOKEN=ghp_xxx MATCH_PASSWORD=yourpassword ./save-match-secrets-to-github.sh
#
# Requirements:
#   - GITHUB_PERSONAL_ACCESS_TOKEN set in environment (or Replit Secrets)
#   - MATCH_PASSWORD set in environment, or enter it when prompted
#   - gh (GitHub CLI) installed and accessible

cd "$(dirname "$0")"

export GH_TOKEN="${GITHUB_PERSONAL_ACCESS_TOKEN:-${GH_TOKEN:-}}"

if [ -z "$GH_TOKEN" ]; then
  echo "Error: GITHUB_PERSONAL_ACCESS_TOKEN or GH_TOKEN environment variable is not set."
  echo "Add it to Replit Secrets or export it before running this script."
  exit 1
fi

if ! command -v gh &> /dev/null; then
  echo "Error: GitHub CLI (gh) is not installed."
  exit 1
fi

echo "==> Generating MATCH_GIT_BASIC_AUTHORIZATION"
AUTH_B64=$(./base64-match-auth.sh | tail -n 1)

if [ -z "$AUTH_B64" ]; then
  echo "Error: Failed to generate MATCH_GIT_BASIC_AUTHORIZATION."
  exit 1
fi

if [ -z "${MATCH_PASSWORD:-}" ]; then
  read -s -p "Enter MATCH_PASSWORD: " MATCH_PASSWORD
  echo
fi

if [ -z "$MATCH_PASSWORD" ]; then
  echo "Error: MATCH_PASSWORD is required."
  exit 1
fi

REPO="j272-glitch/babelwords-ios"

echo "==> Saving MATCH_GIT_BASIC_AUTHORIZATION to GitHub Secrets"
echo "$AUTH_B64" | gh secret set MATCH_GIT_BASIC_AUTHORIZATION -R "$REPO"

echo "==> Saving MATCH_PASSWORD to GitHub Secrets"
echo "$MATCH_PASSWORD" | gh secret set MATCH_PASSWORD -R "$REPO"

echo "==> Done. Both match secrets are saved in GitHub for $REPO."
