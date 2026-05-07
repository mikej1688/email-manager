import React, { useState } from 'react';
import './Login.css';

function Login() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleGoogleLogin = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/auth/google/url');
      const data = await res.json();
      window.location.href = data.url;
    } catch {
      setError('Could not reach the server. Is the backend running?');
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <h1>📧 Email Manager</h1>
        <p>Sign in to manage your email accounts</p>
        {error && <div className="login-error">{error}</div>}
        <button
          className="google-signin-btn"
          onClick={handleGoogleLogin}
          disabled={loading}
        >
          {loading ? 'Redirecting…' : 'Sign in with Google'}
        </button>
      </div>
    </div>
  );
}

export default Login;
