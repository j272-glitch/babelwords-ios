# iOS Integration Guide - LinguaVibe

## Overview

This guide covers the iOS Swift integration for LinguaVibe, including:
- StoreKit 2 subscription management (iOS 15+)
- Legacy StoreKit support (iOS 13-14)
- JavaScript bridge for web app communication

## Files Structure

```
LinguaVibe/
├── Billing/
│   └── SubscriptionManager.swift    # StoreKit subscription handling
├── Bridge/
│   └── SubscriptionBridge.swift     # JS bridge for subscriptions
├── Ads/
│   ├── AdMobBridge.swift            # AdMob JS bridge
│   └── AdPreloadManager.swift       # Native ad preloading
└── MainViewController.swift         # Main WebView controller
```

## Subscription Integration

### 1. App Store Connect Setup

Create subscription products in App Store Connect:
- Product ID: `premium_monthly` - $4.99/month
- Product ID: `premium_yearly` - $49.99/year

### 2. Enable In-App Purchase Capability

In Xcode:
1. Select your target
2. Go to "Signing & Capabilities"
3. Click "+ Capability"
4. Add "In-App Purchase"

### 3. Initialize in MainViewController

```swift
class MainViewController: UIViewController {
    
    private var subscriptionBridge: SubscriptionBridge?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        // Initialize subscription bridge after WebView setup
        subscriptionBridge = SubscriptionBridge(webView: webView)
        
        // Initialize StoreKit
        if #available(iOS 15.0, *) {
            Task {
                await SubscriptionManager.shared.initialize()
                SubscriptionManager.shared.callback = subscriptionBridge
            }
        } else {
            SubscriptionManagerLegacy.shared.initialize()
            SubscriptionManagerLegacy.shared.callback = subscriptionBridge
        }
    }
    
    deinit {
        subscriptionBridge?.cleanup()
        if #available(iOS 15.0, *) {
            SubscriptionManager.shared.destroy()
        } else {
            SubscriptionManagerLegacy.shared.destroy()
        }
    }
}
```

## JavaScript API

The bridge exposes `window.iOSSubscriptionBridge` (also aliased as `window.AndroidSubscriptionBridge` for cross-platform compatibility):

### Methods

```javascript
// Subscribe to a product
window.iOSSubscriptionBridge.subscribe('premium_monthly');
window.iOSSubscriptionBridge.subscribe('premium_yearly');

// Restore previous purchases
window.iOSSubscriptionBridge.restorePurchases();

// Check if user has premium
window.iOSSubscriptionBridge.checkSubscription();

// Get detailed subscription status
window.iOSSubscriptionBridge.getSubscriptionStatus();
```

### Events

Listen for subscription events:

```javascript
// Option 1: Direct callback
window.onSubscriptionEvent = function(event) {
    console.log('Subscription event:', event.event);
    
    switch (event.event) {
        case 'subscription_purchased':
            console.log('Purchased:', event.productId);
            break;
        case 'subscription_restored':
            console.log('Restored:', event.productId);
            break;
        case 'subscription_error':
            console.log('Error:', event.message);
            break;
        case 'premium_status_changed':
            console.log('Premium:', event.isPremium);
            break;
    }
};

// Option 2: CustomEvent listener
window.addEventListener('subscription_event', function(e) {
    console.log('Subscription event:', e.detail);
});
```

### Event Payloads

**subscription_purchased / subscription_restored:**
```json
{
    "event": "subscription_purchased",
    "transactionId": "1000000123456789",
    "productId": "premium_monthly",
    "isPremium": true
}
```

**subscription_error:**
```json
{
    "event": "subscription_error",
    "errorCode": -1,
    "message": "Product not found"
}
```

**premium_status_changed:**
```json
{
    "event": "premium_status_changed",
    "isPremium": true
}
```

## Cross-Platform Compatibility

The iOS bridge is aliased as `window.AndroidSubscriptionBridge` so the web app can use a single interface:

```javascript
// Works on both iOS and Android
const bridge = window.AndroidSubscriptionBridge || window.iOSSubscriptionBridge;

if (bridge) {
    bridge.subscribe('premium_monthly');
}
```

## Testing

### Sandbox Testing

1. Create a Sandbox tester in App Store Connect
2. Sign out of App Store on device
3. Run the app and make a purchase
4. Sign in with Sandbox account when prompted

### StoreKit Testing in Xcode

1. Create a StoreKit Configuration file
2. Add test products matching your product IDs
3. Run in Simulator with StoreKit configuration enabled

## Troubleshooting

### Products Not Loading
- Verify product IDs match App Store Connect exactly
- Ensure Paid Apps agreement is signed
- Check that app bundle ID matches

### Purchases Failing
- Verify In-App Purchase capability is enabled
- Check entitlements file includes in-app purchase
- Ensure device is signed into valid Apple ID

### Restoration Not Working
- User must be signed into the Apple ID used for original purchase
- Subscription may have expired
- Check for transaction verification errors in logs
