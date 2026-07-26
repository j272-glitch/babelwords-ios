#!/bin/bash
set -e

# Interactively prompts for the App Store Connect API key details and creates
# app_store_connect_api_key.json for fastlane match/use on the cloud Mac.
# This avoids the need to set environment variables.

cd "$(dirname "$0")"

echo "==> App Store Connect API key setup"

echo -n "Enter Key ID (e.g., T2S67D64LB): "
read KEY_ID

echo -n "Enter Issuer ID (UUID): "
read ISSUER_ID

echo -n "Paste the base64-encoded .p8 key: "
read -s B64_KEY

echo
echo

if [ -z "$KEY_ID" ] || [ -z "$ISSUER_ID" ] || [ -z "$B64_KEY" ]; then
  echo "Error: All three values are required."
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
