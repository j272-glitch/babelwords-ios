import express from 'express';
import { MemStorage } from './storage';

const router = express.Router();
const storage = new MemStorage();

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
    .highlight { background-color: #EDE9FE; padding: 3px 6px; border-radius: 3px; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Privacy Policy - LinguaLink</h1>
    
    <p><strong>Effective Date:</strong> December 9, 2024</p>
    <p><strong>Last Updated:</strong> December 9, 2024</p>
    <p><strong>Version:</strong> 2.0</p>
    
    <div class="warning">
      ⚠️ <strong>Important Update:</strong> This privacy policy has been updated to include information about anonymous data sharing with advertising partners for revenue purposes. Please review Section 6.5 and Section 16.
    </div>

    <h2>1. Introduction</h2>
    <p>Welcome to LinguaLink ("we," "our," "us," or the "Service"). LinguaLink is committed to protecting your privacy and ensuring the security of your personal data in compliance with the General Data Protection Regulation (GDPR) (EU) 2016/679 and other applicable data protection laws.</p>
    <p>This Privacy Policy explains how we collect, use, disclose, and safeguard your information when you use our authentication service and platform.</p>
    
    <p><strong>Service URL:</strong> https://gtlingua.com</p>
    <p><strong>Contact Email:</strong> gtlingua@pm.me</p>
    <p><strong>Data Protection Officer:</strong> gtlingua@pm.me</p>

    <h2>2. Data Controller Information</h2>
    <p><strong>Data Controller:</strong><br>
    LinguaLink<br>
    GTLingua Development<br>
    Email: gtlingua@pm.me</p>
    <p>For GDPR purposes, we are the data controller of your personal information.</p>

    <h2>3. Information We Collect</h2>

    <h3>3.1 Information Collected Through OAuth Authentication</h3>
    <p>When you create an account using Single Sign-On (SSO) providers, we collect minimal information necessary for authentication:</p>
    
    <p><strong>From Google Sign-In:</strong></p>
    <ul>
      <li>Name (first and last)</li>
      <li>Email address</li>
      <li>Google account ID</li>
      <li>Profile picture URL (optional)</li>
    </ul>
    
    <p><strong>From X (Twitter) Sign-In:</strong></p>
    <ul>
      <li>Display name</li>
      <li>X username</li>
      <li>X account ID</li>
      <li>Email address (if permitted)</li>
      <li>Profile picture URL (optional)</li>
    </ul>
    
    <p><strong>From Facebook Login:</strong></p>
    <ul>
      <li>Name (first and last)</li>
      <li>Facebook user ID</li>
      <li>Email address (if permitted)</li>
      <li>Profile picture URL (optional)</li>
    </ul>
    
    <p><strong>From Apple Sign In:</strong></p>
    <ul>
      <li>Name (if shared)</li>
      <li>Apple user identifier</li>
      <li>Email address (may be relay email)</li>
    </ul>

    <h3>3.2 Information Collected Automatically</h3>
    <p><strong>Technical Information:</strong></p>
    <ul>
      <li>IP address (pseudonymized through hashing)</li>
      <li>Browser type and version</li>
      <li>Operating system</li>
      <li>Device type (mobile/desktop)</li>
      <li>Authentication timestamps</li>
      <li>Session identifiers</li>
      <li>Device advertising identifier (anonymized)</li>
    </ul>
    
    <p><strong>Usage Data:</strong></p>
    <ul>
      <li>Login frequency</li>
      <li>Last login date and time</li>
      <li>Authentication provider used</li>
      <li>Account creation date</li>
      <li>General usage patterns (anonymized)</li>
    </ul>

    <h3>3.3 Information Shared with Advertising Partners (Anonymized)</h3>
    <p><strong>We may share ONLY anonymized data with trusted advertising partners for revenue generation:</strong></p>
    <ul>
      <li>Hashed user identifiers (cannot be traced back to you)</li>
      <li>General demographic information (age range, country)</li>
      <li>Device type and operating system</li>
      <li>App usage patterns (anonymized)</li>
      <li>No personally identifiable information (PII) is ever shared</li>
    </ul>

    <h3>3.4 Consent and Preferences</h3>
    <p><strong>Consent Records:</strong></p>
    <ul>
      <li>Consent timestamp</li>
      <li>Consent version</li>
      <li>Specific permissions granted (including advertising consent)</li>
      <li>IP address at time of consent (hashed)</li>
    </ul>
    
    <p><strong>User Preferences:</strong></p>
    <ul>
      <li>Language preference</li>
      <li>Notification settings</li>
      <li>Privacy choices</li>
      <li>Advertising preferences</li>
    </ul>

    <h3>3.5 Information We Do NOT Collect or Share</h3>
    <p>LinguaLink does NOT collect, access, or share with any third parties:</p>
    <ul>
      <li>Your real name or email address with advertising partners</li>
      <li>Social media posts or content</li>
      <li>Friend lists or contacts</li>
      <li>Private messages</li>
      <li>Precise location data/GPS coordinates</li>
      <li>Financial information</li>
      <li>Health information</li>
      <li>Political affiliations</li>
      <li>Religious beliefs</li>
      <li>Sexual orientation</li>
      <li>Biometric data</li>
    </ul>

    <h2>4. Legal Basis for Processing</h2>
    <p>We process your personal data under the following legal bases according to GDPR Article 6:</p>

    <h3>4.1 Consent (Article 6(1)(a))</h3>
    <ul>
      <li>Creating your account through OAuth providers</li>
      <li><strong>Sharing anonymized data with advertising partners</strong></li>
      <li>Marketing communications (if opted in)</li>
      <li>Analytics data (if consented)</li>
    </ul>

    <h3>4.2 Contract (Article 6(1)(b))</h3>
    <ul>
      <li>Providing authentication services</li>
      <li>Managing your account</li>
      <li>Delivering core functionality</li>
    </ul>

    <h3>4.3 Legitimate Interests (Article 6(1)(f))</h3>
    <ul>
      <li>Ensuring platform security</li>
      <li>Preventing fraud and abuse</li>
      <li>Improving service performance</li>
      <li>Maintaining free service through advertising revenue (with consent)</li>
    </ul>

    <h3>4.4 Legal Obligation (Article 6(1)(c))</h3>
    <ul>
      <li>Complying with legal requirements</li>
      <li>Responding to lawful requests</li>
    </ul>

    <h2>5. Purpose of Data Processing</h2>
    <p>We use your personal data for:</p>

    <p><strong>Essential Services:</strong></p>
    <ul>
      <li>User authentication and authorization</li>
      <li>Account creation and management</li>
      <li>Session management</li>
      <li>Password-less login functionality</li>
    </ul>

    <p><strong>Service Improvement:</strong></p>
    <ul>
      <li>Technical troubleshooting</li>
      <li>Service optimization</li>
      <li>Security monitoring</li>
      <li>Fraud prevention</li>
    </ul>

    <p><strong>Advertising and Revenue (with consent):</strong></p>
    <ul>
      <li>Sharing anonymized data with advertising partners</li>
      <li>Generating advertising revenue to keep the service free</li>
      <li>Improving ad relevance (anonymized data only)</li>
      <li>No personally identifiable information is used</li>
    </ul>

    <p><strong>Communication (with consent):</strong></p>
    <ul>
      <li>Service updates and changes</li>
      <li>Security notifications</li>
      <li>Privacy policy updates</li>
      <li>Response to user inquiries</li>
    </ul>

    <p><strong>Legal Compliance:</strong></p>
    <ul>
      <li>Meeting regulatory requirements</li>
      <li>Responding to legal processes</li>
      <li>Protecting rights and property</li>
    </ul>

    <h2>6. Data Sharing and Recipients</h2>

    <h3>6.1 OAuth Providers</h3>
    <p>We interact with OAuth providers solely for authentication:</p>
    <ul>
      <li>Google (Google OAuth 2.0)</li>
      <li>X Corporation (X/Twitter OAuth 2.0)</li>
      <li>Meta Platforms (Facebook Login)</li>
      <li>Apple Inc. (Sign in with Apple)</li>
    </ul>
    <p>These providers receive only authentication requests. We do not share your LinguaLink activity or additional personal data with them.</p>

    <h3>6.2 Infrastructure Providers</h3>
    <ul>
      <li><strong>Replit:</strong> Hosting and infrastructure (data processing agreement in place)</li>
      <li><strong>Replit Database:</strong> Secure data storage</li>
    </ul>

    <h3>6.3 Advertising Partners (With Your Consent)</h3>
    <p>We may partner with carefully selected advertising networks to monetize our free service. Current partners are listed at: <a href="https://gtlingua.com/privacy/partners">https://gtlingua.com/privacy/partners</a></p>

    <p><strong>What We May Share with Advertising Partners:</strong></p>
    <ul>
      <li><strong>Anonymized user identifier</strong> (hashed, cannot identify you)</li>
      <li><strong>Device advertising ID</strong> (you can reset this in device settings)</li>
      <li><strong>General demographics</strong> (country, language, age range if provided)</li>
      <li><strong>Device information</strong> (type, OS version, screen size)</li>
      <li><strong>Anonymized usage data</strong> (session frequency, feature usage)</li>
    </ul>

    <p><strong>What We NEVER Share with Advertising Partners:</strong></p>
    <ul>
      <li>Your name, email, or any personally identifiable information</li>
      <li>Your OAuth provider account details</li>
      <li>Your social media content</li>
      <li>Your contacts or friends</li>
      <li>Your precise location</li>
      <li>Any data you haven't consented to share</li>
    </ul>

    <p><strong>How Advertising Partners Use Data:</strong></p>
    <ul>
      <li>Display relevant advertisements</li>
      <li>Measure ad performance</li>
      <li>Improve their advertising network</li>
      <li>Comply with their own privacy obligations</li>
    </ul>

    <h3>6.4 Data Anonymization Process</h3>
    <p>Before sharing with any advertising partner, we:</p>
    <ol>
      <li>Remove all direct identifiers (name, email, etc.)</li>
      <li>Hash user IDs using SHA-256 (irreversible)</li>
      <li>Generalize demographic data (e.g., age 25 → age range 18-34)</li>
      <li>Aggregate data where possible</li>
      <li>Apply differential privacy techniques</li>
      <li>Ensure k-anonymity (grouping with similar users)</li>
    </ol>

    <h3>6.5 We Never Share Data With:</h3>
    <ul>
      <li>Unauthorized third parties</li>
      <li>Data brokers for resale purposes</li>
      <li>Marketing companies (except approved advertising partners with consent)</li>
      <li>Unknown or unvetted third parties</li>
    </ul>

    <h3>6.6 Legal Disclosures</h3>
    <p>We may disclose data only when:</p>
    <ul>
      <li>Required by law</li>
      <li>Responding to valid legal process</li>
      <li>Protecting against imminent harm</li>
      <li>With your explicit consent</li>
    </ul>

    <h2>7. International Data Transfers</h2>
    <p>Your data may be processed in countries outside the European Economic Area (EEA):</p>

    <p><strong>For Authentication Services:</strong></p>
    <ul>
      <li>Standard Contractual Clauses (SCCs) with processors</li>
      <li>Adequacy decisions where applicable</li>
    </ul>

    <p><strong>For Advertising Partners:</strong></p>
    <ul>
      <li>Partners must comply with GDPR for EU users</li>
      <li>Data is anonymized before any transfer</li>
      <li>You can opt-out at any time</li>
      <li>Partners must be certified under applicable privacy frameworks</li>
    </ul>

    <h2>8. Data Retention</h2>
    <p>We retain personal data only as long as necessary:</p>

    <table>
      <tr>
        <th>Data Category</th>
        <th>Retention Period</th>
        <th>Justification</th>
      </tr>
      <tr>
        <td>Account Information</td>
        <td>Duration of account + 30 days</td>
        <td>Service provision</td>
      </tr>
      <tr>
        <td>Authentication Logs</td>
        <td>90 days</td>
        <td>Security and debugging</td>
      </tr>
      <tr>
        <td>Consent Records</td>
        <td>3 years from consent</td>
        <td>Legal requirement</td>
      </tr>
      <tr>
        <td>Deleted User Data</td>
        <td>30 days (recovery period)</td>
        <td>User convenience</td>
      </tr>
      <tr>
        <td>Security Logs</td>
        <td>1 year</td>
        <td>Fraud prevention</td>
      </tr>
      <tr>
        <td>Communication Records</td>
        <td>2 years</td>
        <td>Legal compliance</td>
      </tr>
      <tr>
        <td>Anonymized Advertising Data</td>
        <td>Indefinite (cannot identify you)</td>
        <td>Already anonymized</td>
      </tr>
      <tr>
        <td>Advertising Consent</td>
        <td>Until withdrawn + 3 years</td>
        <td>Legal documentation</td>
      </tr>
    </table>

    <p>After retention periods expire, personal data is permanently and irreversibly deleted. Anonymized data may be retained indefinitely as it cannot identify you.</p>

    <h2>9. Your Rights Under GDPR</h2>
    <p>As a data subject, you have the following rights:</p>

    <h3>9.1 Right to Access (Article 15)</h3>
    <p>Request a copy of all personal data we hold about you, including information about our advertising partners.</p>

    <h3>9.2 Right to Rectification (Article 16)</h3>
    <p>Request correction of inaccurate or incomplete data.</p>

    <h3>9.3 Right to Erasure (Article 17)</h3>
    <p>Request deletion of your personal data ("right to be forgotten").</p>

    <h3>9.4 Right to Restrict Processing (Article 18)</h3>
    <p>Request we limit how we use your data.</p>

    <h3>9.5 Right to Data Portability (Article 20)</h3>
    <p>Receive your data in a structured, commonly used, machine-readable format.</p>

    <h3>9.6 Right to Object (Article 21)</h3>
    <p>Object to processing based on legitimate interests or direct marketing.<br>
    <strong>Special Note:</strong> You can object to advertising data sharing at any time.</p>

    <h3>9.7 Right to Withdraw Consent (Article 7)</h3>
    <p>Withdraw previously given consent at any time, including advertising consent.</p>

    <h3>9.8 Right Not to be Subject to Automated Decision-Making (Article 22)</h3>
    <p>Not be subject to decisions based solely on automated processing.</p>

    <h3>9.9 Right to Lodge a Complaint</h3>
    <p>File a complaint with your local data protection authority.</p>

    <h2>10. Exercising Your Rights</h2>

    <h3>10.1 Self-Service Options</h3>
    <p>Access your privacy controls at: <a href="https://gtlingua.com/privacy">https://gtlingua.com/privacy</a></p>
    <ul>
      <li><strong>Export Your Data:</strong> Privacy Dashboard → Export Data (JSON format)</li>
      <li><strong>Delete Your Account:</strong> Privacy Dashboard → Delete Account</li>
      <li><strong>Update Consent:</strong> Privacy Dashboard → Manage Consent</li>
      <li><strong>Opt-Out of Advertising:</strong> Privacy Dashboard → Advertising Preferences → Disable</li>
      <li><strong>View Advertising Partners:</strong> Privacy Dashboard → Current Partners</li>
      <li><strong>Correct Information:</strong> Account Settings → Edit Profile</li>
    </ul>

    <h3>10.2 Advertising Opt-Out Options</h3>
    <p><strong>To opt-out of advertising partner data sharing:</strong></p>
    <ol>
      <li><strong>In LinguaLink:</strong> Settings → Privacy → Advertising → Opt-Out</li>
      <li><strong>Device Level:</strong>
        <ul>
          <li>iOS: Settings → Privacy → Advertising → Limit Ad Tracking</li>
          <li>Android: Settings → Google → Ads → Opt out of Ads Personalization</li>
        </ul>
      </li>
      <li><strong>Industry Tools:</strong>
        <ul>
          <li>EU: <a href="http://www.youronlinechoices.eu/">Your Online Choices</a></li>
          <li>US: <a href="http://optout.networkadvertising.org/">NAI Opt-Out</a></li>
          <li>Global: <a href="http://optout.aboutads.info/">Digital Advertising Alliance</a></li>
        </ul>
      </li>
    </ol>

    <h3>10.3 Contact Us</h3>
    <p>Email: gtlingua@pm.me<br>
    Subject: "GDPR Request - [Specific Right]"<br>
    Response Time: Within 30 days (one month)</p>

    <h3>10.4 Identity Verification</h3>
    <p>We may request information to verify your identity before processing requests.</p>

    <h2>11. Data Security Measures</h2>
    <p>LinguaLink implements comprehensive security measures:</p>

    <h3>11.1 Technical Measures</h3>
    <ul>
      <li><strong>Encryption:</strong> TLS 1.3 for data in transit</li>
      <li><strong>Secure Storage:</strong> Encrypted database storage</li>
      <li><strong>Anonymization:</strong> Irreversible hashing before sharing with partners</li>
      <li><strong>Access Control:</strong> Role-based access restrictions</li>
      <li><strong>Authentication:</strong> OAuth 2.0/OpenID Connect standards</li>
      <li><strong>Session Security:</strong> Secure session tokens with expiration</li>
      <li><strong>Password Protection:</strong> No passwords stored (OAuth only)</li>
    </ul>

    <h3>11.2 Organizational Measures</h3>
    <ul>
      <li>Privacy by design principles</li>
      <li>Regular security audits</li>
      <li>Employee data protection training</li>
      <li>Incident response procedures</li>
      <li>Data minimization practices</li>
      <li>Privacy impact assessments</li>
      <li>Data Processing Agreements with all partners</li>
    </ul>

    <h3>11.3 Breach Notification</h3>
    <p>In case of a data breach:</p>
    <ul>
      <li>Notification to authorities within 72 hours</li>
      <li>Notification to affected users without undue delay</li>
      <li>Partner notification if their data affected</li>
      <li>Documentation of breach and response</li>
    </ul>

    <h2>12. Cookies Policy</h2>

    <h3>12.1 Essential Cookies</h3>
    <p>We use strictly necessary cookies:</p>

    <table>
      <tr>
        <th>Cookie Name</th>
        <th>Purpose</th>
        <th>Duration</th>
      </tr>
      <tr>
        <td>session_id</td>
        <td>Maintain user session</td>
        <td>Session</td>
      </tr>
      <tr>
        <td>auth_token</td>
        <td>Authentication state</td>
        <td>24 hours</td>
      </tr>
      <tr>
        <td>consent_given</td>
        <td>Track consent status</td>
        <td>1 year</td>
      </tr>
      <tr>
        <td>csrf_token</td>
        <td>Security protection</td>
        <td>Session</td>
      </tr>
      <tr>
        <td>ad_consent</td>
        <td>Advertising consent status</td>
        <td>1 year</td>
      </tr>
    </table>

    <h3>12.2 Advertising Cookies (With Consent)</h3>
    <p>If you consent to advertising:</p>

    <table>
      <tr>
        <th>Cookie Name</th>
        <th>Purpose</th>
        <th>Duration</th>
      </tr>
      <tr>
        <td>anon_ad_id</td>
        <td>Anonymized identifier for advertising</td>
        <td>90 days</td>
      </tr>
      <tr>
        <td>ad_frequency</td>
        <td>Frequency capping</td>
        <td>30 days</td>
      </tr>
    </table>

    <h3>12.3 Third-Party Cookies</h3>
    <p>OAuth providers and advertising partners may set their own cookies. Refer to their privacy policies:</p>
    <ul>
      <li><a href="https://policies.google.com/privacy">Google Privacy Policy</a></li>
      <li><a href="https://twitter.com/privacy">X Privacy Policy</a></li>
      <li><a href="https://www.facebook.com/privacy/policy">Facebook Privacy Policy</a></li>
      <li><a href="https://www.apple.com/legal/privacy/">Apple Privacy Policy</a></li>
      <li>Current advertising partners: <a href="https://gtlingua.com/privacy/partners">https://gtlingua.com/privacy/partners</a></li>
    </ul>

    <h2>13. Children's Privacy</h2>
    <p>LinguaLink is not intended for users under 16 years of age. We do not knowingly:</p>
    <ul>
      <li>Collect personal data from children under 16</li>
      <li>Share children's data with advertising partners</li>
      <li>Display targeted advertising to children</li>
    </ul>
    <p>If we discover such collection, we will delete the data immediately.</p>
    <p>Parents/guardians who believe we have collected data from their child should contact us at gtlingua@pm.me.</p>

    <h2>14. Consent Management</h2>

    <h3>14.1 Obtaining Consent</h3>
    <p>We obtain explicit, informed consent through:</p>
    <ul>
      <li>Clear consent checkboxes (not pre-ticked)</li>
      <li>Granular consent options (separate for advertising)</li>
      <li>Easy-to-understand language</li>
      <li>Separate consent for different purposes</li>
    </ul>

    <h3>14.2 Advertising Consent</h3>
    <p><strong>Specific consent for advertising partner data sharing:</strong></p>
    <ul>
      <li>Clearly explained during onboarding</li>
      <li>Optional (service works without it)</li>
      <li>Can be withdrawn at any time</li>
      <li>Does not affect other service features</li>
      <li>List of current partners always available</li>
    </ul>

    <h3>14.3 Withdrawing Consent</h3>
    <p>Withdraw consent anytime at: <a href="https://gtlingua.com/privacy/consent">https://gtlingua.com/privacy/consent</a></p>
    <p>Withdrawal does not affect lawfulness of prior processing.</p>

    <h2>15. Marketing Communications</h2>
    <p>We send marketing communications only with explicit consent:</p>
    <ul>
      <li>Product updates</li>
      <li>New features</li>
      <li>Privacy tips</li>
    </ul>
    <p><strong>Note:</strong> Marketing communications are separate from advertising data sharing.</p>
    <p><strong>Unsubscribe:</strong> Click unsubscribe link in any email or update preferences in Privacy Dashboard.</p>

    <h2>16. Advertising and How We Keep LinguaLink Free</h2>

    <h3>16.1 Why We Use Advertising</h3>
    <p>LinguaLink is a free service. To maintain this, we partner with selected advertising networks to generate revenue through anonymized data sharing. This allows us to:</p>
    <ul>
      <li>Keep the service free for all users</li>
      <li>Maintain and improve our infrastructure</li>
      <li>Develop new features</li>
      <li>Ensure security and reliability</li>
    </ul>

    <h3>16.2 Your Control Over Advertising</h3>
    <p>You have complete control:</p>
    <ul>
      <li><strong>Opt-in required:</strong> We only share data if you explicitly consent</li>
      <li><strong>Opt-out anytime:</strong> Disable in Privacy Settings instantly</li>
      <li><strong>Use without ads:</strong> Service fully functional without advertising consent</li>
      <li><strong>No PII shared:</strong> Only anonymized, non-identifiable data</li>
      <li><strong>Transparency:</strong> Current partners listed at <a href="https://gtlingua.com/privacy/partners">https://gtlingua.com/privacy/partners</a></li>
    </ul>

    <h3>16.3 Transparency Commitment</h3>
    <p>We commit to:</p>
    <ul>
      <li>Never sharing personally identifiable information</li>
      <li>Being transparent about our advertising partnerships</li>
      <li>Providing clear opt-out mechanisms</li>
      <li>Respecting your privacy choices</li>
      <li>Vetting all advertising partners for privacy compliance</li>
      <li>Updating our partner list when changes occur</li>
    </ul>

    <h3>16.4 Advertising Partner Requirements</h3>
    <p>All our advertising partners must:</p>
    <ul>
      <li>Comply with GDPR and applicable privacy laws</li>
      <li>Sign data processing agreements</li>
      <li>Use data only for agreed purposes</li>
      <li>Implement appropriate security measures</li>
      <li>Delete data upon request</li>
      <li>Provide their own privacy policies</li>
    </ul>

    <h2>17. Changes to This Policy</h2>
    <p>We may update this Privacy Policy periodically. We will notify you of material changes via:</p>
    <ul>
      <li>Email notification</li>
      <li>Prominent notice on our Service</li>
      <li>Consent request for significant changes (like new advertising partnerships)</li>
    </ul>
    <p>Continue using LinguaLink after changes indicates acceptance of updated policy.</p>
    <p><strong>Note:</strong> Changes to advertising partnerships will be reflected in our partner list and may require renewed consent.</p>

    <h2>18. Data Protection Officer</h2>
    <p>For privacy concerns or questions:</p>
    <p><strong>Data Protection Officer</strong><br>
    Email: gtlingua@pm.me<br>
    Response time: 5 business days</p>
    <p>For advertising-specific concerns:<br>
    Email: gtlingua@pm.me</p>

    <h2>19. Supervisory Authority</h2>
    <p>EU users may lodge complaints with their local supervisory authority. Find your authority at: <a href="https://edpb.europa.eu/about-edpb/board/members_en">https://edpb.europa.eu/about-edpb/board/members_en</a></p>

    <h2>20. California Privacy Rights</h2>
    <p>California residents have additional rights under CCPA, including the right to:</p>
    <ul>
      <li>Know what personal information is collected</li>
      <li>Know if personal information is sold or disclosed (we do not sell personal information)</li>
      <li>Say no to the sale of personal information</li>
      <li>Request deletion of personal information</li>
    </ul>
    <p>See our California Privacy Notice for details.</p>

    <h2>21. Accessibility</h2>
    <p>This Privacy Policy is available in:</p>
    <ul>
      <li>Plain language format</li>
      <li>Screen reader compatible format</li>
      <li>Multiple languages (upon request)</li>
    </ul>
    <p>For accessible formats, email: gtlingua@pm.me</p>

    <h2>22. Contact Information</h2>
    <p><strong>General Privacy Inquiries:</strong><br>
    Email: gtlingua@pm.me</p>
    <p><strong>Data Protection Officer:</strong><br>
    Email: gtlingua@pm.me</p>
    <p><strong>Advertising Privacy Concerns:</strong><br>
    Email: gtlingua@pm.me</p>
    <p><strong>Response Commitment:</strong></p>
    <ul>
      <li>Acknowledgment: 48 hours</li>
      <li>Resolution: 30 days</li>
    </ul>

    <hr>

    <h2>Appendix A: Glossary</h2>
    <p><strong>Personal Data:</strong> Any information relating to an identified or identifiable person<br>
    <strong>Anonymized Data:</strong> Data that cannot be used to identify a person<br>
    <strong>Processing:</strong> Any operation performed on personal data<br>
    <strong>Data Controller:</strong> Entity determining purposes and means of processing<br>
    <strong>OAuth:</strong> Open standard for authorization<br>
    <strong>GDPR:</strong> General Data Protection Regulation<br>
    <strong>SSO:</strong> Single Sign-On<br>
    <strong>Advertising Partner:</strong> Vetted third-party advertising networks that receive only anonymized data<br>
    <strong>PII:</strong> Personally Identifiable Information</p>

    <h2>Appendix B: Version History</h2>
    <table>
      <tr>
        <th>Version</th>
        <th>Date</th>
        <th>Changes</th>
      </tr>
      <tr>
        <td>1.0</td>
        <td>Initial</td>
        <td>Initial privacy policy</td>
      </tr>
      <tr>
        <td>2.0</td>
        <td>December 9, 2024</td>
        <td>Added anonymized data sharing with advertising partners</td>
      </tr>
    </table>

    <h2>Appendix C: Advertising Partner Data Sharing Details</h2>
    <p><strong>Data Categories Shared (All Anonymized):</strong></p>
    <ol>
      <li><strong>Device Information</strong>
        <ul>
          <li>Device type (phone/tablet)</li>
          <li>Operating system and version</li>
          <li>Screen resolution</li>
          <li>Language setting</li>
        </ul>
      </li>
      <li><strong>Usage Information</strong>
        <ul>
          <li>Session frequency (daily/weekly/monthly user)</li>
          <li>Feature usage patterns (which OAuth providers used)</li>
          <li>General geographic region (country level only)</li>
        </ul>
      </li>
      <li><strong>Identifiers</strong>
        <ul>
          <li>Hashed user ID (SHA-256, cannot be reversed)</li>
          <li>Advertising ID (resettable by user)</li>
        </ul>
      </li>
    </ol>

    <p><strong>Data Security with Partners:</strong></p>
    <ul>
      <li>Data Processing Agreement required</li>
      <li>GDPR compliance mandatory</li>
      <li>Regular security audits</li>
      <li>Breach notification requirements</li>
      <li>Clear data deletion procedures</li>
    </ul>

    <p><strong>Current Advertising Partners:</strong><br>
    Visit <a href="https://gtlingua.com/privacy/partners">https://gtlingua.com/privacy/partners</a> for an up-to-date list of our advertising partners and their privacy policies.</p>

    <hr>

    <p><strong>Last Review Date:</strong> December 9, 2024<br>
    <strong>Next Review Date:</strong> June 9, 2025</p>

    <p>© 2024 LinguaLink. All rights reserved.</p>
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

    <h2>Deletion Timeline</h2>
    <ul>
      <li><strong>Self-Service:</strong> Immediate deletion</li>
      <li><strong>Email Request:</strong> Within 48 hours</li>
      <li><strong>Third-party OAuth:</strong> Notified of deletion (they control their own data)</li>
      <li><strong>Advertising Partners:</strong> Anonymized data cannot be traced to you</li>
    </ul>

    <h2>Before You Delete</h2>
    <div class="warning">
      ⚠️ <strong>Important:</strong>
      <ul>
        <li>Account deletion is <strong>permanent and irreversible</strong></li>
        <li>You'll lose access to all LinguaLink features</li>
        <li>Any saved preferences or settings will be lost</li>
        <li>You can create a new account later if needed</li>
      </ul>
    </div>

    <h2>Export Your Data First (Optional)</h2>
    <p>Want to keep a copy of your data before deletion?</p>
    <ol>
      <li>Visit <a href="https://gtlingua.com/privacy">Privacy Dashboard</a></li>
      <li>Click <strong>"Export Data"</strong></li>
      <li>Download your data (JSON format)</li>
      <li>Then proceed with deletion</li>
    </ol>
    <a href="https://gtlingua.com/privacy" class="button">Export Data First</a>

    <h2>Partial Data Management</h2>
    <p><strong>Don't want to delete everything? You can also:</strong></p>
    <ul>
      <li><strong>Opt-out of advertising:</strong> Keep account, stop data sharing</li>
      <li><strong>Update preferences:</strong> Control what data we collect</li>
      <li><strong>Restrict processing:</strong> Limit how we use your data</li>
    </ul>
    <a href="https://gtlingua.com/privacy" class="button">Manage Privacy Settings</a>

    <h2>Verification Process</h2>
    <p>To protect your account, we may ask you to verify your identity:</p>
    <ul>
      <li>Log in to your account for self-service deletion</li>
      <li>Email from your registered account for email requests</li>
      <li>Answer security questions if needed</li>
    </ul>

    <h2>Third-Party Data</h2>
    <p><strong>What we can and cannot delete:</strong></p>
    <ul>
      <li>✅ <strong>We delete:</strong> All data we control (LinguaLink account data)</li>
      <li>⚠️ <strong>We notify:</strong> OAuth providers (Google, Facebook, X, Apple) - they control their own data</li>
      <li>ℹ️ <strong>Already anonymous:</strong> Advertising partner data cannot identify you</li>
    </ul>

    <h2>Confirmation & Receipt</h2>
    <p>After deletion, you'll receive:</p>
    <ul>
      <li>Email confirmation of deletion</li>
      <li>Timestamp of when deletion occurred</li>
      <li>Reference number for your records</li>
      <li>Information about any data we cannot delete and why</li>
    </ul>

    <h2>Need Help?</h2>
    <div class="info">
      📞 <strong>Support Options:</strong>
      <ul>
        <li><strong>Email:</strong> gtlingua@pm.me</li>
        <li><strong>Data Protection Officer:</strong> gtlingua@pm.me</li>
        <li><strong>Response Time:</strong> Within 48 hours</li>
        <li><strong>Resolution Time:</strong> Within 30 days (usually much faster)</li>
      </ul>
    </div>

    <h2>Your Rights</h2>
    <p>Under data protection laws, you have the right to:</p>
    <ul>
      <li>Request deletion of your data (Right to be Forgotten)</li>
      <li>Access all data we have about you</li>
      <li>Correct inaccurate information</li>
      <li>Restrict how we process your data</li>
      <li>Object to certain types of processing</li>
      <li>Data portability (export your data)</li>
    </ul>

    <div class="warning">
      ⚠️ <strong>Legal Note:</strong> If you're not satisfied with our response, you can lodge a complaint with your local data protection authority.
    </div>

    <hr>
    
    <h2>Quick Action Links</h2>
    <div style="text-align: center;">
      <a href="https://gtlingua.com/privacy" class="button danger-button">Delete My Account</a>
      <a href="mailto:gtlingua@pm.me?subject=Data%20Deletion%20Request" class="button">Email Deletion Request</a>
      <a href="https://gtlingua.com/policy" class="button">View Privacy Policy</a>
    </div>

    <p style="text-align: center; margin-top: 30px; color: #6B7280;">
      <small>Last updated: December 9, 2024 | <a href="https://gtlingua.com/policy">Privacy Policy</a></small>
    </p>
  </div>
</body>
</html>`;
  
  res.send(deletionInstructionsHtml);
});

export default router;