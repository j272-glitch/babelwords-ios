#!/bin/bash
# Encode GCP service account key for GitHub Secrets
# Usage: ./scripts/encode_gcp_key.sh [path-to-json-file]

set -e

JSON_FILE="${1:-attached_assets/babelwords-android-cc951602ae42_1783720944824.json}"

if [ ! -f "$JSON_FILE" ]; then
    echo "Error: File not found: $JSON_FILE"
    echo ""
    echo "Available JSON files in attached_assets/:"
    ls -1 attached_assets/*.json 2>/dev/null || echo "  (none found)"
    echo ""
    echo "Usage: $0 [path-to-service-account.json]"
    exit 1
fi

echo "=== GCP Service Account Key Encoder ==="
echo ""
echo "File: $JSON_FILE"
echo ""
echo "Base64-encoded output (copy this entire block):"
echo "---BEGIN GCP_SA_KEY---"
base64 -w 0 "$JSON_FILE"
echo ""
echo "---END GCP_SA_KEY---"
echo ""
echo "Next steps:"
echo "1. Copy the entire line between ---BEGIN--- and ---END--- (it's one long line)"
echo "2. Go to https://github.com/j272-glitch/babelwords-android/settings/secrets/actions"
echo "3. Click 'New repository secret'"
echo "4. Name: GCP_SA_KEY"
echo "5. Value: paste the base64 string above"
echo "6. Add another secret:"
echo "   Name: GCP_PROJECT_ID"
echo "   Value: babelwords-android"
echo ""
echo "After adding to GitHub, delete the JSON file from this directory for security:"
echo "  rm $JSON_FILE"
