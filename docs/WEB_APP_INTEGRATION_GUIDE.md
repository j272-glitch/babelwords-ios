# Web App Integration Guide for BabelWords Android

This guide documents the required JavaScript implementations for linguagt.com to work with the updated Android app ad system.

## Overview

The Android app injects `window.AndroidAdBridge` for ad operations. The web app must implement callback handlers that the Android app calls to notify about ad events.

## Required Callback Handlers

### Interstitial Ad Callbacks

```javascript
// Called when interstitial ad is loaded and ready to show
window.onInterstitialReady = function() {
    console.log('Interstitial ad ready');
    // Enable "show ad" button or auto-show
};

// Called when interstitial fails to load
// Parameters: error (string), code (number)
window.onInterstitialLoadFailed = function(error, code) {
    console.error('Interstitial load failed:', error, 'Code:', code);
    // Handle failure - maybe retry or hide ad button
    // Common codes: -2 = timeout, 3 = no fill
};

// Called when interstitial is dismissed by user
window.onInterstitialClosed = function() {
    console.log('Interstitial closed');
    // Resume app flow, preload next ad
};

// Called when interstitial fails to show
window.onInterstitialFailedToShow = function(error) {
    console.error('Interstitial show failed:', error);
    // Handle gracefully - continue without ad
};
```

### Rewarded Ad Callbacks

```javascript
// Called when rewarded ad is loaded and ready to show
window.onRewardedReady = function() {
    console.log('Rewarded ad ready');
    // Enable "watch ad for reward" button
};

// Called when rewarded fails to load
// Parameters: error (string), code (number)
window.onRewardedLoadFailed = function(error, code) {
    console.error('Rewarded load failed:', error, 'Code:', code);
    // Handle failure - maybe retry or show alternative
};

// Called when rewarded is dismissed WITHOUT earning reward
window.onRewardedClosed = function() {
    console.log('Rewarded closed without reward');
    // User cancelled before earning reward
};

// Called when rewarded fails to show
window.onRewardedFailedToShow = function(error) {
    console.error('Rewarded show failed:', error);
    // Handle gracefully
};

// CRITICAL: Called when user earns reward
// Parameters: type (string), amount (number) - BOTH required
window.onRewardEarned = function(type, amount) {
    console.log('Reward earned! Type:', type, 'Amount:', amount);
    // Grant 30 minutes of unlimited translations
    grantUnlimitedTranslations(30 * 60 * 1000); // 30 minutes in ms
};
```

## Calling Android Bridge Methods

### Check if Bridge Exists

```javascript
function isAndroidApp() {
    return typeof window.AndroidAdBridge !== 'undefined';
}
```

### Interstitial Ads

```javascript
// Load interstitial (preload for later)
function loadInterstitial() {
    if (isAndroidApp()) {
        window.AndroidAdBridge.loadInterstitial();
    }
}

// Show preloaded interstitial
function showInterstitial() {
    if (isAndroidApp()) {
        window.AndroidAdBridge.showInterstitial();
    }
}

// Load and show in one call (convenience method)
function loadAndShowInterstitial() {
    if (isAndroidApp()) {
        window.AndroidAdBridge.loadInterstitialAndShow();
    }
}

// Check if interstitial is ready
function isInterstitialReady() {
    if (isAndroidApp()) {
        return window.AndroidAdBridge.isInterstitialReady() === 'true';
    }
    return false;
}
```

### Rewarded Ads

```javascript
// Load rewarded ad (preload for later)
function loadRewarded() {
    if (isAndroidApp()) {
        window.AndroidAdBridge.loadRewarded();
    }
}

// Show preloaded rewarded ad
function showRewarded() {
    if (isAndroidApp()) {
        window.AndroidAdBridge.showRewarded();
    }
}

// Load and show in one call
function loadAndShowRewarded() {
    if (isAndroidApp()) {
        window.AndroidAdBridge.loadRewardedAndShow();
    }
}

// Check if rewarded is ready
function isRewardedReady() {
    if (isAndroidApp()) {
        return window.AndroidAdBridge.isRewardedReady() === 'true';
    }
    return false;
}
```

## Promise-Based Integration Pattern

For cleaner async/await usage:

```javascript
// Promise wrapper for interstitial
function showInterstitialAsync() {
    return new Promise((resolve, reject) => {
        if (!isAndroidApp()) {
            reject(new Error('Not in Android app'));
            return;
        }

        // Set up one-time handlers
        const originalClosed = window.onInterstitialClosed;
        const originalFailed = window.onInterstitialFailedToShow;

        window.onInterstitialClosed = function() {
            window.onInterstitialClosed = originalClosed;
            window.onInterstitialFailedToShow = originalFailed;
            resolve({ shown: true, rewarded: false });
        };

        window.onInterstitialFailedToShow = function(error) {
            window.onInterstitialClosed = originalClosed;
            window.onInterstitialFailedToShow = originalFailed;
            reject(new Error(error));
        };

        window.AndroidAdBridge.loadInterstitialAndShow();
    });
}

// Promise wrapper for rewarded
function showRewardedAsync() {
    return new Promise((resolve, reject) => {
        if (!isAndroidApp()) {
            reject(new Error('Not in Android app'));
            return;
        }

        let rewardEarned = false;

        const originalReward = window.onRewardEarned;
        const originalClosed = window.onRewardedClosed;
        const originalFailed = window.onRewardedFailedToShow;

        window.onRewardEarned = function(type, amount) {
            rewardEarned = true;
            if (originalReward) originalReward(type, amount);
            
            // Resolve after reward callback
            window.onRewardEarned = originalReward;
            window.onRewardedClosed = originalClosed;
            window.onRewardedFailedToShow = originalFailed;
            resolve({ rewarded: true, type, amount });
        };

        window.onRewardedClosed = function() {
            if (!rewardEarned) {
                window.onRewardEarned = originalReward;
                window.onRewardedClosed = originalClosed;
                window.onRewardedFailedToShow = originalFailed;
                resolve({ rewarded: false });
            }
        };

        window.onRewardedFailedToShow = function(error) {
            window.onRewardEarned = originalReward;
            window.onRewardedClosed = originalClosed;
            window.onRewardedFailedToShow = originalFailed;
            reject(new Error(error));
        };

        window.AndroidAdBridge.loadRewardedAndShow();
    });
}
```

## Usage Example

```javascript
// Show rewarded ad button handler
async function onWatchAdForRewardClick() {
    try {
        const result = await showRewardedAsync();
        
        if (result.rewarded) {
            // User watched full ad and earned reward
            showToast('You earned 30 minutes of unlimited translations!');
            enableUnlimitedMode(30 * 60 * 1000);
        } else {
            // User closed ad early
            showToast('Watch the full ad to earn your reward');
        }
    } catch (error) {
        console.error('Rewarded ad error:', error);
        showToast('Unable to show ad. Please try again.');
    }
}
```

## Important Notes

1. **Reward Callback Signature**: `onRewardEarned(type, amount)` requires BOTH parameters. The Android app sends type='coins' or similar and amount as an integer.

2. **Ad Expiration**: Ads expire after 45 minutes. The Android app handles this automatically and reloads expired ads.

3. **Network Retry**: The Android app implements exponential backoff (5s-60s) for network failures. The web app should handle temporary unavailability gracefully.

4. **Foreground Recovery**: After ad clicks that open Play Store/browser, the Android app automatically brings itself back to foreground.

5. **Bridge Method Return Values**: Methods like `isInterstitialReady()` return strings ('true'/'false'), not booleans. Always compare with === 'true'.

## Minimum Required Implementations

At minimum, implement these callbacks to ensure proper functionality:

```javascript
// Essential callbacks
window.onRewardEarned = function(type, amount) {
    // Grant unlimited translations for 30 minutes
};

window.onInterstitialClosed = function() {
    // Resume app flow
};

window.onRewardedClosed = function() {
    // Handle cancelled reward ad
};
```

## Testing

To test in browser without Android app:

```javascript
// Mock the Android bridge for testing
if (typeof window.AndroidAdBridge === 'undefined') {
    window.AndroidAdBridge = {
        loadInterstitial: () => console.log('[MOCK] loadInterstitial'),
        showInterstitial: () => {
            console.log('[MOCK] showInterstitial');
            setTimeout(() => window.onInterstitialClosed && window.onInterstitialClosed(), 2000);
        },
        loadInterstitialAndShow: () => {
            console.log('[MOCK] loadInterstitialAndShow');
            setTimeout(() => window.onInterstitialClosed && window.onInterstitialClosed(), 2000);
        },
        loadRewarded: () => console.log('[MOCK] loadRewarded'),
        showRewarded: () => {
            console.log('[MOCK] showRewarded');
            setTimeout(() => {
                window.onRewardEarned && window.onRewardEarned('coins', 1);
            }, 2000);
        },
        loadRewardedAndShow: () => {
            console.log('[MOCK] loadRewardedAndShow');
            setTimeout(() => {
                window.onRewardEarned && window.onRewardEarned('coins', 1);
            }, 2000);
        },
        isInterstitialReady: () => 'true',
        isRewardedReady: () => 'true'
    };
}
```
