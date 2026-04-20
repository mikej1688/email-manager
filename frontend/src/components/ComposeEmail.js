import React, { useState } from 'react';

function ComposeEmail({ accounts, onClose, onSent, replyTo, forwardEmail }) {
  const [to, setTo] = useState(replyTo ? replyTo.to : (forwardEmail ? '' : ''));
  const [cc, setCc] = useState(replyTo ? (replyTo.cc || '') : '');
  const [subject, setSubject] = useState(
    replyTo ? replyTo.subject :
    forwardEmail ? `Fwd: ${forwardEmail.subject || ''}` : ''
  );
  const [body, setBody] = useState(
    forwardEmail
      ? `\n\n---------- Forwarded message ----------\nFrom: ${forwardEmail.fromAddress || ''}\nDate: ${forwardEmail.receivedDate ? new Date(forwardEmail.receivedDate).toLocaleString() : ''}\nSubject: ${forwardEmail.subject || ''}\nTo: ${forwardEmail.toAddresses || ''}\n\n${forwardEmail.bodyPlainText || ''}`
      : replyTo
        ? `\n\nOn ${replyTo.date}, ${replyTo.fromName || replyTo.to} wrote:\n> ${(replyTo.originalText || '').split('\n').join('\n> ')}`
        : ''
  );
  const [accountId, setAccountId] = useState(
    replyTo ? replyTo.accountId :
    forwardEmail ? forwardEmail.accountId :
    (accounts.length > 0 ? accounts[0].id : '')
  );
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [showCc, setShowCc] = useState(!!cc);

  const handleSend = async () => {
    if (!to.trim()) { setError('Please enter a recipient'); return; }
    if (!subject.trim()) { setError('Please enter a subject'); return; }
    if (!accountId) { setError('Please select an account'); return; }

    setSending(true);
    setError('');

    try {
      let url, payload;

      if (replyTo && replyTo.emailId) {
        url = `/api/emails/${replyTo.emailId}/reply`;
        payload = {
          body: body,
          cc: cc,
          replyAll: String(replyTo.replyAll || false),
          isHtml: 'false'
        };
      } else if (forwardEmail && forwardEmail.emailId) {
        url = `/api/emails/${forwardEmail.emailId}/forward`;
        payload = {
          to: to,
          body: body,
          isHtml: 'false'
        };
      } else {
        url = '/api/emails/send';
        payload = {
          accountId: String(accountId),
          to: to,
          cc: cc,
          subject: subject,
          body: body,
          isHtml: 'false'
        };
      }

      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (response.ok) {
        if (onSent) onSent();
        onClose();
      } else {
        let msg = 'Failed to send email';
        try {
          const data = await response.json();
          msg = data.message || msg;
        } catch (_) {}
        setError(msg);
      }
    } catch (err) {
      setError('Network error: could not send email');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="compose-overlay" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="compose-modal">
        <div className="compose-header">
          <span className="compose-title">
            {replyTo ? (replyTo.replyAll ? 'Reply All' : 'Reply') :
             forwardEmail ? 'Forward' : 'New Message'}
          </span>
          <button className="compose-close" onClick={onClose}>✕</button>
        </div>

        <div className="compose-body">
          {!replyTo && (
            <div className="compose-field">
              <label>From</label>
              <select value={accountId} onChange={e => setAccountId(Number(e.target.value))}>
                {accounts.map(a => (
                  <option key={a.id} value={a.id}>{a.emailAddress}</option>
                ))}
              </select>
            </div>
          )}

          <div className="compose-field">
            <label>To</label>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                type="text"
                value={to}
                onChange={e => setTo(e.target.value)}
                placeholder="recipient@example.com"
                disabled={!!replyTo}
                style={{ flex: 1 }}
              />
              {!showCc && (
                <button className="compose-cc-btn" onClick={() => setShowCc(true)}>Cc</button>
              )}
            </div>
          </div>

          {showCc && (
            <div className="compose-field">
              <label>Cc</label>
              <input
                type="text"
                value={cc}
                onChange={e => setCc(e.target.value)}
                placeholder="cc@example.com"
              />
            </div>
          )}

          <div className="compose-field">
            <label>Subject</label>
            <input
              type="text"
              value={subject}
              onChange={e => setSubject(e.target.value)}
              placeholder="Subject"
              disabled={!!replyTo}
            />
          </div>

          <div className="compose-field compose-field-body">
            <textarea
              value={body}
              onChange={e => setBody(e.target.value)}
              placeholder="Write your message..."
              rows={12}
            />
          </div>

          {error && <div className="compose-error">{error}</div>}
        </div>

        <div className="compose-footer">
          <button className="btn btn-primary compose-send-btn" onClick={handleSend} disabled={sending}>
            {sending ? 'Sending...' : 'Send'}
          </button>
          <button className="btn compose-discard-btn" onClick={onClose}>Discard</button>
        </div>
      </div>
    </div>
  );
}

export default ComposeEmail;
