# Android Signing Secrets Setup

Based on the production `release.keystore` with enhanced security (August 22, 2025), here are the exact secret values to add to your environment:

## Secret Values

### ANDROID_KEYSTORE_BASE64
*Copy the entire content from `keystore.base64.txt` file (starts with MIIK5AIBAz...)*

### ANDROID_KEYSTORE_PASSWORD
```
gtlingua2025secure
```

### ANDROID_KEY_ALIAS
```
linguagt-release-key
```

### ANDROID_KEY_PASSWORD
```
gtlingua2025secure
```

## Setup Instructions

1. Copy each value above (without the triple backticks)
2. Add them as secrets in your environment
3. Use these exact names for the secret keys
4. Your build workflows will automatically use these for app signing

## Keystore Information

- **Algorithm**: RSA 2048-bit with SHA384withRSA
- **Validity**: 68+ years (25,000 days until 2093)
- **Certificate**: Self-signed for LinguaGT app
- **Organization**: GTLingua Development, San Francisco, CA
- **SHA-256 Fingerprint**: `38:5B:F1:BD:AF:F9:57:B9:62:77:C8:19:3F:02:80:F8:B0:3A:16:DA:7B:98:10:BC:90:2F:CC:45:92:D3:95:D6`

## Security Notes

- Keep these secrets secure and never commit them to code
- The keystore is required for all future app updates
- Use the same keystore for consistent app signing across versions