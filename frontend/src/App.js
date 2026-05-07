import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Link, useNavigate } from 'react-router-dom';
import './App.css';
import Dashboard from './components/Dashboard';
import EmailList from './components/EmailList';
import AccountManagement from './components/AccountManagement';
import Notifications from './components/Notifications';
import PrivacyPolicy from './components/PrivacyPolicy';
import Footer from './components/Footer';
import Login from './components/Login';
import AuthCallback from './components/AuthCallback';
import ProtectedRoute from './components/ProtectedRoute';
import apiClient from './utils/apiClient';
import { clearToken, getUser } from './utils/auth';

function AppShell() {
  const [accounts, setAccounts] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const navigate = useNavigate();
  const currentUser = getUser();

  useEffect(() => {
    fetchAccounts();
    fetchNotifications();
  }, []);

  const fetchAccounts = async () => {
    try {
      const response = await apiClient.get('/api/accounts/active');
      const data = await response.json();
      setAccounts(data);
    } catch (error) {
      console.error('Error fetching accounts:', error);
    }
  };

  const fetchNotifications = async () => {
    try {
      const response = await apiClient.get('/api/notifications/unread');
      const data = await response.json();
      setNotifications(data);
    } catch (error) {
      console.error('Error fetching notifications:', error);
    }
  };

  const handleLogout = () => {
    clearToken();
    navigate('/login');
  };

  return (
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
        <div className="user-menu">
          {currentUser && <span className="user-name">{currentUser.name}</span>}
          <button className="logout-btn" onClick={handleLogout}>Sign out</button>
        </div>
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
  );
}

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/auth/callback" element={<AuthCallback />} />
        <Route path="/*" element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        } />
      </Routes>
    </Router>
  );
}

export default App;
