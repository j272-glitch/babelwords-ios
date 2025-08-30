const express = require('express');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

// Serve static files
app.use(express.static('.'));

// Privacy policy route
app.get('/policy', (req, res) => {
    res.send(`
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Privacy Policy - LinguaLink</title>
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
    <a href="/" class="back-link">← Back to LinguaLink</a>
    
    <h1>Privacy Policy</h1>
    <p><em>Last updated: August 22, 2025</em></p>
    
    <div class="highlight">
        <strong>Quick Summary:</strong> LinguaLink processes your voice recordings only for translation purposes. We don't store personal audio data and use secure, real-time processing.
    </div>

    <h2>1. Data Collection and Usage</h2>
    <p><strong>LinguaLink</strong> is committed to protecting your privacy. This policy explains how we collect, use, and safeguard your information when using our speech translation service.</p>
    
    <h2>2. Audio Data Processing</h2>
    <ul>
        <li><strong>Voice Recordings:</strong> We temporarily process your voice recordings solely for translation purposes</li>
        <li><strong>Real-time Processing:</strong> Audio data is processed in real-time and is not stored permanently</li>
        <li><strong>No Personal Storage:</strong> We do not retain personal voice recordings after translation</li>
        <li><strong>Secure Transmission:</strong> All audio data is transmitted securely using industry-standard encryption</li>
    </ul>

    <h2>3. Third-Party Services</h2>
    <p>We use <strong>Deepgram Nova-2</strong> for advanced speech recognition. Deepgram's privacy policy governs their processing of audio data for transcription services. We have selected Deepgram for their commitment to privacy and security.</p>

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
    <p>We implement comprehensive security measures including:</p>
    <ul>
        <li>End-to-end encryption for all audio transmissions</li>
        <li>Secure API connections with certificate validation</li>
        <li>Minimal data retention policies</li>
        <li>Regular security audits and updates</li>
        <li>Access controls and authentication for all systems</li>
    </ul>

    <h2>7. Google Play Store Compliance</h2>
    <p>This privacy policy complies with Google Play Store requirements for applications that process audio data. We maintain transparency about data collection and provide clear user consent mechanisms throughout the application experience.</p>

    <h2>8. International Data Transfers</h2>
    <p>Audio data may be processed on servers located in different countries to provide optimal performance. All transfers comply with applicable data protection regulations and use appropriate safeguards.</p>

    <h2>9. Children's Privacy</h2>
    <p>Our service is not directed to children under 13. We do not knowingly collect personal information from children under 13. If you become aware that a child has provided personal information, please contact us.</p>

    <h2>10. Changes to Privacy Policy</h2>
    <p>We may update this privacy policy periodically. Users will be notified of significant changes through the application or via email if contact information is available.</p>

    <h2>11. Contact Information</h2>
    <p>For privacy-related questions, data requests, or concerns, please contact us:</p>
    <ul>
        <li><strong>Email:</strong> privacy@linguagt.com</li>
        <li><strong>Address:</strong> GTLingua Development, San Francisco, CA</li>
        <li><strong>Response Time:</strong> We respond to privacy inquiries within 48 hours</li>
    </ul>

    <h2>12. Legal Basis for Processing</h2>
    <p>We process your data based on:</p>
    <ul>
        <li><strong>Consent:</strong> Your explicit consent to use our translation services</li>
        <li><strong>Legitimate Interest:</strong> Providing and improving translation services</li>
        <li><strong>Service Delivery:</strong> Processing necessary to deliver requested translation services</li>
    </ul>

    <div class="highlight">
        <strong>Questions?</strong> If you have any questions about this privacy policy or how we handle your data, please don't hesitate to contact us. Your privacy is important to us.
    </div>

    <p style="margin-top: 3rem; padding-top: 2rem; border-top: 1px solid #e2e8f0; color: #64748b;">
        <strong>LinguaLink Privacy Policy</strong> | Effective Date: August 22, 2025 | 
        <a href="/" style="color: #4F46E5;">Return to App</a>
    </p>
</body>
</html>
    `);
});

// Main route - Serve LinguaLink Ad
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'lingualink-ad.html'));
});

// Also serve ad directly
app.get('/ad', (req, res) => {
    res.sendFile(path.join(__dirname, 'lingualink-ad.html'));
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`LinguaLink web app with privacy policy running on http://0.0.0.0:${PORT}`);
    console.log(`Privacy policy available at http://0.0.0.0:${PORT}/policy`);
});