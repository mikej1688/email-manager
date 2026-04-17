import React, { useState, useEffect } from 'react';

function EmailList({ accounts }) {
  const [emails, setEmails] = useState([]);
  const [selectedAccount, setSelectedAccount] = useState(null);
  const [filter, setFilter] = useState('all');
  const [loading, setLoading] = useState(false);
  const [selectedEmail, setSelectedEmail] = useState(null);
  const [emailLoading, setEmailLoading] = useState(false);

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

  const fetchEmails = async () => {
    setLoading(true);
    try {
      let url = `/api/emails/account/${selectedAccount}`;
      
      if (filter === 'unread') {
        url += '/unread';
      } else if (filter === 'urgent') {
        url += '/importance/URGENT';
      } else if (filter === 'high') {
        url += '/importance/HIGH';
      }

      const response = await fetch(url);
      const data = await response.json();
      setEmails(data.content || []);
    } catch (error) {
      console.error('Error fetching emails:', error);
    } finally {
      setLoading(false);
    }
  };

  const markAsRead = async (emailId) => {
    try {
      await fetch(`/api/emails/${emailId}/mark-read`, { method: 'PUT' });
      fetchEmails();
    } catch (error) {
      console.error('Error marking email as read:', error);
    }
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
      <h2>Emails</h2>

      <div style={{display: 'flex', gap: '1rem', alignItems: 'flex-start'}}>
        {/* Email List Panel */}
        <div className="card" style={{flex: selectedEmail ? '0 0 40%' : '1', minWidth: 0}}>
        <div style={{display: 'flex', gap: '1rem', marginBottom: '1rem'}}>
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
            </select>
          </div>

          <button onClick={fetchEmails} className="btn btn-primary">
            🔄 Refresh
          </button>
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
                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                  <div>
                    <strong>{email.fromAddress}</strong>
                    {getImportanceBadge(email.importance)}
                    {email.isPhishing && <span style={{color: 'red', marginLeft: '0.5rem'}}>⚠️ PHISHING</span>}
                    {email.isSpam && <span style={{color: 'orange', marginLeft: '0.5rem'}}>🗑️ SPAM</span>}
                  </div>
                  <div style={{fontSize: '0.9rem', color: '#7f8c8d'}}>
                    {new Date(email.receivedDate).toLocaleString()}
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
            maxHeight: 'calc(100vh - 140px)',
          }}>
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
              <button
                onClick={() => setSelectedEmail(null)}
                title="Close"
                style={{
                  background: 'none', border: 'none', cursor: 'pointer',
                  color: '#5f6368', fontSize: '1.2rem', padding: '4px',
                  borderRadius: '50%', lineHeight: 1, flexShrink: 0, marginLeft: '16px',
                }}
              >
                ✕
              </button>
            </div>

            {/* Email body rendered in iframe — isolates email CSS from app CSS */}
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
                  minHeight: '500px',
                  height: 'calc(100vh - 300px)',
                }}
              />
            )}

            {/* Open in new tab button */}
            <div style={{padding: '8px 24px', borderTop: '1px solid #e0e0e0', background: '#f8f9fa', display: 'flex', justifyContent: 'flex-end'}}>
              <button
                onClick={() => {
                  const html = selectedEmail.bodyHtml || selectedEmail.bodyPlainText || '(No content)';
                  const fullHtml = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${selectedEmail.subject || 'Email'}</title><style>body{margin:24px auto;max-width:860px;font-family:Arial,sans-serif;font-size:14px;color:#202124;word-break:break-word;}img{max-width:100%;height:auto;}a{color:#1a73e8;}.header{margin-bottom:24px;padding-bottom:16px;border-bottom:1px solid #e0e0e0;}.header h1{font-size:1.4rem;font-weight:400;margin:0 0 12px 0;}.meta{color:#5f6368;font-size:0.85rem;line-height:1.8;}</style></head><body><div class="header"><h1>${selectedEmail.subject || ''}</h1><div class="meta"><b>From:</b> ${selectedEmail.fromAddress || ''}<br><b>To:</b> ${selectedEmail.toAddresses || ''}<br><b>Date:</b> ${selectedEmail.receivedDate ? new Date(selectedEmail.receivedDate).toLocaleString() : ''}</div></div>${html}</body></html>`;
                  const blob = new Blob([fullHtml], {type: 'text/html'});
                  const url = URL.createObjectURL(blob);
                  window.open(url, '_blank');
                  setTimeout(() => URL.revokeObjectURL(url), 60000);
                }}
                style={{
                  background: 'none', border: '1px solid #dadce0', borderRadius: '4px',
                  padding: '6px 16px', cursor: 'pointer', color: '#1a73e8',
                  fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '6px',
                }}
              >
                ↗ Open in browser tab
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default EmailList;
