import React, { useEffect, useState } from 'react';

const RECIPIENT_SUGGESTION_LIMIT = 8;

const getNormalizedRecipient = (recipient) => {
  if (!recipient) return '';
  const trimmed = recipient.trim();
  const start = trimmed.indexOf('<');
  const end = trimmed.indexOf('>');
  if (start >= 0 && end > start) {
    return trimmed.slice(start + 1, end).trim().toLowerCase();
  }
  return trimmed.toLowerCase();
};

const getActiveRecipientToken = (recipientValue) => {
  if (!recipientValue) return '';
  const separatorIndex = Math.max(recipientValue.lastIndexOf(','), recipientValue.lastIndexOf(';'));
  return recipientValue.slice(separatorIndex + 1).trim();
};

const replaceRecipientToken = (recipientValue, suggestion) => {
  const separatorIndex = Math.max(recipientValue.lastIndexOf(','), recipientValue.lastIndexOf(';'));
  const prefix = separatorIndex >= 0
    ? recipientValue.slice(0, separatorIndex + 1).trimEnd()
    : '';
  return prefix ? `${prefix} ${suggestion}, ` : `${suggestion}, `;
};

const buildReplyAllCc = (editedTo, originalTo, originalCc, accountEmailAddress) => {
  const toRecipients = new Set();
  (editedTo || '').split(/[;,]/).forEach((part) => {
    const normalized = getNormalizedRecipient(part);
    if (normalized) {
      toRecipients.add(normalized);
    }
  });

  const ownAddress = getNormalizedRecipient(accountEmailAddress);
  const deduped = new Map();
  [originalTo, originalCc].forEach((recipientList) => {
    (recipientList || '').split(/[;,]/).forEach((part) => {
      const trimmed = part.trim();
      const normalized = getNormalizedRecipient(trimmed);
      if (!normalized || normalized === ownAddress || toRecipients.has(normalized)) {
        return;
      }
      if (!deduped.has(normalized)) {
        deduped.set(normalized, trimmed);
      }
    });
  });

  return Array.from(deduped.values()).join(', ');
};

function ComposeEmail({ accounts, onClose, onSent, replyTo, forwardEmail }) {
  const currentAccount = accounts.find((account) => account.id === replyTo?.accountId);
  const initialReplyAllCc = replyTo?.replyAll
    ? buildReplyAllCc(replyTo.to, replyTo.originalToAddresses, replyTo.originalCcAddresses, currentAccount?.emailAddress)
    : (replyTo?.cc || '');

  const [to, setTo] = useState(replyTo ? replyTo.to : (forwardEmail ? '' : ''));
  const [cc, setCc] = useState(replyTo ? initialReplyAllCc : '');
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
  const [showCc, setShowCc] = useState(!!initialReplyAllCc || !!replyTo?.replyAll);
  const [ccEdited, setCcEdited] = useState(false);
  const [activeRecipientField, setActiveRecipientField] = useState('');
  const [recipientSuggestions, setRecipientSuggestions] = useState([]);
  const [showRecipientSuggestions, setShowRecipientSuggestions] = useState(false);
  const [highlightedRecipientIndex, setHighlightedRecipientIndex] = useState(-1);

  const activeRecipientValue = activeRecipientField === 'cc' ? cc : to;
  const activeRecipientToken = getActiveRecipientToken(activeRecipientValue);

  useEffect(() => {
    if (!replyTo?.replyAll || ccEdited) {
      return;
    }

    const recalculatedCc = buildReplyAllCc(
      to,
      replyTo.originalToAddresses,
      replyTo.originalCcAddresses,
      currentAccount?.emailAddress
    );
    setCc(recalculatedCc);
  }, [ccEdited, currentAccount?.emailAddress, replyTo, to]);

  useEffect(() => {
    if (!activeRecipientField || !showRecipientSuggestions) {
      setRecipientSuggestions([]);
      setHighlightedRecipientIndex(-1);
      return undefined;
    }

    let cancelled = false;
    const timeoutId = setTimeout(async () => {
      try {
        const response = await fetch(
          `/api/emails/recipient-suggestions?q=${encodeURIComponent(activeRecipientToken)}&limit=${RECIPIENT_SUGGESTION_LIMIT}`
        );

        if (!response.ok || cancelled) {
          return;
        }

        const suggestions = await response.json();
        const normalizedToken = getNormalizedRecipient(activeRecipientToken);
        const nextSuggestions = suggestions.filter((suggestion) => {
          const normalizedSuggestion = getNormalizedRecipient(suggestion);
          if (!normalizedSuggestion) {
            return false;
          }
          if (!normalizedToken) {
            return true;
          }
          return normalizedSuggestion.includes(normalizedToken) && normalizedSuggestion !== normalizedToken;
        });

        if (!cancelled) {
          setRecipientSuggestions(nextSuggestions);
          setHighlightedRecipientIndex(nextSuggestions.length > 0 ? 0 : -1);
        }
      } catch (_) {
        if (!cancelled) {
          setRecipientSuggestions([]);
          setHighlightedRecipientIndex(-1);
        }
      }
    }, 120);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [activeRecipientField, activeRecipientToken, showRecipientSuggestions]);

  const handleRecipientChange = (field, value) => {
    if (field === 'cc') {
      setCcEdited(true);
      setCc(value);
    } else {
      setTo(value);
    }
    setActiveRecipientField(field);
    setShowRecipientSuggestions(true);
    setHighlightedRecipientIndex(-1);
  };

  const handleRecipientFocus = (field) => {
    setActiveRecipientField(field);
    setShowRecipientSuggestions(true);
  };

  const handleRecipientBlur = () => {
    window.setTimeout(() => {
      setShowRecipientSuggestions(false);
      setHighlightedRecipientIndex(-1);
    }, 100);
  };

  const applyRecipientSuggestion = (field, suggestion) => {
    const currentValue = field === 'cc' ? cc : to;
    const nextValue = replaceRecipientToken(currentValue, suggestion);
    if (field === 'cc') {
      setCcEdited(true);
      setCc(nextValue);
    } else {
      setTo(nextValue);
    }
    setActiveRecipientField(field);
    setShowRecipientSuggestions(false);
    setHighlightedRecipientIndex(-1);
  };

  const handleRecipientKeyDown = (field, event) => {
    if (!showRecipientSuggestions || activeRecipientField !== field || recipientSuggestions.length === 0) {
      return;
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setHighlightedRecipientIndex((currentIndex) => (currentIndex + 1) % recipientSuggestions.length);
      return;
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlightedRecipientIndex((currentIndex) => (
        currentIndex <= 0 ? recipientSuggestions.length - 1 : currentIndex - 1
      ));
      return;
    }

    if (event.key === 'Enter') {
      if (highlightedRecipientIndex < 0 || highlightedRecipientIndex >= recipientSuggestions.length) {
        return;
      }

      event.preventDefault();
      applyRecipientSuggestion(field, recipientSuggestions[highlightedRecipientIndex]);
      return;
    }

    if (event.key === 'Escape') {
      event.preventDefault();
      setShowRecipientSuggestions(false);
      setHighlightedRecipientIndex(-1);
    }
  };

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
          to: to,
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
              <div className="compose-recipient-input-wrap" style={{ flex: 1 }}>
                <input
                  type="text"
                  value={to}
                  onChange={e => handleRecipientChange('to', e.target.value)}
                  onKeyDown={e => handleRecipientKeyDown('to', e)}
                  onFocus={() => handleRecipientFocus('to')}
                  onBlur={handleRecipientBlur}
                  placeholder="recipient@example.com"
                  autoComplete="off"
                  style={{ flex: 1 }}
                />
                {showRecipientSuggestions && activeRecipientField === 'to' && recipientSuggestions.length > 0 && (
                  <div className="compose-recipient-suggestions">
                    {recipientSuggestions.map((suggestion) => (
                      <button
                        key={suggestion}
                        type="button"
                        className={`compose-recipient-suggestion${suggestion === recipientSuggestions[highlightedRecipientIndex] ? ' is-highlighted' : ''}`}
                        onMouseDown={(event) => {
                          event.preventDefault();
                          applyRecipientSuggestion('to', suggestion);
                        }}
                      >
                        {suggestion}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              {!showCc && (
                <button className="compose-cc-btn" onClick={() => setShowCc(true)}>Cc</button>
              )}
            </div>
          </div>

          {showCc && (
            <div className="compose-field">
              <label>Cc</label>
              <div className="compose-recipient-input-wrap">
                <input
                  type="text"
                  value={cc}
                  onChange={e => handleRecipientChange('cc', e.target.value)}
                  onKeyDown={e => handleRecipientKeyDown('cc', e)}
                  onFocus={() => handleRecipientFocus('cc')}
                  onBlur={handleRecipientBlur}
                  placeholder="cc@example.com"
                  autoComplete="off"
                />
                {showRecipientSuggestions && activeRecipientField === 'cc' && recipientSuggestions.length > 0 && (
                  <div className="compose-recipient-suggestions">
                    {recipientSuggestions.map((suggestion) => (
                      <button
                        key={suggestion}
                        type="button"
                        className={`compose-recipient-suggestion${suggestion === recipientSuggestions[highlightedRecipientIndex] ? ' is-highlighted' : ''}`}
                        onMouseDown={(event) => {
                          event.preventDefault();
                          applyRecipientSuggestion('cc', suggestion);
                        }}
                      >
                        {suggestion}
                      </button>
                    ))}
                  </div>
                )}
              </div>
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
