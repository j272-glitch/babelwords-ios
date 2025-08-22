# Android Signing Secrets Setup

Based on the newly generated `release.keystore` (August 22, 2025), here are the exact secret values to add to your environment:

## Secret Values

### ANDROID_KEYSTORE_BASE64
*Copy the entire content from `keystore.base64.txt` file (starts with MIIK5AIBAz...)*

### ANDROID_KEYSTORE_PASSWORD
```
gtlingua123
```

### ANDROID_KEY_ALIAS
```
your-key-alias
```

### ANDROID_KEY_PASSWORD
```
gtlingua123
```

## Setup Instructions

1. Copy each value above (without the triple backticks)
2. Add them as secrets in your environment
3. Use these exact names for the secret keys
4. Your build workflows will automatically use these for app signing

## Keystore Information

- **Algorithm**: RSA 2048-bit
- **Validity**: ~27 years (until 2053)
- **Certificate**: Self-signed for GTLingua app
- **Organization**: LinguaLink Development

## Security Notes

- Keep these secrets secure and never commit them to code
- The keystore is required for all future app updates
- Use the same keystore for consistent app signing across versions