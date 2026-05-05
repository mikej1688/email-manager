import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import './App.css';
import Dashboard from './components/Dashboard';
import EmailList from './components/EmailList';
import AccountManagement from './components/AccountManagement';
import Notifications from './components/Notifications';
import PrivacyPolicy from './components/PrivacyPolicy';
import Footer from './components/Footer';

function App() {
  const [accounts, setAccounts] = useState([]);
  const [notifications, setNotifications] = useState([]);

  useEffect(() => {
    // Fetch accounts on load
    fetchAccounts();
    fetchNotifications();
  }, []);

  const fetchAccounts = async () => {
    try {
      const response = await fetch('/api/accounts/active');
      const data = await response.json();
      setAccounts(data);
    } catch (error) {
      console.error('Error fetching accounts:', error);
    }
  };

  const fetchNotifications = async () => {
    try {
      const response = await fetch('/api/notifications/unread');
      const data = await response.json();
      setNotifications(data);
    } catch (error) {
      console.error('Error fetching notifications:', error);
    }
  };

  return (
    <Router>
      <div className="App">
        <header className="App-header">
          <h1>📧 Email Manager</h1>
          <nav>
            <Link to="/">Dashboard</Link>
            <Link to="/emails">Emails</Link>
            <Link to="/accounts">Accounts</Link>
            <Link to="/notifications">
              Notifications
              {notifications.length > 0 && (
                <span className="notification-badge">{notifications.length}</span>
              )}
            </Link>
          </nav>
        </header>

        <main className="App-main">
          <Routes>
            <Route path="/" element={<Dashboard accounts={accounts} />} />
            <Route path="/emails" element={<EmailList accounts={accounts} />} />
            <Route path="/accounts" element={<AccountManagement onAccountsChange={fetchAccounts} />} />
            <Route path="/notifications" element={<Notifications notifications={notifications} onRefresh={fetchNotifications} />} />
            <Route path="/privacy" element={<PrivacyPolicy />} />
          </Routes>
        </main>
        <Footer />
      </div>
    </Router>
  );
}

export default App;
