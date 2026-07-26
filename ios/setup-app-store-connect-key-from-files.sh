#!/bin/bash
set -e

# Creates app_store_connect_api_key.json from three text files.
# This avoids interactive terminal prompts which can fail on mobile/NoMachine.
#
# Create these files on the cloud Mac using TextEdit (any folder you like):
#   key_id.txt          -> contains the Key ID
#   issuer_id.txt       -> contains the Issuer ID
#   api_key_base64.txt  -> contains the base64-encoded .p8 key
#
# Then run:
#   ./setup-app-store-connect-key-from-files.sh [INPUT_DIR]
#
# If no INPUT_DIR is provided, it defaults to /tmp.

INPUT_DIR="${1:-/tmp}"

cd "$(dirname "$0")"

KEY_ID_FILE="$INPUT_DIR/key_id.txt"
ISSUER_ID_FILE="$INPUT_DIR/issuer_id.txt"
B64_FILE="$INPUT_DIR/api_key_base64.txt"

if [ ! -f "$KEY_ID_FILE" ] || [ ! -f "$ISSUER_ID_FILE" ] || [ ! -f "$B64_FILE" ]; then
  echo "Error: Missing input files. Create these three files:"
  echo "  $KEY_ID_FILE"
  echo "  $ISSUER_ID_FILE"
  echo "  $B64_FILE"
  exit 1
fi

KEY_ID=$(cat "$KEY_ID_FILE" | tr -d '\n')
ISSUER_ID=$(cat "$ISSUER_ID_FILE" | tr -d '\n')
B64_KEY=$(cat "$B64_FILE" | tr -d '\n')

if [ -z "$KEY_ID" ] || [ -z "$ISSUER_ID" ] || [ -z "$B64_KEY" ]; then
  echo "Error: One or more input files are empty."
  exit 1
fi

echo "==> Decoding private key"
echo "$B64_KEY" | base64 -d -o app_store_connect_api_key.p8

echo "==> Creating app_store_connect_api_key.json"
key=$(cat app_store_connect_api_key.p8 | sed 's/$/\\n/' | tr -d '\n')
cat > app_store_connect_api_key.json <<EOF
{
  "key_id": "$KEY_ID",
  "issuer_id": "$ISSUER_ID",
  "key": "$key",
  "duration": 1200,
  "in_house": false
}
EOF

echo "==> Done. app_store_connect_api_key.json is ready for fastlane match."
