import React, { useState, useEffect } from 'react';

function Dashboard({ accounts }) {
  const [stats, setStats] = useState({
    totalAccounts: 0,
    totalEmails: 0,
    unreadEmails: 0,
    urgentEmails: 0
  });

  useEffect(() => {
    fetchStats();
  }, [accounts]);

  const fetchStats = async () => {
    try {
      let totalUnread = 0;
      
      for (const account of accounts) {
        const response = await fetch(`/api/emails/account/${account.id}/stats`);
        const data = await response.json();
        totalUnread += data.unreadCount || 0;
      }

      setStats({
        totalAccounts: accounts.length,
        totalEmails: 0,  // Can be fetched from backend
        unreadEmails: totalUnread,
        urgentEmails: 0   // Can be fetched from backend
      });
    } catch (error) {
      console.error('Error fetching stats:', error);
    }
  };

  return (
    <div>
      <h2>Dashboard</h2>
      
      <div className="stats-grid">
        <div className="stat-card">
          <h3>Email Accounts</h3>
          <div className="number">{stats.totalAccounts}</div>
        </div>
        
        <div className="stat-card">
          <h3>Unread Emails</h3>
          <div className="number" style={{color: '#3498db'}}>{stats.unreadEmails}</div>
        </div>
        
        <div className="stat-card">
          <h3>Urgent Emails</h3>
          <div className="number" style={{color: '#e74c3c'}}>{stats.urgentEmails}</div>
        </div>
      </div>

      <div className="card">
        <h3>Active Accounts</h3>
        {accounts.length === 0 ? (
          <p>No email accounts configured. Go to Accounts to add one.</p>
        ) : (
          <ul style={{listStyle: 'none', padding: 0}}>
            {accounts.map(account => (
              <li key={account.id} style={{padding: '0.5rem', borderBottom: '1px solid #eee'}}>
                <strong>{account.displayName}</strong> ({account.emailAddress})
                <span style={{marginLeft: '1rem', color: '#7f8c8d'}}>
                  {account.provider}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

export default Dashboard;
