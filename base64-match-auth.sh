#!/bin/bash
set -e

# Converts a GitHub username + Personal Access Token into the base64 string
# required for fastlane match's MATCH_GIT_BASIC_AUTHORIZATION secret.
#
# Usage:
#   GITHUB_PERSONAL_ACCESS_TOKEN=ghp_xxx ./base64-match-auth.sh
#   GITHUB_PERSONAL_ACCESS_TOKEN=ghp_xxx ./base64-match-auth.sh [github-username]
#
# If no username is provided, it defaults to j272-glitch (the repo owner).

GITHUB_USERNAME="${1:-j272-glitch}"
TOKEN="${GITHUB_PERSONAL_ACCESS_TOKEN:-}"

if [ -z "$TOKEN" ]; then
  echo "Error: GITHUB_PERSONAL_ACCESS_TOKEN environment variable is not set."
  echo "Add it to Replit Secrets or export it before running this script."
  exit 1
fi

echo "==> Generating MATCH_GIT_BASIC_AUTHORIZATION for $GITHUB_USERNAME"
echo -n "$GITHUB_USERNAME:$TOKEN" | base64 -w 0
echo
