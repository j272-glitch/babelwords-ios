const express = require('express');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());
app.use(express.static('.'));

// ─────────────────────────────────────────────────────────────────────────────
// IAP — Google Play Billing verification
// ─────────────────────────────────────────────────────────────────────────────

const PACKAGE_NAME = 'com.linguawonder.app';

const PRODUCT_CREDITS = {
    boost_hints_small:    { type: 'hints',        amount: 3  },
    boost_hints_medium:   { type: 'hints',        amount: 8  },
    boost_hints_large:    { type: 'hints',        amount: 20 },
    boost_foggust_small:  { type: 'foggusts',     amount: 2  },
    boost_foggust_bundle: { type: 'foggusts',     amount: 6  },
    sub_scholar_monthly:  { type: 'subscription', tier: 'scholar'  },
    sub_premium_monthly:  { type: 'subscription', tier: 'premium'  },
};

const SUBSCRIPTION_PRODUCTS = new Set(['sub_scholar_monthly', 'sub_premium_monthly']);

// In-memory store — replace with a persistent DB (e.g. Replit DB / Postgres) in production.
// Key: purchaseToken  Value: { productId, creditedAt }
const verifiedTokens = new Map();

// Simple per-IP balance store for demo; production must tie this to authenticated user accounts.
const userBalances = new Map();

function getBalance(userId) {
    if (!userBalances.has(userId)) {
        userBalances.set(userId, { hintBalance: 0, fogGustBalance: 0, subscription: null });
    }
    return userBalances.get(userId);
}

async function getAndroidPublisher() {
    const rawKey = process.env.GOOGLE_PLAY_KEY;
    if (!rawKey) return null;

    const { google } = await import('googleapis');
    let credentials;
    try {
        credentials = JSON.parse(rawKey);
    } catch {
        console.error('[IAP] GOOGLE_PLAY_KEY is not valid JSON');
        return null;
    }

    const auth = new google.auth.GoogleAuth({
        credentials,
        scopes: ['https://www.googleapis.com/auth/androidpublisher'],
    });

    return google.androidpublisher({ version: 'v3', auth });
}

async function verifyWithGoogle(publisher, productId, purchaseToken) {
    const isSubscription = SUBSCRIPTION_PRODUCTS.has(productId);
    if (isSubscription) {
        const { data } = await publisher.purchases.subscriptions.get({
            packageName: PACKAGE_NAME,
            subscriptionId: productId,
            token: purchaseToken,
        });
        // paymentState: 1 = payment received, 2 = free trial
        const valid = data.paymentState === 1 || data.paymentState === 2;
        return { valid, raw: data };
    } else {
        const { data } = await publisher.purchases.products.get({
            packageName: PACKAGE_NAME,
            productId,
            token: purchaseToken,
        });
        // purchaseState: 0 = purchased
        const valid = data.purchaseState === 0;
        return { valid, raw: data };
    }
}

// POST /api/iap/google/verify
// Body: { purchaseToken, productId, packageName? }
app.post('/api/iap/google/verify', async (req, res) => {
    const { purchaseToken, productId } = req.body || {};

    if (!purchaseToken || !productId) {
        return res.status(400).json({ error: 'purchaseToken and productId are required' });
    }

    const credit = PRODUCT_CREDITS[productId];
    if (!credit) {
        return res.status(400).json({ error: `Unknown productId: ${productId}` });
    }

    // Idempotency — do not double-credit the same token
    if (verifiedTokens.has(purchaseToken)) {
        const prev = verifiedTokens.get(purchaseToken);
        console.log(`[IAP] Token already verified for ${prev.productId} at ${prev.creditedAt}`);
        return res.json({ success: true, alreadyVerified: true, credit });
    }

    const publisher = await getAndroidPublisher();

    if (publisher) {
        // Real verification via Google Play Developer API
        try {
            const { valid, raw } = await verifyWithGoogle(publisher, productId, purchaseToken);
            if (!valid) {
                console.warn(`[IAP] Invalid purchase: ${productId} token=${purchaseToken.slice(0, 20)}…`);
                return res.status(402).json({ error: 'Purchase verification failed — invalid token' });
            }
            console.log(`[IAP] ✅ Verified ${productId} via Google API`);
        } catch (err) {
            console.error('[IAP] Google API error:', err.message);
            return res.status(502).json({ error: 'Google verification failed', detail: err.message });
        }
    } else {
        // GOOGLE_PLAY_KEY not set — skip verification (internal testing only)
        console.warn('[IAP] ⚠️  GOOGLE_PLAY_KEY not set — skipping verification (dev mode)');
    }

    // Record token to prevent double-credit
    verifiedTokens.set(purchaseToken, { productId, creditedAt: new Date().toISOString() });

    // Credit the user
    const userId = req.ip;
    const balance = getBalance(userId);

    if (credit.type === 'hints') {
        balance.hintBalance += credit.amount;
    } else if (credit.type === 'foggusts') {
        balance.fogGustBalance += credit.amount;
    } else if (credit.type === 'subscription') {
        balance.subscription = { tier: credit.tier, activatedAt: new Date().toISOString() };
    }

    console.log(`[IAP] Credited ${JSON.stringify(credit)} → balance: ${JSON.stringify(balance)}`);

    return res.json({
        success: true,
        credit,
        hintBalance:    balance.hintBalance,
        fogGustBalance: balance.fogGustBalance,
        subscription:   balance.subscription,
    });
});

// GET /api/iap/balance
app.get('/api/iap/balance', (req, res) => {
    const balance = getBalance(req.ip);
    res.json(balance);
});

// POST /api/iap/restore
// Acknowledges previously credited tokens are still valid (client-driven restore)
app.post('/api/iap/restore', async (req, res) => {
    const { tokens = [] } = req.body || {};
    const publisher = await getAndroidPublisher();
    const results = [];

    for (const { purchaseToken, productId } of tokens) {
        if (!purchaseToken || !productId || !PRODUCT_CREDITS[productId]) {
            results.push({ purchaseToken, productId, status: 'skipped' });
            continue;
        }

        if (verifiedTokens.has(purchaseToken)) {
            results.push({ purchaseToken, productId, status: 'already_credited' });
            continue;
        }

        let valid = !publisher; // if no key, trust the client (dev mode)
        if (publisher) {
            try {
                ({ valid } = await verifyWithGoogle(publisher, productId, purchaseToken));
            } catch {
                valid = false;
            }
        }

        if (!valid) {
            results.push({ purchaseToken, productId, status: 'invalid' });
            continue;
        }

        verifiedTokens.set(purchaseToken, { productId, creditedAt: new Date().toISOString() });
        const credit = PRODUCT_CREDITS[productId];
        const balance = getBalance(req.ip);

        if (credit.type === 'hints')        balance.hintBalance   += credit.amount;
        else if (credit.type === 'foggusts') balance.fogGustBalance += credit.amount;
        else if (credit.type === 'subscription') balance.subscription = { tier: credit.tier, activatedAt: new Date().toISOString() };

        results.push({ purchaseToken, productId, status: 'restored', credit });
    }

    res.json({ success: true, results });
});

// POST /api/iap/test-purchase  — development only, blocked in production
app.post('/api/iap/test-purchase', (req, res) => {
    if (process.env.NODE_ENV === 'production' || process.env.GOOGLE_PLAY_KEY) {
        return res.status(403).json({ error: 'test-purchase is disabled in production' });
    }
    const { productId } = req.body || {};
    const credit = PRODUCT_CREDITS[productId];
    if (!credit) return res.status(400).json({ error: `Unknown productId: ${productId}` });

    const balance = getBalance(req.ip);
    if (credit.type === 'hints')        balance.hintBalance   += credit.amount;
    else if (credit.type === 'foggusts') balance.fogGustBalance += credit.amount;
    else if (credit.type === 'subscription') balance.subscription = { tier: credit.tier, activatedAt: new Date().toISOString() };

    console.log(`[IAP-TEST] Granted ${JSON.stringify(credit)}`);
    res.json({ success: true, credit, hintBalance: balance.hintBalance, fogGustBalance: balance.fogGustBalance, subscription: balance.subscription });
});

// ─────────────────────────────────────────────────────────────────────────────
// Existing routes
// ─────────────────────────────────────────────────────────────────────────────

app.get('/policy', (req, res) => {
    res.send(`
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Privacy Policy - LinguaWonder</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            max-width: 800px;
            margin: 0 auto;
            padding: 2rem;
            color: #333;
        }
        h1 { color: #4F46E5; }
        h2 { color: #1e293b; margin-top: 2rem; }
        .back-link {
            display: inline-block;
            margin-bottom: 2rem;
            color: #4F46E5;
            text-decoration: none;
        }
        .back-link:hover { text-decoration: underline; }
        .highlight { background: #f0f9ff; padding: 1rem; border-left: 4px solid #4F46E5; margin: 1rem 0; }
    </style>
</head>
<body>
    <a href="/" class="back-link">← Back to LinguaWonder</a>

    <h1>Privacy Policy</h1>
    <p><em>Last updated: August 22, 2025</em></p>

    <div class="highlight">
        <strong>Quick Summary:</strong> LinguaWonder processes your voice recordings only for translation purposes. We don't store personal audio data and use secure, real-time processing.
    </div>

    <h2>1. Data Collection and Usage</h2>
    <p><strong>LinguaWonder</strong> is committed to protecting your privacy. This policy explains how we collect, use, and safeguard your information when using our speech translation service.</p>

    <h2>2. Audio Data Processing</h2>
    <ul>
        <li><strong>Voice Recordings:</strong> We temporarily process your voice recordings solely for translation purposes</li>
        <li><strong>Real-time Processing:</strong> Audio data is processed in real-time and is not stored permanently</li>
        <li><strong>No Personal Storage:</strong> We do not retain personal voice recordings after translation</li>
        <li><strong>Secure Transmission:</strong> All audio data is transmitted securely using industry-standard encryption</li>
    </ul>

    <h2>3. Third-Party Services</h2>
    <p>We use <strong>Deepgram Nova-2</strong> for advanced speech recognition. Deepgram's privacy policy governs their processing of audio data for transcription services.</p>

    <h2>4. Data Retention</h2>
    <ul>
        <li>Voice recordings are processed in real-time and deleted immediately after translation</li>
        <li>Translation logs may be kept for service improvement (fully anonymized)</li>
        <li>No personal identifiers are stored with usage data</li>
        <li>Session data is cleared when you close the application</li>
    </ul>

    <h2>5. User Rights and Consent</h2>
    <ul>
        <li><strong>Informed Consent:</strong> By using this service, you consent to audio processing for translation</li>
        <li><strong>Withdrawal:</strong> You can stop using the service at any time</li>
        <li><strong>Data Access:</strong> Contact us to request information about any stored data</li>
        <li><strong>Data Deletion:</strong> Request deletion of any stored data through our contact form</li>
    </ul>

    <h2>6. Security Measures</h2>
    <ul>
        <li>End-to-end encryption for all audio transmissions</li>
        <li>Secure API connections with certificate validation</li>
        <li>Minimal data retention policies</li>
        <li>Regular security audits and updates</li>
    </ul>

    <h2>7. Google Play Store Compliance</h2>
    <p>This privacy policy complies with Google Play Store requirements for applications that process audio data and in-app purchases. We maintain transparency about data collection and provide clear user consent mechanisms.</p>

    <h2>8. In-App Purchases</h2>
    <p>LinguaWonder offers optional in-app purchases through Google Play Billing. Purchase verification is performed server-side using the Google Play Developer API. We do not store full payment details.</p>

    <h2>9. International Data Transfers</h2>
    <p>Audio data may be processed on servers located in different countries. All transfers comply with applicable data protection regulations.</p>

    <h2>10. Children's Privacy</h2>
    <p>Our service is not directed to children under 13. We do not knowingly collect personal information from children under 13.</p>

    <h2>11. Changes to Privacy Policy</h2>
    <p>We may update this privacy policy periodically. Users will be notified of significant changes through the application.</p>

    <h2>12. Contact Information</h2>
    <ul>
        <li><strong>Email:</strong> privacy@linguagt.com</li>
        <li><strong>Response Time:</strong> We respond to privacy inquiries within 48 hours</li>
    </ul>

    <div class="highlight">
        <strong>Questions?</strong> Contact us at privacy@linguagt.com — your privacy is important to us.
    </div>

    <p style="margin-top: 3rem; padding-top: 2rem; border-top: 1px solid #e2e8f0; color: #64748b;">
        <strong>LinguaWonder Privacy Policy</strong> | Effective Date: August 22, 2025 |
        <a href="/" style="color: #4F46E5;">Return to App</a>
    </p>
</body>
</html>
    `);
});

app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'lingualink-ad.html'));
});

app.get('/ad', (req, res) => {
    res.sendFile(path.join(__dirname, 'lingualink-ad.html'));
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`LinguaWonder server running on http://0.0.0.0:${PORT}`);
    console.log(`IAP verification: ${process.env.GOOGLE_PLAY_KEY ? '✅ GOOGLE_PLAY_KEY set' : '⚠️  GOOGLE_PLAY_KEY not set (dev mode)'}`);
});
