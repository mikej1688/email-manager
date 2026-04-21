import React, { useState, useEffect } from 'react';

function AccountManagement({ onAccountsChange }) {
  const emptyForm = {
    emailAddress: '',
    displayName: '',
    provider: 'GMAIL',
    imapServer: '',
    imapPort: '',
    smtpServer: '',
    smtpPort: '',
    encryptedPassword: ''
  };

  const yahooDefaults = {
    provider: 'YAHOO',
    imapServer: 'imap.mail.yahoo.com',
    imapPort: '993',
    smtpServer: 'smtp.mail.yahoo.com',
    smtpPort: '587'
  };

  const [accounts, setAccounts] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [oauthEmail, setOauthEmail] = useState('');
  const [showOAuthForm, setShowOAuthForm] = useState(false);
  const [formData, setFormData] = useState(emptyForm);
  const [testingConnection, setTestingConnection] = useState(false);
  const [testConnectionResult, setTestConnectionResult] = useState(null);

  useEffect(() => {
    fetchAccounts();
    
    // Check for OAuth callback success/error in URL params
    const urlParams = new URLSearchParams(window.location.search);
    const success = urlParams.get('success');
    const error = urlParams.get('error');
    const email = urlParams.get('email');
    
    if (success === 'true' && email) {
      alert(`Successfully connected Gmail account: ${email}`);
      // Clean up URL
      window.history.replaceState({}, document.title, window.location.pathname);
      fetchAccounts();
    } else if (error) {
      alert(`OAuth error: ${error}`);
      window.history.replaceState({}, document.title, window.location.pathname);
    }
  }, []);

  const fetchAccounts = async () => {
    try {
      const response = await fetch('/api/accounts');
      const data = await response.json();
      setAccounts(data);
      if (onAccountsChange) {
        onAccountsChange();
      }
    } catch (error) {
      console.error('Error fetching accounts:', error);
    }
  };

  const handleGmailOAuth = async (e) => {
    e.preventDefault();
    if (!oauthEmail) {
      alert('Please enter your Gmail address');
      return;
    }
    
    try {
      const response = await fetch(`/api/oauth/gmail/authorize?email=${encodeURIComponent(oauthEmail)}`);
      const data = await response.json();
      
      if (data.authorizationUrl) {
        // Redirect to Google OAuth page
        window.location.href = data.authorizationUrl;
      } else {
        alert('Failed to initiate OAuth flow');
      }
    } catch (error) {
      console.error('Error starting OAuth:', error);
      alert('Failed to start OAuth flow');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await fetch('/api/accounts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData)
      });
      setShowForm(false);
      setFormData(emptyForm);
      setTestConnectionResult(null);
      fetchAccounts();
    } catch (error) {
      console.error('Error adding account:', error);
    }
  };

  const openYahooConnect = () => {
    setShowForm(true);
    setShowOAuthForm(false);
    setTestConnectionResult(null);
    setFormData((prev) => ({
      ...emptyForm,
      ...yahooDefaults,
      emailAddress: prev.provider === 'YAHOO' ? prev.emailAddress : '',
      displayName: prev.provider === 'YAHOO' ? prev.displayName : '',
      encryptedPassword: prev.provider === 'YAHOO' ? prev.encryptedPassword : ''
    }));
  };

  const handleProviderChange = (provider) => {
    setTestConnectionResult(null);
    if (provider === 'YAHOO') {
      setFormData((prev) => ({ ...prev, ...yahooDefaults }));
      return;
    }

    setFormData((prev) => ({
      ...prev,
      provider,
      imapServer: prev.provider === 'YAHOO' ? '' : prev.imapServer,
      imapPort: prev.provider === 'YAHOO' ? '' : prev.imapPort,
      smtpServer: prev.provider === 'YAHOO' ? '' : prev.smtpServer,
      smtpPort: prev.provider === 'YAHOO' ? '' : prev.smtpPort
    }));
  };

  const handleDraftTestConnection = async () => {
    if (!formData.emailAddress || !formData.displayName) {
      setTestConnectionResult({ success: false, message: 'Enter email address and display name first.' });
      return;
    }

    if (formData.provider !== 'GMAIL' && !formData.encryptedPassword) {
      setTestConnectionResult({ success: false, message: 'Enter the account password or app password first.' });
      return;
    }

    setTestingConnection(true);
    setTestConnectionResult(null);

    try {
      const response = await fetch('/api/accounts/test-connection', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData)
      });

      if (!response.ok) {
        setTestConnectionResult({ success: false, message: 'Connection test failed.' });
        return;
      }

      const data = await response.json();
      const connected = typeof data === 'boolean' ? data : !!data.success;
      setTestConnectionResult({
        success: connected,
        message: typeof data === 'boolean'
          ? (connected ? 'Connection successful.' : 'Could not connect with these settings.')
          : (data.message || (connected ? 'Connection successful.' : 'Could not connect with these settings.'))
      });
    } catch (error) {
      console.error('Error testing connection:', error);
      setTestConnectionResult({ success: false, message: 'Connection test failed.' });
    } finally {
      setTestingConnection(false);
    }
  };

  const deleteAccount = async (id) => {
    if (window.confirm('Are you sure you want to delete this account?')) {
      try {
        await fetch(`/api/accounts/${id}`, { method: 'DELETE' });
        fetchAccounts();
      } catch (error) {
        console.error('Error deleting account:', error);
      }
    }
  };

  const syncAccount = async (id) => {
    try {
      await fetch(`/api/accounts/${id}/sync`, { method: 'POST' });
      alert('Sync started successfully');
    } catch (error) {
      console.error('Error syncing account:', error);
      alert('Failed to sync account');
    }
  };

  return (
    <div>
      <h2>Account Management</h2>

      <div className="card">
        <div style={{display: 'flex', gap: '1rem', marginBottom: '1rem'}}>
          <button 
            className="btn btn-primary" 
            onClick={() => {
              setShowOAuthForm(!showOAuthForm);
              setShowForm(false);
            }}
          >
            {showOAuthForm ? 'Cancel' : '+ Connect Gmail (OAuth)'}
          </button>

          <button
            className="btn btn-success"
            onClick={openYahooConnect}
          >
            + Connect Yahoo
          </button>
          
          <button 
            className="btn btn-secondary" 
            onClick={() => {
              setShowForm(!showForm);
              setShowOAuthForm(false);
              if (!showForm) {
                setFormData(emptyForm);
                setTestConnectionResult(null);
              }
            }}
          >
            {showForm ? 'Cancel' : '+ Add Other Account'}
          </button>
        </div>

        {showOAuthForm && (
          <div style={{marginBottom: '2rem', padding: '1rem', background: '#e8f5e9', borderRadius: '4px', border: '1px solid #4caf50'}}>
            <h3>Connect Gmail Account</h3>
            <p style={{fontSize: '0.9rem', color: '#555', marginBottom: '1rem'}}>
              Use OAuth 2.0 for secure authentication with Gmail. You'll be redirected to Google to grant permissions.
            </p>
            <form onSubmit={handleGmailOAuth} style={{display: 'flex', gap: '1rem', alignItems: 'flex-end'}}>
              <div className="form-group" style={{flex: 1, marginBottom: 0}}>
                <label>Gmail Address *</label>
                <input 
                  type="email" 
                  required
                  value={oauthEmail}
                  onChange={(e) => setOauthEmail(e.target.value)}
                  placeholder="your.email@gmail.com"
                />
              </div>
              <button type="submit" className="btn btn-success">
                Authorize with Google
              </button>
            </form>
          </div>
        )}

        {showForm && (
          <form onSubmit={handleSubmit} style={{marginBottom: '2rem', padding: '1rem', background: '#f9f9f9', borderRadius: '4px'}}>
            <h3>Add New Email Account</h3>
            
            <div className="form-group">
              <label>Email Address *</label>
              <input 
                type="email" 
                required
                value={formData.emailAddress}
                onChange={(e) => {
                  setTestConnectionResult(null);
                  setFormData({...formData, emailAddress: e.target.value});
                }}
              />
            </div>

            <div className="form-group">
              <label>Display Name *</label>
              <input 
                type="text" 
                required
                value={formData.displayName}
                onChange={(e) => {
                  setTestConnectionResult(null);
                  setFormData({...formData, displayName: e.target.value});
                }}
              />
            </div>

            <div className="form-group">
              <label>Provider *</label>
              <p style={{fontSize: '0.85rem', color: '#666', margin: '0.25rem 0 0.5rem'}}>
                Gmail supports OAuth in this app. Other providers use standard IMAP/SMTP settings. Facebook client credentials are not supported for mailbox access.
              </p>
              <select 
                value={formData.provider}
                onChange={(e) => handleProviderChange(e.target.value)}
              >
                <option value="GMAIL">Gmail</option>
                <option value="YAHOO">Yahoo</option>
                <option value="OUTLOOK">Outlook</option>
                <option value="IMAP_GENERIC">Other (IMAP)</option>
              </select>
            </div>

            {formData.provider !== 'GMAIL' && (
              <>
                {formData.provider === 'YAHOO' && (
                  <div style={{marginBottom: '1rem', padding: '0.75rem', background: '#fff8e1', border: '1px solid #ffecb3', borderRadius: '4px', color: '#6d4c41'}}>
                    Yahoo uses IMAP and SMTP in this app. Use your Yahoo email address and a Yahoo app password, not your normal sign-in password.
                  </div>
                )}
                <div className="form-group">
                  <label>IMAP Server</label>
                  <input 
                    type="text"
                    value={formData.imapServer}
                    onChange={(e) => {
                      setTestConnectionResult(null);
                      setFormData({...formData, imapServer: e.target.value});
                    }}
                    placeholder="imap.example.com"
                  />
                </div>

                <div className="form-group">
                  <label>IMAP Port</label>
                  <input 
                    type="number"
                    value={formData.imapPort}
                    onChange={(e) => {
                      setTestConnectionResult(null);
                      setFormData({...formData, imapPort: e.target.value});
                    }}
                    placeholder="993"
                  />
                </div>

                <div className="form-group">
                  <label>SMTP Server</label>
                  <input 
                    type="text"
                    value={formData.smtpServer}
                    onChange={(e) => {
                      setTestConnectionResult(null);
                      setFormData({...formData, smtpServer: e.target.value});
                    }}
                    placeholder="smtp.example.com"
                  />
                </div>

                <div className="form-group">
                  <label>SMTP Port</label>
                  <input 
                    type="number"
                    value={formData.smtpPort}
                    onChange={(e) => {
                      setTestConnectionResult(null);
                      setFormData({...formData, smtpPort: e.target.value});
                    }}
                    placeholder="587"
                  />
                </div>

                <div className="form-group">
                  <label>Password</label>
                  <input 
                    type="password"
                    value={formData.encryptedPassword}
                    onChange={(e) => {
                      setTestConnectionResult(null);
                      setFormData({...formData, encryptedPassword: e.target.value});
                    }}
                  />
                </div>
              </>
            )}

            {showForm && formData.provider !== 'GMAIL' && testConnectionResult && (
              <div style={{
                marginBottom: '1rem',
                padding: '0.75rem',
                borderRadius: '4px',
                background: testConnectionResult.success ? '#e8f5e9' : '#fdecea',
                border: `1px solid ${testConnectionResult.success ? '#81c784' : '#f5c2c0'}`,
                color: testConnectionResult.success ? '#2e7d32' : '#b3261e'
              }}>
                {testConnectionResult.message}
              </div>
            )}

            <div style={{display: 'flex', gap: '0.75rem', alignItems: 'center'}}>
              {formData.provider !== 'GMAIL' && (
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={handleDraftTestConnection}
                  disabled={testingConnection}
                >
                  {testingConnection ? 'Testing...' : 'Test Connection'}
                </button>
              )}
              <button type="submit" className="btn btn-success">
                Add Account
              </button>
            </div>
          </form>
        )}

        <h3>Your Accounts</h3>
        {accounts.length === 0 ? (
          <p>No accounts configured.</p>
        ) : (
          <table style={{width: '100%', borderCollapse: 'collapse'}}>
            <thead>
              <tr style={{borderBottom: '2px solid #ddd'}}>
                <th style={{padding: '0.75rem', textAlign: 'left'}}>Email</th>
                <th style={{padding: '0.75rem', textAlign: 'left'}}>Display Name</th>
                <th style={{padding: '0.75rem', textAlign: 'left'}}>Provider</th>
                <th style={{padding: '0.75rem', textAlign: 'left'}}>Status</th>
                <th style={{padding: '0.75rem', textAlign: 'left'}}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map(account => (
                <tr key={account.id} style={{borderBottom: '1px solid #eee'}}>
                  <td style={{padding: '0.75rem'}}>{account.emailAddress}</td>
                  <td style={{padding: '0.75rem'}}>{account.displayName}</td>
                  <td style={{padding: '0.75rem'}}>{account.provider}</td>
                  <td style={{padding: '0.75rem'}}>
                    <span style={{
                      padding: '4px 8px',
                      borderRadius: '4px',
                      background: account.isActive ? '#2ecc71' : '#95a5a6',
                      color: 'white',
                      fontSize: '0.85rem'
                    }}>
                      {account.isActive ? 'Active' : 'Inactive'}
                    </span>
                    {account.provider === 'GMAIL' && account.accessToken && (
                      <span style={{
                        marginLeft: '0.5rem',
                        padding: '4px 8px',
                        borderRadius: '4px',
                        background: '#2196f3',
                        color: 'white',
                        fontSize: '0.85rem'
                      }}>
                        OAuth
                      </span>
                    )}
                  </td>
                  <td style={{padding: '0.75rem'}}>
                    <button 
                      onClick={() => syncAccount(account.id)}
                      className="btn btn-primary"
                      style={{marginRight: '0.5rem', padding: '0.25rem 0.75rem'}}
                    >
                      Sync
                    </button>
                    <button 
                      onClick={() => deleteAccount(account.id)}
                      className="btn btn-danger"
                      style={{padding: '0.25rem 0.75rem'}}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export default AccountManagement;
