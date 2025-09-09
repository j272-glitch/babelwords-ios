const express = require('express');
const router = express.Router();

router.get('/policy', (req, res) => {
  const privacyPolicyHtml = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Privacy Policy - LinguaLink</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
      line-height: 1.6;
      color: #333;
      max-width: 800px;
      margin: 0 auto;
      padding: 20px;
      background-color: #f8f9fa;
    }
    .container {
      background-color: white;
      padding: 40px;
      border-radius: 12px;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    }
    h1 { color: #6366F1; font-size: 2.5em; margin-bottom: 10px; }
    h2 { color: #6366F1; font-size: 1.8em; margin-top: 30px; border-bottom: 2px solid #6366F1; padding-bottom: 5px; }
    h3 { color: #4C1D95; font-size: 1.3em; margin-top: 25px; }
    h4 { color: #6B21A8; font-size: 1.1em; margin-top: 20px; }
    table { width: 100%; border-collapse: collapse; margin: 15px 0; }
    th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
    th { background-color: #6366F1; color: white; }
    tr:nth-child(even) { background-color: #f2f2f2; }
    .warning { background-color: #FEF3C7; border-left: 4px solid #F59E0B; padding: 15px; margin: 15px 0; border-radius: 4px; }
    strong { color: #4C1D95; }
    ul, ol { padding-left: 25px; }
    li { margin-bottom: 8px; }
    a { color: #6366F1; text-decoration: none; }
    a:hover { text-decoration: underline; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Privacy Policy - LinguaLink</h1>
    
    <p><strong>Effective Date:</strong> December 9, 2024</p>
    <p><strong>Last Updated:</strong> December 9, 2024</p>
    <p><strong>Version:</strong> 2.0</p>
    
    <div class="warning">
      ⚠️ <strong>Important Update:</strong> This privacy policy has been updated to include information about anonymous data sharing with advertising partners for revenue purposes.
    </div>

    <h2>Contact Information</h2>
    <p><strong>Service URL:</strong> https://gtlingua.com</p>
    <p><strong>Contact Email:</strong> gtlingua@pm.me</p>
    <p><strong>Data Protection Officer:</strong> gtlingua@pm.me</p>

    <h2>Data Deletion</h2>
    <p>You have the right to request deletion of your personal data. For detailed instructions, visit:</p>
    <p><a href="/delete-data" style="font-weight: bold;">Data Deletion Instructions</a></p>

    <p style="text-align: center; margin-top: 30px; color: #6B7280;">
      <small>© 2024 LinguaLink. All rights reserved.</small>
    </p>
  </div>
</body>
</html>`;
  
  res.send(privacyPolicyHtml);
});

// Data deletion instructions endpoint
router.get('/delete-data', (req, res) => {
  const deletionInstructionsHtml = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Data Deletion Instructions - LinguaLink</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
      line-height: 1.6;
      color: #333;
      max-width: 800px;
      margin: 0 auto;
      padding: 20px;
      background-color: #f8f9fa;
    }
    .container {
      background-color: white;
      padding: 40px;
      border-radius: 12px;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    }
    h1 { color: #6366F1; font-size: 2.5em; margin-bottom: 10px; }
    h2 { color: #6366F1; font-size: 1.8em; margin-top: 30px; border-bottom: 2px solid #6366F1; padding-bottom: 5px; }
    h3 { color: #4C1D95; font-size: 1.3em; margin-top: 25px; }
    .warning { background-color: #FEF3C7; border-left: 4px solid #F59E0B; padding: 15px; margin: 15px 0; border-radius: 4px; }
    .success { background-color: #D1FAE5; border-left: 4px solid #10B981; padding: 15px; margin: 15px 0; border-radius: 4px; }
    .info { background-color: #DBEAFE; border-left: 4px solid #3B82F6; padding: 15px; margin: 15px 0; border-radius: 4px; }
    strong { color: #4C1D95; }
    ul, ol { padding-left: 25px; }
    li { margin-bottom: 8px; }
    a { color: #6366F1; text-decoration: none; }
    a:hover { text-decoration: underline; }
    .button { 
      background-color: #6366F1; 
      color: white; 
      padding: 12px 24px; 
      border-radius: 8px; 
      text-decoration: none; 
      display: inline-block; 
      margin: 10px 5px; 
      font-weight: bold;
    }
    .button:hover { background-color: #4F46E5; color: white; }
    .danger-button { 
      background-color: #EF4444; 
    }
    .danger-button:hover { background-color: #DC2626; }
    .method-box {
      border: 2px solid #E5E7EB;
      border-radius: 8px;
      padding: 20px;
      margin: 15px 0;
    }
    .method-title {
      color: #6366F1;
      font-size: 1.2em;
      margin-bottom: 10px;
      font-weight: bold;
    }
  </style>
</head>
<body>
  <div class="container">
    <h1>Data Deletion Instructions</h1>
    <p><strong>LinguaLink - Your Right to be Forgotten</strong></p>
    
    <div class="info">
      ℹ️ <strong>Your Rights:</strong> Under GDPR Article 17, you have the right to request deletion of your personal data. We make this process simple and transparent.
    </div>

    <h2>Quick Delete Options</h2>

    <div class="method-box">
      <div class="method-title">🚀 Method 1: Self-Service (Instant)</div>
      <p><strong>Fastest option - Delete your account instantly</strong></p>
      <ol>
        <li>Visit <a href="https://gtlingua.com/privacy">https://gtlingua.com/privacy</a></li>
        <li>Log in to your account</li>
        <li>Go to <strong>Privacy Dashboard</strong></li>
        <li>Click <strong>"Delete Account"</strong></li>
        <li>Confirm deletion</li>
        <li>Your data is immediately removed</li>
      </ol>
      <a href="https://gtlingua.com/privacy" class="button danger-button">Delete Account Now</a>
    </div>

    <div class="method-box">
      <div class="method-title">📧 Method 2: Email Request</div>
      <p><strong>Email us for assisted deletion</strong></p>
      <ol>
        <li>Send email to: <strong>gtlingua@pm.me</strong></li>
        <li>Subject: <strong>"Data Deletion Request"</strong></li>
        <li>Include your account email</li>
        <li>We'll process within 48 hours</li>
        <li>Confirmation email sent</li>
      </ol>
      <a href="mailto:gtlingua@pm.me?subject=Data%20Deletion%20Request&body=Please%20delete%20my%20account%20and%20all%20personal%20data.%0A%0AAccount%20email:%20[YOUR_EMAIL_HERE]%0A%0AThank%20you." class="button">Send Deletion Email</a>
    </div>

    <h2>What Gets Deleted</h2>
    <div class="success">
      ✅ <strong>Immediately Deleted:</strong>
      <ul>
        <li>Your account profile and settings</li>
        <li>Authentication tokens and sessions</li>
        <li>Personal data (name, email, profile picture)</li>
        <li>OAuth provider connections</li>
        <li>Usage history and preferences</li>
        <li>Communication records</li>
      </ul>
    </div>

    <div class="info">
      📊 <strong>Anonymized Data:</strong> Any previously anonymized advertising data cannot be deleted as it cannot be traced back to you. This data was already stripped of all personally identifiable information.
    </div>

    <h2>Need Help?</h2>
    <div class="info">
      📞 <strong>Support Options:</strong>
      <ul>
        <li><strong>Email:</strong> gtlingua@pm.me</li>
        <li><strong>Data Protection Officer:</strong> gtlingua@pm.me</li>
        <li><strong>Response Time:</strong> Within 48 hours</li>
      </ul>
    </div>

    <hr>
    
    <h2>Quick Action Links</h2>
    <div style="text-align: center;">
      <a href="https://gtlingua.com/privacy" class="button danger-button">Delete My Account</a>
      <a href="mailto:gtlingua@pm.me?subject=Data%20Deletion%20Request" class="button">Email Deletion Request</a>
      <a href="/policy" class="button">View Privacy Policy</a>
    </div>

    <p style="text-align: center; margin-top: 30px; color: #6B7280;">
      <small>Last updated: December 9, 2024 | <a href="/policy">Privacy Policy</a></small>
    </p>
  </div>
</body>
</html>`;
  
  res.send(deletionInstructionsHtml);
});

module.exports = router;