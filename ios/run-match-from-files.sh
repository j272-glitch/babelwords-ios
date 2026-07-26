#!/bin/bash
set -e

# Automates running `fastlane match` on the cloud Mac.
# Reads MATCH_GIT_BASIC_AUTHORIZATION and MATCH_PASSWORD from files,
# derives the match repo URL from the git origin, and runs sync_signing.
#
# Create these files on the cloud Mac using TextEdit (plain text, NOT .rtf):
#   match_auth.txt     -> contains MATCH_GIT_BASIC_AUTHORIZATION
#   match_password.txt -> contains MATCH_PASSWORD
#
# Then run:
#   ./run-match-from-files.sh [INPUT_DIR]
#
# If no INPUT_DIR is provided, it defaults to /tmp.

INPUT_DIR="${1:-/tmp}"

find_file() {
  local dir="$1"
  shift
  for name in "$@"; do
    local path="$dir/$name"
    if [ -f "$path" ]; then
      echo "$path"
      return 0
    fi
  done
  return 1
}

cd "$(dirname "$0")"

AUTH_FILE=$(find_file "$INPUT_DIR" "match_auth.txt" "match-auth.txt" "match-auth") || {
  echo "Error: Missing match auth file. Create one of: $INPUT_DIR/match_auth.txt, $INPUT_DIR/match-auth.txt, or $INPUT_DIR/match-auth"
  exit 1
}
PASSWORD_FILE=$(find_file "$INPUT_DIR" "match_password.txt" "match-password.txt" "match-password") || {
  echo "Error: Missing match password file. Create one of: $INPUT_DIR/match_password.txt, $INPUT_DIR/match-password.txt, or $INPUT_DIR/match-password"
  exit 1
}

MATCH_GIT_BASIC_AUTHORIZATION=$(cat "$AUTH_FILE" | tr -d '\n')
MATCH_PASSWORD=$(cat "$PASSWORD_FILE" | tr -d '\n')

if [ -z "$MATCH_GIT_BASIC_AUTHORIZATION" ] || [ -z "$MATCH_PASSWORD" ]; then
  echo "Error: One or more input files are empty."
  exit 1
fi

# Derive the match repo URL from the current git origin.
# Example: git@github.com:user/babelwords-ios.git -> git@github.com:user/babelwords-match.git
# Example: https://github.com/user/babelwords-ios.git -> https://github.com/user/babelwords-match.git
ORIGIN_URL=$(git remote get-url origin 2>/dev/null || echo "")
if [ -z "$ORIGIN_URL" ]; then
  echo "Error: Could not determine git origin URL. Set FASTLANE_MATCH_REPO_URL manually."
  exit 1
fi

MATCH_REPO_URL=$(echo "$ORIGIN_URL" | sed 's|babelwords-ios\.git|babelwords-match.git|')

echo "==> Using match repo: $MATCH_REPO_URL"

export MATCH_GIT_BASIC_AUTHORIZATION
export MATCH_PASSWORD
export FASTLANE_MATCH_REPO_URL="$MATCH_REPO_URL"

bundle exec fastlane sync_signing
