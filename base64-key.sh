#!/bin/bash
set -e

# Converts an App Store Connect .p8 private key file to a base64 string.
# Usage: ./base64-key.sh [path/to/AuthKey_XXXXXXXXXX.p8]
# If no path is provided, it looks for the first .p8 file in attached_assets/.

cd "$(dirname "$0")"

if [ -n "$1" ]; then
  KEY_FILE="$1"
else
  KEY_FILE=$(find attached_assets -name "*.p8" -type f 2>/dev/null | head -n 1)
fi

if [ -z "$KEY_FILE" ] || [ ! -f "$KEY_FILE" ]; then
  echo "Error: No .p8 file found."
  echo "Usage: ./base64-key.sh path/to/AuthKey_XXXXXXXXXX.p8"
  exit 1
fi

echo "==> Converting $(basename "$KEY_FILE") to base64"
base64 -w 0 "$KEY_FILE"
echo
