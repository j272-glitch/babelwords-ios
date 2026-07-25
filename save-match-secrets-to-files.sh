#!/bin/bash
set -e

# Writes the fastlane match secrets to local files so they can be copied into
# Replit Secrets without selecting text from the shell.
#
# Usage:
#   MATCH_PASSWORD=yourpassword ./save-match-secrets-to-files.sh
#
# Files created (gitignored):
#   .secrets/MATCH_GIT_BASIC_AUTHORIZATION
#   .secrets/MATCH_PASSWORD

cd "$(dirname "$0")"

mkdir -p .secrets

if [ -z "${GITHUB_PERSONAL_ACCESS_TOKEN:-}" ]; then
  echo "Error: GITHUB_PERSONAL_ACCESS_TOKEN environment variable is not set."
  echo "Add it to Replit Secrets or export it before running this script."
  exit 1
fi

echo "==> Generating MATCH_GIT_BASIC_AUTHORIZATION"
./base64-match-auth.sh | tail -n 1 > .secrets/MATCH_GIT_BASIC_AUTHORIZATION

echo "==> Writing MATCH_GIT_BASIC_AUTHORIZATION to .secrets/MATCH_GIT_BASIC_AUTHORIZATION"

if [ -z "${MATCH_PASSWORD:-}" ]; then
  read -s -p "Enter MATCH_PASSWORD: " MATCH_PASSWORD
  echo
fi

if [ -z "$MATCH_PASSWORD" ]; then
  echo "Error: MATCH_PASSWORD is required."
  exit 1
fi

echo "==> Writing MATCH_PASSWORD to .secrets/MATCH_PASSWORD"
echo "$MATCH_PASSWORD" > .secrets/MATCH_PASSWORD

echo "==> Done. Open these files in the Replit editor and copy each value to Tools → Secrets:"
echo "  .secrets/MATCH_GIT_BASIC_AUTHORIZATION  -> secret name: MATCH_GIT_BASIC_AUTHORIZATION"
echo "  .secrets/MATCH_PASSWORD                  -> secret name: MATCH_PASSWORD"
echo ""
echo "These files are listed in .gitignore and will not be committed."
