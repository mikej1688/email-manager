import React, { useState, useEffect, useRef, useCallback } from 'react';
import ComposeEmail from './ComposeEmail';

const POLL_INTERVAL_MS = 60000; // 60 seconds

function EmailList({ accounts }) {
  const [emails, setEmails] = useState([]);
  const [selectedAccount, setSelectedAccount] = useState(null);
  const [filter, setFilter] = useState('all');
  const [loading, setLoading] = useState(false);
  const [selectedEmail, setSelectedEmail] = useState(null);
  const [emailLoading, setEmailLoading] = useState(false);
  const [composeMode, setComposeMode] = useState(null); // null | 'compose' | 'reply' | 'replyAll' | 'forward'
  const [selectedEmails, setSelectedEmails] = useState(new Set());
  const [actionFeedback, setActionFeedback] = useState('');
  const [newEmailCount, setNewEmailCount] = useState(0);

  // Stable refs so the polling interval doesn't go stale
  const selectedAccountRef = useRef(selectedAccount);
  const filterRef = useRef(filter);
  const emailsRef = useRef(emails);
  useEffect(() => { selectedAccountRef.current = selectedAccount; }, [selectedAccount]);
  useEffect(() => { filterRef.current = filter; }, [filter]);
  useEffect(() => { emailsRef.current = emails; }, [emails]);

  useEffect(() => {
    if (accounts.length > 0 && !selectedAccount) {
      setSelectedAccount(accounts[0].id);
    }
  }, [accounts]);

  useEffect(() => {
    if (selectedAccount) {
      fetchEmails();
    }
  }, [selectedAccount, filter]);

  // Auto-clear feedback
  useEffect(() => {
    if (actionFeedback) {
      const t = setTimeout(() => setActionFeedback(''), 3000);
      return () => clearTimeout(t);
    }
  }, [actionFeedback]);

  // Auto-clear new-email badge
  useEffect(() => {
    if (newEmailCount > 0) {
      const t = setTimeout(() => setNewEmailCount(0), 5000);
      return () => clearTimeout(t);
    }
  }, [newEmailCount]);

  // Background polling — silently checks for new emails without resetting loading state
  useEffect(() => {
    const poll = async () => {
      const accountId = selectedAccountRef.current;
      const currentFilter = filterRef.current;
      if (!accountId) return;
      try {
        // Sync from Gmail before checking for new arrivals
        await fetch(`/api/accounts/${accountId}/sync`, { method: 'POST' });

        let url = `/api/emails/account/${accountId}`;
        if (currentFilter === 'unread') url += '/unread';
        else if (currentFilter === 'urgent') url += '/importance/URGENT';
        else if (currentFilter === 'high') url += '/importance/HIGH';
        else if (currentFilter === 'sent') url += '/category/SENT';

        const response = await fetch(url);
        if (!response.ok) return;
        const data = await response.json();
        const fetched = data.content || [];

        setEmails(prev => {
          const prevIds = new Set(prev.map(e => e.id));
          const incoming = fetched.filter(e => !prevIds.has(e.id));
          if (incoming.length > 0) {
            setNewEmailCount(incoming.length);
            return [...incoming, ...prev];
          }
          return prev;
        });
      } catch (_) {
        // Silent — don't disturb the UI on poll failure
      }
    };

    const timer = setInterval(poll, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, []); // empty deps — uses refs to stay stable

  // Fetch local DB emails (no Gmail sync)
  const fetchEmails = async (accountId = selectedAccount, currentFilter = filter) => {
    if (!accountId) return;
    setLoading(true);
    setNewEmailCount(0);
    try {
      let url = `/api/emails/account/${accountId}`;
      if (currentFilter === 'unread') url += '/unread';
      else if (currentFilter === 'urgent') url += '/importance/URGENT';
      else if (currentFilter === 'high') url += '/importance/HIGH';
      else if (currentFilter === 'sent') url += '/category/SENT';

      const response = await fetch(url);
      const data = await response.json();
      setEmails(data.content || []);
      setSelectedEmails(new Set());
    } catch (error) {
      console.error('Error fetching emails:', error);
    } finally {
      setLoading(false);
    }
  };

  // Sync from Gmail then reload local list
  const syncAndRefresh = async () => {
    if (!selectedAccount) return;
    setLoading(true);
    setNewEmailCount(0);
    try {
      await fetch(`/api/accounts/${selectedAccount}/sync`, { method: 'POST' });
    } catch (error) {
      console.error('Sync error:', error);
    }
    await fetchEmails();
  };

  const openEmail = async (email) => {
    setEmailLoading(true);
    try {
      const response = await fetch(`/api/emails/${email.id}`);
      const data = await response.json();
      setSelectedEmail(data);
      if (!email.isRead) {
        await fetch(`/api/emails/${email.id}/mark-read`, { method: 'PUT' });
        setEmails(prev => prev.map(e => e.id === email.id ? { ...e, isRead: true } : e));
      }
    } catch (error) {
      console.error('Error opening email:', error);
    } finally {
      setEmailLoading(false);
    }
  };

  const refreshEmailBody = async () => {
    if (!selectedEmail) return;
    setEmailLoading(true);
    try {
      const response = await fetch(`/api/emails/${selectedEmail.id}/refresh-body`, { method: 'PUT' });
      if (response.ok) {
        const data = await response.json();
        setSelectedEmail(data);
      }
    } catch (error) {
      console.error('Error refreshing email body:', error);
    } finally {
      setEmailLoading(false);
    }
  };

  // --- Action handlers ---

  const trashEmail = async (emailId) => {
    try {
      const response = await fetch(`/api/emails/${emailId}/trash`, { method: 'PUT' });
      if (response.ok) {
        setEmails(prev => prev.filter(e => e.id !== emailId));
        if (selectedEmail && selectedEmail.id === emailId) setSelectedEmail(null);
        setActionFeedback('Moved to Trash');
      }
    } catch (error) {
      console.error('Error trashing email:', error);
      setActionFeedback('Failed to delete');
    }
  };

  const archiveEmail = async (emailId) => {
    try {
      const response = await fetch(`/api/emails/${emailId}/archive`, { method: 'PUT' });
      if (response.ok) {
        setEmails(prev => prev.filter(e => e.id !== emailId));
        if (selectedEmail && selectedEmail.id === emailId) setSelectedEmail(null);
        setActionFeedback('Archived');
      }
    } catch (error) {
      console.error('Error archiving email:', error);
    }
  };

  const moveEmail = async (emailId, category) => {
    try {
      const response = await fetch(`/api/emails/${emailId}/move?category=${category}`, { method: 'PUT' });
      if (response.ok) {
        setEmails(prev => prev.filter(e => e.id !== emailId));
        if (selectedEmail && selectedEmail.id === emailId) setSelectedEmail(null);
        setActionFeedback(`Moved to ${category}`);
      }
    } catch (error) {
      console.error('Error moving email:', error);
    }
  };

  const toggleStar = async (emailId, isStarred) => {
    try {
      const endpoint = isStarred ? 'unstar' : 'star';
      await fetch(`/api/emails/${emailId}/${endpoint}`, { method: 'PUT' });
      setEmails(prev => prev.map(e => e.id === emailId ? { ...e, isStarred: !isStarred } : e));
      if (selectedEmail && selectedEmail.id === emailId) {
        setSelectedEmail(prev => ({ ...prev, isStarred: !isStarred }));
      }
    } catch (error) {
      console.error('Error toggling star:', error);
    }
  };

  const bulkTrash = async () => {
    for (const id of selectedEmails) {
      await trashEmail(id);
    }
    setSelectedEmails(new Set());
  };

  const bulkArchive = async () => {
    for (const id of selectedEmails) {
      await archiveEmail(id);
    }
    setSelectedEmails(new Set());
  };

  const toggleSelect = (emailId, e) => {
    e.stopPropagation();
    setSelectedEmails(prev => {
      const next = new Set(prev);
      if (next.has(emailId)) next.delete(emailId);
      else next.add(emailId);
      return next;
    });
  };

  const openReply = (replyAll = false) => {
    if (!selectedEmail) return;
    setComposeMode(replyAll ? 'replyAll' : 'reply');
  };

  const openForward = () => {
    if (!selectedEmail) return;
    setComposeMode('forward');
  };

  const getImportanceBadge = (importance) => {
    if (!importance) return null;
    const classes = {
      'URGENT': 'importance-badge importance-urgent',
      'HIGH': 'importance-badge importance-high',
      'NORMAL': 'importance-badge importance-normal',
      'LOW': 'importance-badge importance-low'
    };
    return <span className={classes[importance] || 'importance-badge'}>{importance}</span>;
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h2 style={{ margin: 0 }}>Emails</h2>
        <button className="btn btn-primary" onClick={() => setComposeMode('compose')}
          style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.95rem' }}>
          ✏️ Compose
        </button>
      </div>

      {actionFeedback && (
        <div className="action-feedback">{actionFeedback}</div>
      )}

      {newEmailCount > 0 && (
        <div className="action-feedback" style={{background: '#1a73e8', cursor: 'pointer'}}
          onClick={fetchEmails}>
          📬 {newEmailCount} new email{newEmailCount > 1 ? 's' : ''} arrived — click to refresh
        </div>
      )}

      <div style={{display: 'flex', gap: '1rem', alignItems: 'flex-start'}}>
        {/* Email List Panel */}
        <div className="card" style={{flex: selectedEmail ? '0 0 40%' : '1', minWidth: 0}}>
        <div style={{display: 'flex', gap: '1rem', marginBottom: '1rem', flexWrap: 'wrap', alignItems: 'center'}}>
          <div>
            <label htmlFor="account-select">Account: </label>
            <select
              id="account-select"
              value={selectedAccount || ''}
              onChange={(e) => setSelectedAccount(Number(e.target.value))}
              style={{padding: '0.5rem', borderRadius: '4px', border: '1px solid #ddd'}}
            >
              {accounts.map(account => (
                <option key={account.id} value={account.id}>
                  {account.emailAddress}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="filter-select">Filter: </label>
            <select
              id="filter-select"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              style={{padding: '0.5rem', borderRadius: '4px', border: '1px solid #ddd'}}
            >
              <option value="all">All</option>
              <option value="unread">Unread</option>
              <option value="urgent">Urgent</option>
              <option value="high">High Priority</option>
              <option value="sent">Sent</option>
            </select>
          </div>

          <button onClick={syncAndRefresh} className="btn btn-primary">
            🔄 Refresh
          </button>

          {selectedEmails.size > 0 && (
            <div className="bulk-actions">
              <span style={{ fontSize: '0.85rem', color: '#5f6368' }}>{selectedEmails.size} selected</span>
              <button className="btn-icon" title="Delete selected" onClick={bulkTrash}>🗑️</button>
              <button className="btn-icon" title="Archive selected" onClick={bulkArchive}>📥</button>
            </div>
          )}
        </div>

        {loading ? (
          <p>Loading emails...</p>
        ) : emails.length === 0 ? (
          <p>No emails found.</p>
        ) : (
          <ul className="email-list">
            {emails.map(email => (
              <li
                key={email.id}
                className={`email-item ${!email.isRead ? 'unread' : ''} ${email.importance ? email.importance.toLowerCase() : ''}`}
                onClick={() => openEmail(email)}
                style={{cursor: 'pointer', background: selectedEmail && selectedEmail.id === email.id ? '#eaf4fb' : ''}}
              >
                <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                  <input
                    type="checkbox"
                    checked={selectedEmails.has(email.id)}
                    onChange={(e) => toggleSelect(email.id, e)}
                    onClick={(e) => e.stopPropagation()}
                    style={{ cursor: 'pointer', accentColor: '#1a73e8' }}
                  />
                  <span
                    className="star-btn"
                    onClick={(e) => { e.stopPropagation(); toggleStar(email.id, email.isStarred); }}
                    title={email.isStarred ? 'Unstar' : 'Star'}
                  >
                    {email.isStarred ? '⭐' : '☆'}
                  </span>
                  <div style={{flex: 1, minWidth: 0}}>
                    <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                      <div>
                        <strong>{filter === 'sent' ? (email.toAddresses || email.fromAddress) : email.fromAddress}</strong>
                        {filter === 'sent' && <span style={{marginLeft: '6px', fontSize: '0.8rem', color: '#5f6368'}}>To</span>}
                        {getImportanceBadge(email.importance)}
                        {email.isPhishing && <span style={{color: 'red', marginLeft: '0.5rem'}}>⚠️ PHISHING</span>}
                        {email.isSpam && <span style={{color: 'orange', marginLeft: '0.5rem'}}>🗑️ SPAM</span>}
                      </div>
                      <div style={{fontSize: '0.9rem', color: '#7f8c8d', display: 'flex', alignItems: 'center', gap: '8px'}}>
                        {new Date(email.receivedDate).toLocaleString()}
                        <span className="email-item-actions" onClick={e => e.stopPropagation()}>
                          <button className="btn-icon-sm" title="Archive" onClick={() => archiveEmail(email.id)}>📥</button>
                          <button className="btn-icon-sm" title="Delete" onClick={() => trashEmail(email.id)}>🗑️</button>
                        </span>
                      </div>
                    </div>
                    <div style={{marginTop: '0.5rem', fontWeight: email.isRead ? 'normal' : 'bold'}}>
                      {email.subject}
                    </div>
                    {email.dueDate && (
                      <div style={{marginTop: '0.5rem', color: '#e74c3c', fontSize: '0.9rem'}}>
                        📅 Due: {new Date(email.dueDate).toLocaleDateString()}
                      </div>
                    )}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
        </div>

        {/* Email Detail Panel */}
        {selectedEmail && (
          <div style={{
            flex: '1',
            minWidth: 0,
            background: 'white',
            borderRadius: '8px',
            boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            height: 'calc(100vh - 140px)',
          }}>
            {/* Toolbar */}
            <div className="email-toolbar">
              <div className="email-toolbar-left">
                <button className="btn-icon" title="Archive" onClick={() => archiveEmail(selectedEmail.id)}>📥</button>
                <button className="btn-icon" title="Delete" onClick={() => trashEmail(selectedEmail.id)}>🗑️</button>
                <span className="toolbar-divider" />
                <div className="move-dropdown">
                  <button className="btn-icon" title="Move to...">📁</button>
                  <div className="move-dropdown-content">
                    {['INBOX', 'IMPORTANT', 'SOCIAL', 'PROMOTIONS', 'UPDATES', 'FORUMS', 'SPAM', 'TRASH', 'ARCHIVED', 'SENT'].map(cat => (
                      <button key={cat} onClick={() => moveEmail(selectedEmail.id, cat)}>{cat}</button>
                    ))}
                  </div>
                </div>
                <span className="toolbar-divider" />
                <button className="btn-icon" title={selectedEmail.isStarred ? 'Unstar' : 'Star'}
                  onClick={() => toggleStar(selectedEmail.id, selectedEmail.isStarred)}>
                  {selectedEmail.isStarred ? '⭐' : '☆'}
                </button>
                <button className="btn-icon" title={selectedEmail.isRead ? 'Mark unread' : 'Mark read'}
                  onClick={async () => {
                    const endpoint = selectedEmail.isRead ? 'mark-unread' : 'mark-read';
                    await fetch(`/api/emails/${selectedEmail.id}/${endpoint}`, { method: 'PUT' });
                    setSelectedEmail(prev => ({ ...prev, isRead: !prev.isRead }));
                    setEmails(prev => prev.map(e => e.id === selectedEmail.id ? { ...e, isRead: !selectedEmail.isRead } : e));
                  }}>
                  {selectedEmail.isRead ? '✉️' : '📭'}
                </button>
              </div>
              <button
                onClick={() => setSelectedEmail(null)}
                title="Close"
                className="btn-icon"
              >
                ✕
              </button>
            </div>

            {/* Gmail-style header bar */}
            <div style={{
              padding: '16px 24px',
              borderBottom: '1px solid #e0e0e0',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'flex-start',
              background: 'white',
            }}>
              <div style={{flex: 1, minWidth: 0}}>
                <div style={{fontSize: '1.4rem', fontWeight: 400, color: '#202124', marginBottom: '12px', wordBreak: 'break-word'}}>
                  {selectedEmail.subject}
                  {selectedEmail.importance === 'URGENT' && <span style={{marginLeft: '8px', color: '#d93025', fontSize: '0.9rem', fontWeight: 500}}>● URGENT</span>}
                  {selectedEmail.importance === 'HIGH' && <span style={{marginLeft: '8px', color: '#f29900', fontSize: '0.9rem', fontWeight: 500}}>● HIGH</span>}
                  {selectedEmail.isPhishing && <span style={{marginLeft: '8px', color: '#d93025', fontSize: '0.85rem', background: '#fce8e6', padding: '2px 8px', borderRadius: '4px'}}>⚠️ Phishing</span>}
                  {selectedEmail.isSpam && <span style={{marginLeft: '8px', color: '#80868b', fontSize: '0.85rem', background: '#f1f3f4', padding: '2px 8px', borderRadius: '4px'}}>🗑️ Spam</span>}
                </div>
                {/* Sender row */}
                <div style={{display: 'flex', alignItems: 'center', gap: '12px'}}>
                  <div style={{
                    width: '40px', height: '40px', borderRadius: '50%',
                    background: '#1a73e8', color: 'white',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: '1rem', fontWeight: 500, flexShrink: 0,
                  }}>
                    {(selectedEmail.fromName || selectedEmail.fromAddress || '?')[0].toUpperCase()}
                  </div>
                  <div style={{flex: 1, minWidth: 0}}>
                    <div style={{display: 'flex', alignItems: 'baseline', gap: '8px', flexWrap: 'wrap'}}>
                      <span style={{fontWeight: 500, color: '#202124', fontSize: '0.95rem'}}>
                        {selectedEmail.fromName || selectedEmail.fromAddress}
                      </span>
                      {selectedEmail.fromName && (
                        <span style={{color: '#5f6368', fontSize: '0.85rem'}}>
                          &lt;{selectedEmail.fromAddress}&gt;
                        </span>
                      )}
                    </div>
                    <div style={{color: '#5f6368', fontSize: '0.82rem', marginTop: '2px'}}>
                      {selectedEmail.toAddresses && <span>to {selectedEmail.toAddresses} · </span>}
                      {new Date(selectedEmail.receivedDate).toLocaleString()}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Email body rendered in iframe */}
            {emailLoading ? (
              <div style={{padding: '24px', color: '#5f6368'}}>Loading...</div>
            ) : (!selectedEmail.bodyHtml && !selectedEmail.bodyPlainText) ? (
              <div style={{
                flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center',
                justifyContent: 'center', padding: '40px', color: '#5f6368', gap: '16px',
              }}>
                <div style={{fontSize: '2rem'}}>📷</div>
                <div style={{fontWeight: 500}}>No text content — this email may contain only images or attachments.</div>
                <button
                  onClick={refreshEmailBody}
                  style={{
                    background: '#1a73e8', color: '#fff', border: 'none', borderRadius: '4px',
                    padding: '8px 20px', cursor: 'pointer', fontSize: '0.9rem',
                  }}
                >
                  ↺ Reload from Gmail
                </button>
              </div>
            ) : (
              <iframe
                title="email-body"
                sandbox="allow-scripts allow-popups allow-popups-to-escape-sandbox"
                srcDoc={(() => {
                  const baseTag = `<base target="_blank" rel="noopener noreferrer">`;
                  const html = selectedEmail.bodyHtml
                    || (selectedEmail.bodyPlainText && (
                        selectedEmail.bodyPlainText.trimStart().startsWith('<') ||
                        selectedEmail.bodyPlainText.includes('<html') ||
                        selectedEmail.bodyPlainText.includes('<div') ||
                        selectedEmail.bodyPlainText.includes('<table')
                       ) ? selectedEmail.bodyPlainText : null);
                  if (html) {
                    return `<!DOCTYPE html><html><head><meta charset="utf-8">${baseTag}<style>body{margin:0;padding:16px 24px;font-family:Arial,sans-serif;font-size:14px;color:#202124;word-break:break-word;}img{max-width:100%;height:auto;}a{color:#1a73e8;cursor:pointer;}</style></head><body>${html}</body></html>`;
                  }
                  const text = (selectedEmail.bodyPlainText || '')
                    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
                    .replace(/https?:\/\/[^\s]+/g, url => `<a href="${url}" target="_blank" rel="noopener noreferrer">${url}</a>`);
                  return `<!DOCTYPE html><html><head><meta charset="utf-8">${baseTag}<style>body{margin:0;padding:16px 24px;font-family:Arial,sans-serif;font-size:14px;color:#202124;white-space:pre-wrap;word-break:break-word;line-height:1.6;}a{color:#1a73e8;}</style></head><body>${text}</body></html>`;
                })()}
                style={{
                  flex: 1,
                  width: '100%',
                  border: 'none',
                  minHeight: 0,
                }}
              />
            )}

            {/* Reply / Forward / Open action bar */}
            <div className="email-action-bar">
              <div style={{ display: 'flex', gap: '8px' }}>
                <button className="btn-action" onClick={() => openReply(false)}>
                  ↩️ Reply
                </button>
                <button className="btn-action" onClick={() => openReply(true)}>
                  ↩️↩️ Reply All
                </button>
                <button className="btn-action" onClick={openForward}>
                  ↪️ Forward
                </button>
              </div>
              <button
                onClick={() => {
                  const html = selectedEmail.bodyHtml || selectedEmail.bodyPlainText || '(No content)';
                  const fullHtml = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${selectedEmail.subject || 'Email'}</title><style>body{margin:24px auto;max-width:860px;font-family:Arial,sans-serif;font-size:14px;color:#202124;word-break:break-word;}img{max-width:100%;height:auto;}a{color:#1a73e8;}.header{margin-bottom:24px;padding-bottom:16px;border-bottom:1px solid #e0e0e0;}.header h1{font-size:1.4rem;font-weight:400;margin:0 0 12px 0;}.meta{color:#5f6368;font-size:0.85rem;line-height:1.8;}</style></head><body><div class="header"><h1>${selectedEmail.subject || ''}</h1><div class="meta"><b>From:</b> ${selectedEmail.fromAddress || ''}<br><b>To:</b> ${selectedEmail.toAddresses || ''}<br><b>Date:</b> ${selectedEmail.receivedDate ? new Date(selectedEmail.receivedDate).toLocaleString() : ''}</div></div>${html}</body></html>`;
                  const blob = new Blob([fullHtml], {type: 'text/html'});
                  const url = URL.createObjectURL(blob);
                  window.open(url, '_blank');
                  setTimeout(() => URL.revokeObjectURL(url), 60000);
                }}
                className="btn-action-secondary"
              >
                ↗ Open in browser tab
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Compose / Reply / Forward modal */}
      {composeMode && (
        <ComposeEmail
          accounts={accounts}
          onClose={() => setComposeMode(null)}
          onSent={() => { setActionFeedback('Message sent!'); fetchEmails(); }}
          replyTo={composeMode === 'reply' || composeMode === 'replyAll' ? {
            emailId: selectedEmail.id,
            accountId: selectedAccount,
            to: selectedEmail.fromAddress,
            cc: composeMode === 'replyAll' ? selectedEmail.ccAddresses || '' : '',
            originalToAddresses: selectedEmail.toAddresses || '',
            originalCcAddresses: selectedEmail.ccAddresses || '',
            subject: selectedEmail.subject ? (selectedEmail.subject.startsWith('Re:') ? selectedEmail.subject : `Re: ${selectedEmail.subject}`) : 'Re:',
            fromName: selectedEmail.fromName,
            date: selectedEmail.receivedDate ? new Date(selectedEmail.receivedDate).toLocaleString() : '',
            originalText: selectedEmail.bodyPlainText || '',
            replyAll: composeMode === 'replyAll'
          } : null}
          forwardEmail={composeMode === 'forward' ? {
            emailId: selectedEmail.id,
            accountId: selectedAccount,
            subject: selectedEmail.subject || '',
            fromAddress: selectedEmail.fromAddress || '',
            toAddresses: selectedEmail.toAddresses || '',
            receivedDate: selectedEmail.receivedDate,
            bodyPlainText: selectedEmail.bodyPlainText || '',
            bodyHtml: selectedEmail.bodyHtml || ''
          } : null}
        />
      )}
    </div>
  );
}

export default EmailList;
