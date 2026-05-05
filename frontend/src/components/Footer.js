import React from 'react';
import { Link } from 'react-router-dom';

function Footer() {
  return (
    <footer className="app-footer">
      <div className="app-footer-content">
        <span className="app-footer-notice">
          Your emails are encrypted at rest (AES-256-GCM). Access is logged.
        </span>
        <span className="app-footer-sep">·</span>
        <Link to="/privacy" className="app-footer-link">Privacy Policy</Link>
      </div>
    </footer>
  );
}

export default Footer;
