import React from 'react';

function Section({ title, children }) {
  return (
    <section className="privacy-section">
      <h2>{title}</h2>
      {children}
    </section>
  );
}

function PrivacyPolicy() {
  return (
    <div className="privacy-policy">
      <h1>Privacy Policy</h1>
      <p className="privacy-effective">Effective date: May 2026</p>

      <Section title="1. What data we store">
        <p>
          When you connect an email account, this application fetches and locally caches
          your emails — including sender, recipients, subject, and message body — so they
          can be displayed, searched, and classified without repeatedly querying your mail
          provider.
        </p>
        <p>The following data is stored in the application database:</p>
        <ul>
          <li>Email metadata: message ID, received date, read/starred status, labels</li>
          <li>Email content: subject, sender, recipients, plain-text body, HTML body</li>
          <li>Classification results: spam score, phishing score, importance level, category</li>
          <li>Email account credentials: OAuth tokens (Gmail) or encrypted IMAP password</li>
          <li>Access audit logs: which actions were performed, when, and from which IP address</li>
        </ul>
      </Section>

      <Section title="2. How your data is protected">
        <p>
          Email content fields (subject, sender, recipients, body) are encrypted in the
          database using <strong>AES-256-GCM</strong> — a strong authenticated encryption
          algorithm. A fresh random initialisation vector (IV) is generated for every
          encryption operation, so no two ciphertexts are alike even for identical
          plaintexts.
        </p>
        <p>
          This means that someone who obtains a copy of the database file alone — for
          example through a SQL dump or a storage breach — cannot read your email content
          without also obtaining the encryption key.
        </p>
      </Section>

      <Section title="3. Important limitation: server-managed encryption key">
        <p>
          <strong>
            The encryption key is managed by the application server, not derived from your
            personal password.
          </strong>
        </p>
        <p>
          This is the same model used by most commercial email services (Gmail, Outlook,
          etc.). It means:
        </p>
        <ul>
          <li>
            People who operate the server — developers, system administrators — can in
            principle access the encryption key and use it to decrypt your email content.
          </li>
          <li>
            This application is <strong>not</strong> a zero-knowledge or end-to-end
            encrypted service. True end-to-end encryption would require the decryption key
            to never leave your device, which would also make server-side features like AI
            classification and search impossible.
          </li>
        </ul>
        <p>
          We are transparent about this trade-off so you can make an informed decision
          about what email accounts to connect.
        </p>
      </Section>

      <Section title="4. Access controls in place">
        <p>
          To limit who can access your data in practice, the following controls are applied:
        </p>
        <ul>
          <li>
            <strong>Audit logging:</strong> Every email read, send, delete, and search
            operation is recorded in an immutable audit log with a timestamp and client IP
            address. Automated sync jobs are also logged. This creates an accountable trail
            of all data access.
          </li>
          <li>
            <strong>Database role separation:</strong> The application connects to MySQL
            using a service account with only the permissions it needs. Developer accounts
            are granted column-level restrictions that exclude encrypted content columns
            (subject, sender, recipients, message body) — making casual inspection of email
            content impossible without deliberate circumvention.
          </li>
          <li>
            <strong>Credential isolation:</strong> OAuth tokens and IMAP passwords are
            stored encrypted and excluded from developer-accessible database views.
          </li>
        </ul>
      </Section>

      <Section title="5. Data retention">
        <p>
          Emails remain in the local cache until you delete them through the application
          or remove your email account. Deleting an email through this app also deletes it
          on your mail provider (Gmail / IMAP). Audit log entries are retained indefinitely
          by default to support compliance review.
        </p>
      </Section>

      <Section title="6. Third-party services">
        <p>
          If AI classification is enabled, email subject and body text may be sent to the
          OpenAI API for classification. OpenAI's own privacy policy governs how that data
          is handled. No email content is sent to any other third party.
        </p>
      </Section>

      <Section title="7. Your rights">
        <ul>
          <li>
            <strong>Access:</strong> You can view all emails stored for your account
            through the application interface.
          </li>
          <li>
            <strong>Deletion:</strong> Removing an email account from the application
            deletes all locally cached emails and credentials for that account.
          </li>
          <li>
            <strong>Portability:</strong> Your original emails remain on your mail provider
            (Gmail, Yahoo, etc.) independently of this application.
          </li>
        </ul>
      </Section>

      <Section title="8. Contact">
        <p>
          If you have questions about how your data is handled, or to request deletion of
          your data, please contact the application administrator.
        </p>
      </Section>
    </div>
  );
}

export default PrivacyPolicy;
