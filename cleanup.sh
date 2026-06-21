#!/usr/bin/env bash
#
# cleanup.sh — remove dead / leftover files from the BabelWords repo.
#
# These deletions can't be performed by the Replit Agent directly
# (destructive git operations are blocked in the agent), so run this
# yourself from the repo root:
#
#     bash cleanup.sh
#
# It only DELETES files. After it finishes, review with `git status`
# and then commit + push with:
#
#     ./push.sh "remove dead testrigor package + build cruft"
#
# Each item is independently guarded, so the script is safe to re-run.

set -euo pipefail

# Always operate from the directory this script lives in (repo root).
cd "$(dirname "$0")"

echo "============================================"
echo " BabelWords repository cleanup"
echo "============================================"

removed_any=0

# remove_path <path>
#   git rm -r if the path is tracked, plain rm -rf if it's only on disk,
#   and a no-op (with a note) if it doesn't exist.
remove_path() {
    local path="$1"
    # Tracked if the path itself is tracked OR any file beneath it is tracked
    # (git tracks files, not directories, so a dir tree needs the descendant check).
    if git ls-files --error-unmatch "$path" >/dev/null 2>&1 \
        || [ -n "$(git ls-files -- "$path")" ]; then
        git rm -r --quiet "$path"
        echo "  ✓ removed (git-tracked): $path"
        removed_any=1
    elif [ -e "$path" ]; then
        rm -rf "$path"
        echo "  ✓ removed (untracked):   $path"
        removed_any=1
    else
        echo "  – already gone:          $path"
    fi
}

echo ""
echo "1) Duplicate / stray GitHub Actions workflow files"
echo "   (old v23 workflow superseded by v1; sed temp file)"
remove_path ".github/workflows/android-sdk-update-v23.yml"
remove_path ".github/workflows/sedY0TzQ6"

echo ""
echo "2) Committed self-hosted-runner build logs (cruft)"
echo "   (left over from the old macOS runner)"
remove_path "Users"

echo ""
echo "3) Dead TestRigor-laden package: com.lingualink.linguagt"
echo "   (not referenced by the manifest or the live com.babelwords.app code)"
remove_path "android/app/src/main/java/com/lingualink/linguagt"

echo ""
echo "4) Old exposed signing keystores + plaintext passwords (retired)"
remove_path "release.keystore"
remove_path "my-release-key.jks"
remove_path "keystore-info.txt"
remove_path "android-secrets-setup.md"
remove_path "keystore.base64.txt"
remove_path "release-keystore-base64.txt"

echo ""
echo "5) New BabelWords keystore base64 (keystore-base64.txt)"
echo "   This file is the ONLY on-disk copy of your new signing key."
echo "   It must be untracked before pushing, or the key leaks to GitHub."
if [ -e "keystore-base64.txt" ]; then
    echo ""
    echo "   Before deleting it, confirm BOTH are done:"
    echo "     1. Pasted its contents into the GitHub secret ANDROID_KEYSTORE_BASE64"
    echo "     2. Saved a copy in a secure password manager / backup"
    printf "   Type 'yes' to remove keystore-base64.txt now: "
    read -r confirm_base64
    if [ "$confirm_base64" = "yes" ]; then
        remove_path "keystore-base64.txt"
    else
        echo "  ⚠ kept keystore-base64.txt — do NOT run ./push.sh until it is removed"
        echo "    (re-run this script once you've saved the value)."
    fi
fi

echo ""
echo "============================================"
if [ "$removed_any" -eq 1 ]; then
    echo " Cleanup complete."
    echo " Next:"
    echo "   git status                 # review what changed"
    echo "   ./push.sh \"remove dead testrigor package + build cruft\""
else
    echo " Nothing to remove — repo is already clean."
fi
echo "============================================"
