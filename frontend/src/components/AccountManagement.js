import React, { useState, useEffect } from 'react';

function AccountManagement({ onAccountsChange }) {
  const [accounts, setAccounts] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [oauthEmail, setOauthEmail] = useState('');
  const [showOAuthForm, setShowOAuthForm] = useState(false);
  const [formData, setFormData] = useState({
    emailAddress: '',
    displayName: '',
    provider: 'GMAIL',
    imapServer: '',
    imapPort: '',
    smtpServer: '',
    smtpPort: '',
    encryptedPassword: ''
  });

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
      setFormData({
        emailAddress: '',
        displayName: '',
        provider: 'GMAIL',
        imapServer: '',
        imapPort: '',
        smtpServer: '',
        smtpPort: '',
        encryptedPassword: ''
      });
      fetchAccounts();
    } catch (error) {
      console.error('Error adding account:', error);
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
            className="btn btn-secondary" 
            onClick={() => {
              setShowForm(!showForm);
              setShowOAuthForm(false);
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
                onChange={(e) => setFormData({...formData, emailAddress: e.target.value})}
              />
            </div>

            <div className="form-group">
              <label>Display Name *</label>
              <input 
                type="text" 
                required
                value={formData.displayName}
                onChange={(e) => setFormData({...formData, displayName: e.target.value})}
              />
            </div>

            <div className="form-group">
              <label>Provider *</label>
              <p style={{fontSize: '0.85rem', color: '#666', margin: '0.25rem 0 0.5rem'}}>
                Gmail supports OAuth in this app. Other providers use standard IMAP/SMTP settings. Facebook client credentials are not supported for mailbox access.
              </p>
              <select 
                value={formData.provider}
                onChange={(e) => setFormData({...formData, provider: e.target.value})}
              >
                <option value="GMAIL">Gmail</option>
                <option value="YAHOO">Yahoo</option>
                <option value="OUTLOOK">Outlook</option>
                <option value="IMAP_GENERIC">Other (IMAP)</option>
              </select>
            </div>

            {formData.provider !== 'GMAIL' && (
              <>
                <div className="form-group">
                  <label>IMAP Server</label>
                  <input 
                    type="text"
                    value={formData.imapServer}
                    onChange={(e) => setFormData({...formData, imapServer: e.target.value})}
                    placeholder="imap.example.com"
                  />
                </div>

                <div className="form-group">
                  <label>IMAP Port</label>
                  <input 
                    type="number"
                    value={formData.imapPort}
                    onChange={(e) => setFormData({...formData, imapPort: e.target.value})}
                    placeholder="993"
                  />
                </div>

                <div className="form-group">
                  <label>SMTP Server</label>
                  <input 
                    type="text"
                    value={formData.smtpServer}
                    onChange={(e) => setFormData({...formData, smtpServer: e.target.value})}
                    placeholder="smtp.example.com"
                  />
                </div>

                <div className="form-group">
                  <label>SMTP Port</label>
                  <input 
                    type="number"
                    value={formData.smtpPort}
                    onChange={(e) => setFormData({...formData, smtpPort: e.target.value})}
                    placeholder="587"
                  />
                </div>

                <div className="form-group">
                  <label>Password</label>
                  <input 
                    type="password"
                    value={formData.encryptedPassword}
                    onChange={(e) => setFormData({...formData, encryptedPassword: e.target.value})}
                  />
                </div>
              </>
            )}

            <button type="submit" className="btn btn-success">
              Add Account
            </button>
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
